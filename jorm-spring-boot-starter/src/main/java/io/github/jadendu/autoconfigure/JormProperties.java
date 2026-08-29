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
 * {@code jorm.*} 属性的配置入口。
 *
 * <p>由 {@link JormAutoConfiguration} 基于本类解析后的值应用框架管理的默认配置。
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
     * 多行 INSERT 的批量大小(也是单条 {@code INSERT VALUES (...),
     * (...)...} 语句写入行数的上限)。默认 100 行。
     */
    private int batchSize = 100;

    /**
     * 方言选择。取值可为: {@code "MySQL"}、{@code "PostgreSQL"}、{@code "H2"}、{@code
     * "Default"},或不设置(根据 {@link #jdbcUrl} 自动探测)。
     */
    private String dialect;

    /**
     * 命名策略。可选值: {@code "default"}(snake_case 表名 + 复数形式)、{@code "identity"}
     * (原样使用 Java 标识符),或自定义 {@link io.github.jadendu.entity.naming.NamingStrategy}
     * 的全限定类名。
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

    /** 解析生效的方言: 优先使用属性中的显式名称,否则回退到根据 JDBC URL 自动探测。 */
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

    /** 按名称或类名解析生效的命名策略。 */
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
