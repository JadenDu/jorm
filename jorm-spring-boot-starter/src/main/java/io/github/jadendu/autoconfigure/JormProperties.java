// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.autoconfigure;

import org.apiguardian.api.API;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.jadendu.dialect.Dialect;
import io.github.jadendu.dialect.Dialects;
import io.github.jadendu.entity.naming.DefaultNamingStrategy;
import io.github.jadendu.entity.naming.IdentityNamingStrategy;
import io.github.jadendu.entity.naming.NamingStrategy;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Configuration surface for {@code jorm.*} properties.
 *
 * <p>Bean-managed defaults are applied by {@link JormAutoConfiguration} based on the resolved
 * values on this class.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
@ConfigurationProperties(prefix = "jorm")
public class JormProperties {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;

    /**
     * Multi-row INSERT chunk size (also the upper bound on a single {@code INSERT VALUES (...),
     * (...)...}). Defaults to 100 rows.
     */
    private int batchSize = 100;

    /**
     * Dialect selection. Value can be: {@code "MySQL"}, {@code "PostgreSQL"}, {@code "H2"}, {@code
     * "Default"}, or omitted (auto-detected from {@link #jdbcUrl}).
     */
    private String dialect;

    /**
     * Naming strategy. Possible values: {@code "default"} (snake_case + pluralised tables), {@code
     * "identity"} (use Java identifiers verbatim), or the fully-qualified class name of a custom
     * {@link io.github.jadendu.entity.naming.NamingStrategy}.
     */
    private String namingStrategy = "default";

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String v) {
        this.jdbcUrl = v;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String v) {
        this.username = v;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String v) {
        this.password = v;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String v) {
        this.driverClassName = v;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int v) {
        this.maximumPoolSize = v;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int v) {
        this.minimumIdle = v;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long v) {
        this.connectionTimeout = v;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long v) {
        this.idleTimeout = v;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(long v) {
        this.maxLifetime = v;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int v) {
        this.batchSize = v;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String v) {
        this.dialect = v;
    }

    public String getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(String v) {
        this.namingStrategy = v;
    }

    /** Resolve the active dialect using properties' explicit name + JDBC URL fallback. */
    @API(status = API.Status.INTERNAL)
    public Dialect resolveDialect() {
        if (dialect != null && !diactionBlankOrKeyword(dialect)) {
            return Dialects.byName(dialect);
        }
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return io.github.jadendu.dialect.DefaultDialect.INSTANCE;
        }
        return Dialects.forUrl(jdbcUrl);
    }

    /** Resolve the active naming strategy by name or class-name. */
    @API(status = API.Status.INTERNAL)
    public NamingStrategy resolveNamingStrategy() {
        if (namingStrategy == null
                || namingStrategy.isEmpty()
                || "default".equalsIgnoreCase(namingStrategy)) {
            return DefaultNamingStrategy.INSTANCE;
        }
        if ("identity".equalsIgnoreCase(namingStrategy)) {
            return IdentityNamingStrategy.INSTANCE;
        }
        try {
            Class<?> cls = Class.forName(namingStrategy);
            return (NamingStrategy) cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new JormException(
                    ErrorCode.INVALID_DIALECT,
                    "could not instantiate NamingStrategy: " + namingStrategy,
                    e);
        }
    }

    private static boolean diactionBlankOrKeyword(String s) {
        return s.trim().isEmpty() || "auto".equalsIgnoreCase(s) || "default".equalsIgnoreCase(s);
    }
}
