// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.util.Locale;

import org.apiguardian.api.API;

/**
 * Convenience selector for the built-in dialect singletons based on a JDBC URL. Used by the Spring
 * Boot starter when {@code jorm.dialect} is not explicitly configured.
 *
 * <p>The heuristic is intentionally simple — it only inspects the {@code jdbc:subprotocol:} prefix.
 * Custom dialects (Oracle, SQL Server, ...) must be wired explicitly.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Dialects {

    private Dialects() {}

    /**
     * Pick a dialect for the given JDBC URL. Returns {@link DefaultDialect} when the URL is null,
     * blank, or unrecognised.
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
     * Look up a dialect by canonical short name ({@code "MySQL"}, {@code "H2"}, {@code
     * "PostgreSQL"}, {@code "Default"}). Matching is case-insensitive.
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
