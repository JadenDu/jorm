// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.util.Locale;

import org.apiguardian.api.API;

/**
 * 基于 JDBC URL 选择内置方言单例的便捷选择器。当未显式配置 {@code jorm.dialect} 时,
 * Spring Boot starter 会使用它。
 *
 * <p>该启发式策略刻意保持简单——仅检查 {@code jdbc:subprotocol:} 前缀。
 * 自定义方言(Oracle、SQL Server 等)必须显式装配。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Dialects {

    private Dialects() {}

    /**
     * 为给定的 JDBC URL 选择方言。当 URL 为 null、空白或无法识别时,返回 {@link DefaultDialect}。
     */
    public static Dialect forUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return DefaultDialect.INSTANCE;
        }
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:mysql") || url.startsWith("jdbc:mariadb")) {
            return MySqlDialect.INSTANCE;
        }
        if (url.startsWith("jdbc:postgresql")) {
            return PostgresDialect.INSTANCE;
        }
        if (url.startsWith("jdbc:h2")) {
            return H2Dialect.INSTANCE;
        }
        return DefaultDialect.INSTANCE;
    }

    /**
     * 按规范的短名称查找方言({@code "MySQL"}、{@code "H2"}、{@code
     * "PostgreSQL"}、{@code "Default"})。匹配不区分大小写。
     */
    public static Dialect byName(String name) {
        if (name == null) {
            return DefaultDialect.INSTANCE;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "mysql":
            case "mariadb":
                return MySqlDialect.INSTANCE;
            case "postgres":
            case "postgresql":
            case "pg":
                return PostgresDialect.INSTANCE;
            case "h2":
                return H2Dialect.INSTANCE;
            case "default":
            default:
                return DefaultDialect.INSTANCE;
        }
    }
}
