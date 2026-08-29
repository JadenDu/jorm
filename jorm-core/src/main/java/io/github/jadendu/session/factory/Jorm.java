// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session.factory;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.dialect.DefaultDialect;
import io.github.jadendu.dialect.Dialect;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.session.DeleteSession;
import io.github.jadendu.session.FindSession;
import io.github.jadendu.session.SaveSession;
import io.github.jadendu.session.UpdateSession;
import io.github.jadendu.transaction.CurrentTransactionConnection;

/**
 * 核心 {@code io.github.jadendu} 模块的工厂入口。
 *
 * <p>当调用方未提供外部 {@link Connection} 时,保存每个 {@code
 * Session} 所需的运行时单例({@link DataSource} 与 {@link Dialect})。这些
 * 单例的配置通常由 Spring Boot starter 在初始化后通过 {@link #setDataSource(DataSource)} 和
 * {@link #setDialect(Dialect)} 完成;独立(非 Spring)用户必须在打开第一个会话
 * 之前自行调用 setter 方法。
 *
 * <p>这些单例声明为 {@code volatile},从而在跨线程读取时无需借助外部同步
 * 即可满足 JMM 所要求的内存可见性保证。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Jorm {

    private static final Logger log = LoggerFactory.getLogger(Jorm.class);

    /** Spring 管理(或独立配置)的连接源。 */
    private static volatile DataSource dataSource;

    /** SQL 方言适配器;默认为通用的 MySQL/PG/H2 方言。 */
    private static volatile Dialect dialect = DefaultDialect.INSTANCE;

    /** 每个多行 INSERT 的最大行数({@code batchSave} 的分块大小)。 */
    private static volatile int batchSize = 100;

    private Jorm() {}

    /**
     * 注入 {@link DataSource};不可为 null。任何无连接的 {@code Session} 使用前都必须调用。
     */
    @API(status = API.Status.STABLE)
    public static void setDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new JormException(ErrorCode.DATASOURCE_NOT_CONFIGURED);
        }
        Jorm.dataSource = dataSource;
        log.debug("DataSource configured: {}", dataSource.getClass().getName());
    }

    /** 读取是否已配置 {@link DataSource}。 */
    @API(status = API.Status.STABLE)
    public static boolean isConfigured() {
        return dataSource != null;
    }

    /** 读取激活的 {@link DataSource}(未配置时为 {@code null})。 */
    @API(status = API.Status.STABLE)
    public static DataSource dataSource() {
        return dataSource;
    }

    /** 读取/替换激活的方言;默认为 {@link DefaultDialect}。 */
    @API(status = API.Status.STABLE)
    public static Dialect dialect() {
        return dialect;
    }

    @API(status = API.Status.STABLE)
    public static void setDialect(Dialect newDialect) {
        dialect = newDialect == null ? DefaultDialect.INSTANCE : newDialect;
        log.debug("Dialect configured: {}", dialect.name());
    }

    /** 读取/替换多行 INSERT 的分块大小(由 {@link SaveSession#batchSave} 使用)。 */
    @API(status = API.Status.STABLE)
    public static int batchSize() {
        return batchSize;
    }

    @API(status = API.Status.STABLE)
    public static void setBatchSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1, got " + size);
        }
        batchSize = size;
        log.debug("Batch size configured: {}", batchSize);
    }

    // ------------------------------------------------------------------
    // 会话工厂
    // ------------------------------------------------------------------

    @API(status = API.Status.STABLE)
    public static SaveSession saveSession() {
        return new SaveSession(getConnection());
    }

    @API(status = API.Status.STABLE)
    public static FindSession findSession() {
        return new FindSession(getConnection());
    }

    @API(status = API.Status.STABLE)
    public static DeleteSession deleteSession() {
        return new DeleteSession(getConnection());
    }

    @API(status = API.Status.STABLE)
    public static UpdateSession updateSession() {
        return new UpdateSession(getConnection());
    }

    @API(status = API.Status.STABLE)
    public static SaveSession saveSession(Connection conn) {
        return new SaveSession(conn);
    }

    @API(status = API.Status.STABLE)
    public static FindSession findSession(Connection conn) {
        return new FindSession(conn);
    }

    @API(status = API.Status.STABLE)
    public static DeleteSession deleteSession(Connection conn) {
        return new DeleteSession(conn);
    }

    @API(status = API.Status.STABLE)
    public static UpdateSession updateSession(Connection conn) {
        return new UpdateSession(conn);
    }

    /**
     * 解析 {@link Connection}:优先使用当前激活事务的连接,否则
     * 从已配置的 {@link DataSource} 获取一个新的连接。
     */
    @API(status = API.Status.STABLE)
    public static Connection getConnection() {
        Connection transactionConn = CurrentTransactionConnection.get();
        if (transactionConn != null) {
            return transactionConn;
        }
        DataSource ds = dataSource;
        if (ds == null) {
            throw new JormException(
                    ErrorCode.DATASOURCE_NOT_CONFIGURED,
                    "no DataSource configured — call Jorm.setDataSource(...) or the"
                            + " Spring Boot starter for JORM auto-configuration");
        }
        try {
            return ds.getConnection();
        } catch (SQLException e) {
            throw new JormException(ErrorCode.CONNECTION_ERROR, e);
        }
    }
}
