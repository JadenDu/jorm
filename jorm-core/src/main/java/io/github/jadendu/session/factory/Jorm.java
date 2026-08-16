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
 * Factory entry point for the core {@code io.github.jadendu} module.
 *
 * <p>Holds the runtime singletons ({@link DataSource} and {@link Dialect}) needed by every {@code
 * Session} when no external {@link Connection} is supplied by the caller. Configuration of these
 * singletons is normally done by the Spring Boot starter via {@link #setDataSource(DataSource)} and
 * {@link #setDialect(Dialect)} after initialisation; standalone users must call the setters
 * themselves before opening the first session.
 *
 * <p>The singletons are {@code volatile} so the visibility guarantees required by the JMM hold
 * across threads without resorting to external synchronisation on read.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Jorm {

    private static final Logger log = LoggerFactory.getLogger(Jorm.class);

    /** Spring-managed (or standalone-configured) connection source. */
    private static volatile DataSource dataSource;

    /** SQL-flavour adapter; defaults to the catch-all MySQL/PG/H2 dialect. */
    private static volatile Dialect dialect = DefaultDialect.INSTANCE;

    /** Maximum rows emitted per multi-row INSERT (chunk for {@code batchSave}). */
    private static volatile int batchSize = 100;

    private Jorm() {}

    /**
     * Inject the {@link DataSource}; never null. Required before any unconnected {@code Session}.
     */
    @API(status = API.Status.STABLE)
    public static void setDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new JormException(ErrorCode.DATASOURCE_NOT_CONFIGURED);
        }
        Jorm.dataSource = dataSource;
        log.debug("DataSource configured: {}", dataSource.getClass().getName());
    }

    /** Read whether a {@link DataSource} has been configured. */
    @API(status = API.Status.STABLE)
    public static boolean isConfigured() {
        return dataSource != null;
    }

    /** Read the active {@link DataSource} (or {@code null} when unconfigured). */
    @API(status = API.Status.STABLE)
    public static DataSource dataSource() {
        return dataSource;
    }

    /** Read/replace the active dialect; defaults to {@link DefaultDialect}. */
    @API(status = API.Status.STABLE)
    public static Dialect dialect() {
        return dialect;
    }

    @API(status = API.Status.STABLE)
    public static void setDialect(Dialect newDialect) {
        dialect = newDialect == null ? DefaultDialect.INSTANCE : newDialect;
        log.debug("Dialect configured: {}", dialect.name());
    }

    /** Read/replace the multi-row INSERT chunk size (used by {@link SaveSession#batchSave}). */
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
    // Session factories
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
     * Resolve a {@link Connection}: an active transaction connection first, otherwise a fresh one
     * from the configured {@link DataSource}.
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
