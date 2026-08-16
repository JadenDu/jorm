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
 * Common base for every {@code io.github.jadendu} {@code Session}.
 *
 * <p>Encapsulates:
 *
 * <ul>
 *   <li>{@link Connection} ownership — managed (auto-close on session close) vs. caller-supplied
 *       (left intact on close).
 *   <li>Auto-commit defaults — switched on for non-transactional sessions, left untouched when a
 *       JORM-managed or Spring-managed transaction is active.
 *   <li>Nested savepoints with hierarchical rollback semantics.
 *   <li>A guarded-once {@code close()} that never throws on double-close.
 * </ul>
 *
 * <p><b>Removed since 2.0:</b> the legacy "first-level cache" (per-session per-id map) was dead
 * weight — never fed by any query path. The L2 cache ({@link io.github.jadendu.cache.CacheManager})
 * serves the caching narrative; an opt-in persistence-context API may return through a separate SPI
 * in 3.x.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public abstract class BaseSession<T extends BaseSession<T>> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BaseSession.class);

    // Visible to subclasses; mutability: outer-managed sessions keep
    // their connection supplied, while managed sessions close it.
    protected Connection connection;
    protected boolean isManagedConnection;

    private final Map<String, Savepoint> savepoints = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Integer queryTimeoutSeconds;
    private Integer fetchSize;

    /** Caller-supplied connection (manual transaction / explicit pool). */
    protected BaseSession(Connection connection) {
        this.connection = connection;
        this.isManagedConnection = false;
    }

    /**
     * Auto-managed connection from {@link Jorm#getConnection()} with auto-commit default on (no
     * tx).
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

    /** Subclasses return {@code this} cast to themselves to support chainable fluent calls. */
    protected abstract T self();

    /**
     * Optional SQL execution options applied to the next statement this session issues. Reset to
     * defaults after each {@code Find}/{@code Update}/... execution; to persist options across
     * calls, re-apply them per call.
     */
    @API(status = API.Status.STABLE)
    public T queryTimeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return self();
    }

    /** Override the JDBC fetch-size for the next prepared statement. */
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
     * Apply {@link #queryTimeoutSeconds()} / {@link #fetchSize()} to a fresh {@link
     * java.sql.PreparedStatement}.
     */
    protected void applyQueryOptions(java.sql.PreparedStatement stmt) throws SQLException {
        if (queryTimeoutSeconds != null) {
            stmt.setQueryTimeout(queryTimeoutSeconds);
        }
        if (fetchSize != null) {
            stmt.setFetchSize(fetchSize);
        }
    }

    /** Reset transient query options after each execution. */
    protected void resetQueryOptions() {
        this.queryTimeoutSeconds = null;
        this.fetchSize = null;
    }

    @Override
    public void close() {
        // Active transaction: keep the connection alive; we joined it.
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
            // Spring owns commit/rollback for the tx-bound connection; rolling back here would
            // abort the surrounding @Transactional block mid-flight.
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
     * Issue a savepoint under the current connection. Saving the same name twice throws a {@link
     * JormException}.
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

    /** Roll back to the named savepoint and discard later savepoints. */
    @API(status = API.Status.STABLE)
    public void rollbackToSavepoint(String name) {
        checkIfClosed();
        Savepoint sp = savepoints.get(name);
        if (sp == null) {
            throw new JormException(ErrorCode.NO_SAVEPOINT, "savepoint=" + name);
        }
        try {
            connection.rollback(sp);
            // Drop savepoints created after the rolled-back one.
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

    /** Expose the underlying JDBC connection for advanced callers. */
    @API(status = API.Status.STABLE)
    public Connection getNativeConnection() {
        return connection;
    }
}
