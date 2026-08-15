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
 * Programmatic transaction template that takes care of connection acquisition, {@code
 * setAutoCommit}, {@code commit}, {@code rollback}, and resource cleanup for a code block.
 *
 * <p>Nested invocations reuse the outer transaction (propagation {@code REQUIRED}) automatically
 * when possible — see {@link CurrentTransactionConnection}. Post-commit cache-eviction callbacks
 * are dispatched via {@link AfterCommitHooks}, which in turn routes them to Spring's {@code
 * TransactionSynchronizationManager} when active, eliminating the 1.x race where cache regions were
 * cleared before commit under {@code @Transactional}.
 *
 * <p>Example:
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

    /** Convenience overload with a {@link Consumer} that ignores the connection. */
    @API(status = API.Status.STABLE)
    public void execute(Consumer<Connection> action) {
        execute(
                () -> {
                    action.accept(null);
                    return null;
                });
    }

    /**
     * Run {@code action} inside a transaction; commit on success, rollback on checked/unchecked
     * exception. When a JORM-managed transaction is already active on the current thread, the
     * callback joins it and no commit/rollback is attempted here.
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
                // Pending callbacks are void: the transaction is rolling
                // back, so firing would have been incorrect anyway.
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
     * Register {@code callback} to fire only after the active transaction commits. Behaves like the
     * legacy method of the same name; the redirection lets both Spring and Jorm-managed flows share
     * one registration point.
     */
    @API(status = API.Status.STABLE)
    public static void doAfterCommit(Runnable callback) {
        AfterCommitHooks.register(callback);
    }
}
