// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.session.factory.Jorm;

/**
 * 编程式事务模板，负责代码块中连接的获取、{@code
 * setAutoCommit}、{@code commit}、{@code rollback} 以及资源清理。
 *
 * <p>嵌套调用在可能时自动复用外层事务（传播级别 {@code REQUIRED}）——
 * 参见 {@link CurrentTransactionConnection}。提交后的缓存驱逐回调
 * 通过 {@link AfterCommitHooks} 分发，后者在激活时会将它们路由到 Spring 的 {@code
 * TransactionSynchronizationManager}，消除了 1.x 版本中的竞态问题：
 * 在 {@code @Transactional} 下缓存区域于提交前被清空。
 *
 * <p>示例：
 *
 * <pre>{@code
 * new TransactionTemplate().execute(() -> {
 *     try (SaveSession s = new SaveSession()) {
 *         s.save(user1);
 *         s.save(user2);
 *     }
 *     return null;
 * });
 * }</pre>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class TransactionTemplate {

    private static final Logger log = LoggerFactory.getLogger(TransactionTemplate.class);

    /** 便捷重载，使用忽略连接的 {@link Consumer}。 */
    @API(status = API.Status.STABLE)
    public void execute(Consumer<Connection> action) {
        execute(
                () -> {
                    action.accept(null);
                    return null;
                });
    }

    /**
     * 在事务内执行 {@code action}；成功则提交，遇到受检/非受检异常则回滚。
     * 当当前线程已存在 JORM 管理的事务时，回调会加入该事务，
     * 此处不会尝试提交或回滚。
     */
    @API(status = API.Status.STABLE)
    public <T> T execute(Callable<T> action) {
        Connection conn = null;
        boolean existing = false;
        try {
            conn = CurrentTransactionConnection.get();
            if (conn != null) {
                existing = true;
                log.debug("Joining existing Jorm transaction");
            } else {
                conn = Jorm.getConnection();
                if (conn == null) {
                    throw new JormException(ErrorCode.DATASOURCE_NOT_CONFIGURED);
                }
                conn.setAutoCommit(false);
                CurrentTransactionConnection.set(conn);
                log.debug("Started new Jorm transaction");
            }

            T result = action.call();

            if (!existing) {
                try {
                    conn.commit();
                    log.debug("Jorm transaction committed");
                } catch (SQLException commitEx) {
                    throw new JormException(ErrorCode.TRANSACTION_COMMIT_FAILED, commitEx);
                }
                List<Runnable> callbacks = AfterCommitHooks.drain();
                AfterCommitHooks.flush(callbacks);
            }
            return result;
        } catch (Exception e) {
            if (!existing) {
                // 挂起的回调作废：事务正在回滚，
                // 此时触发它们本身就不正确。
                AfterCommitHooks.drain();
                if (conn != null) {
                    try {
                        conn.rollback();
                        log.debug("Jorm transaction rolled back", e);
                    } catch (SQLException rollbackEx) {
                        log.warn("Rollback failed", rollbackEx);
                        e.addSuppressed(rollbackEx);
                    }
                }
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new JormException(
                    ErrorCode.TRANSACTION_CLOSURE_FAILED, "Transaction execution failed", e);
        } finally {
            if (!existing) {
                CurrentTransactionConnection.clear();
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        log.warn("Failed to close connection after transaction", e);
                    }
                }
            }
        }
    }

    /**
     * 注册 {@code callback}，使其仅在活动事务提交后触发。行为与旧版同名方法一致；
     * 这种重定向让 Spring 与 JORM 管理的流程共享
     * 同一个注册入口。
     */
    @API(status = API.Status.STABLE)
    public static void doAfterCommit(Runnable callback) {
        AfterCommitHooks.register(callback);
    }
}
