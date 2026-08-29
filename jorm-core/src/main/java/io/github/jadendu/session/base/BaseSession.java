// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.transaction.CurrentTransactionConnection;

/**
 * 所有 {@code io.github.jadendu} {@code Session} 的公共基类。
 *
 * <p>封装了:
 *
 * <ul>
 *   <li>{@link Connection} 的所有权——框架托管(会话关闭时自动关闭)与调用方提供
 *       (关闭时保持不变)。
 *   <li>自动提交默认值——对非事务会话开启;当 JORM 管理或 Spring 管理的事务
 *       处于激活状态时保持不变。
 *   <li>带层级回滚语义的嵌套保存点。
 *   <li>只允许执行一次的 {@code close()},重复关闭不会抛出异常。
 * </ul>
 *
 * <p><b>自 2.0 起移除:</b> 遗留的"一级缓存"(按会话按 id 的映射)纯属多余——从未
 * 被任何查询路径使用。二级缓存({@link io.github.jadendu.cache.CacheManager})
 * 承担了缓存职责;可选的持久化上下文 API 可能会在 3.x 中通过独立的 SPI 回归。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public abstract class BaseSession<T extends BaseSession<T>> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BaseSession.class);

    // 对子类可见;可变性:外部管理的会话保持调用方提供的连接,
    // 而框架托管的会话则会关闭它。
    protected Connection connection;
    protected boolean isManagedConnection;

    private final Map<String, Savepoint> savepoints = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Integer queryTimeoutSeconds;
    private Integer fetchSize;

    /** 调用方提供的连接(手动事务 / 显式连接池)。 */
    protected BaseSession(Connection connection) {
        this.connection = connection;
        this.isManagedConnection = false;
    }

    /**
     * 来自 {@link Jorm#getConnection()} 的自动托管连接,自动提交默认开启(无
     * 事务)。
     */
    protected BaseSession() {
        this(Jorm.getConnection());
        this.isManagedConnection = true;
        if (!CurrentTransactionConnection.hasTransaction()
                && !AfterCommitHooks.isSpringTransactionActive()) {
            try {
                this.connection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error(
                        "[ErrorCode={}] auto-commit could not be enabled",
                        ErrorCode.TRANSACTION_AUTOMATIC_FAILED.getCode(),
                        e);
                throw new JormException(ErrorCode.TRANSACTION_AUTOMATIC_FAILED, e);
            }
        }
    }

    /** 子类将 {@code this} 强转为自身类型返回,以支持可链式调用的流式方法。 */
    protected abstract T self();

    /**
     * 可选的 SQL 执行选项,应用于本会话发出的下一条语句。每次 {@code Find}/{@code Update}/...
     * 执行后会重置为默认值;若要在多次调用之间保持选项,
     * 请每次调用时重新设置。
     */
    @API(status = API.Status.STABLE)
    public T queryTimeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return self();
    }

    /** 为下一条预编译语句覆盖 JDBC 抓取大小(fetch-size)。 */
    @API(status = API.Status.STABLE)
    public T fetchSize(int size) {
        this.fetchSize = size;
        return self();
    }

    Integer queryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    Integer fetchSize() {
        return fetchSize;
    }

    protected void checkIfClosed() {
        if (closed.get()) {
            throw new JormException(ErrorCode.SESSION_HAS_CLOSED);
        }
    }

    /**
     * 将 {@link #queryTimeoutSeconds()} / {@link #fetchSize()} 应用于新的 {@link
     * java.sql.PreparedStatement}。
     */
    protected void applyQueryOptions(java.sql.PreparedStatement stmt) throws SQLException {
        if (queryTimeoutSeconds != null) {
            stmt.setQueryTimeout(queryTimeoutSeconds);
        }
        if (fetchSize != null) {
            stmt.setFetchSize(fetchSize);
        }
    }

    /** 每次执行后重置临时查询选项。 */
    protected void resetQueryOptions() {
        this.queryTimeoutSeconds = null;
        this.fetchSize = null;
    }

    @Override
    public void close() {
        // 事务激活中:保持连接存活;本会话已加入该事务。
        if (CurrentTransactionConnection.hasTransaction()
                && CurrentTransactionConnection.get() == this.connection) {
            log.debug("Skipping connection close: session joined a JORM transaction");
            return;
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (!isManagedConnection) {
            log.debug("Skipping connection close: caller-managed connection");
            return;
        }
        if (connection == null) {
            log.warn("Connection already null on close");
            return;
        }
        try {
            // 事务绑定连接的提交/回滚由 Spring 负责;在此处回滚会
            // 中途终止外围的 @Transactional 代码块。
            if (!AfterCommitHooks.isSpringTransactionActive()
                    && !connection.isClosed()
                    && !connection.getAutoCommit()) {
                connection.rollback();
                log.debug("Non-tx session: rolled back on close (commit must be explicit)");
            }
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.error("[ErrorCode={}] close failed", ErrorCode.SESSION_CLOSED_FAILED.getCode(), e);
            throw new JormException(ErrorCode.SESSION_CLOSED_FAILED, e);
        } finally {
            closed.set(true);
        }
    }

    /**
     * 在当前连接下创建保存点。重复保存相同名称会抛出 {@link
     * JormException}。
     */
    @API(status = API.Status.STABLE)
    public void createSavepoint(String name) {
        checkIfClosed();
        if (savepoints.containsKey(name)) {
            throw new JormException(ErrorCode.DUPLICATE_SAVEPOINT_NAME, "name=" + name);
        }
        try {
            savepoints.put(name, connection.setSavepoint(name));
        } catch (SQLException e) {
            throw new JormException(ErrorCode.SAVEPOINT_FAILED, "savepoint=" + name, e);
        }
    }

    /** 回滚到指定名称的保存点,并丢弃其后创建的保存点。 */
    @API(status = API.Status.STABLE)
    public void rollbackToSavepoint(String name) {
        checkIfClosed();
        Savepoint sp = savepoints.get(name);
        if (sp == null) {
            throw new JormException(ErrorCode.NO_SAVEPOINT, "savepoint=" + name);
        }
        try {
            connection.rollback(sp);
            // 丢弃在回滚目标之后创建的保存点。
            List<String> toRemove = new java.util.ArrayList<>();
            boolean found = false;
            for (String key : savepoints.keySet()) {
                if (found) toRemove.add(key);
                if (key.equals(name)) {
                    found = true;
                }
            }
            for (String key : toRemove) {
                savepoints.remove(key);
            }
        } catch (SQLException e) {
            throw new JormException(ErrorCode.ROLLBACK_FAILED, "savepoint=" + name, e);
        }
    }

    /** 向高级调用方暴露底层 JDBC 连接。 */
    @API(status = API.Status.STABLE)
    public Connection getNativeConnection() {
        return connection;
    }
}
