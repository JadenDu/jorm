// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * The catch-all dialect. Targets the {@code LIMIT ? OFFSET ?} clause family (MySQL 5+, PostgreSQL,
 * H2 in MySQL mode, MariaDB, SQLite). Use this when no database-specific dialect is known.
 *
 * <p>Duplicate-key detection here covers the {@code SQLState 23505} family used by PostgreSQL and
 * H2; vendors with their own conventions (MySQL: {@code 23000} + 1062) override {@link
 * #isDuplicateKey}.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DefaultDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** Singleton instance — dialects are stateless. */
    public static final DefaultDialect INSTANCE = new DefaultDialect();

    @Override
    public String getLimitClause(Integer limit, Integer offset) {
        if (limit == null && offset == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(32);
        if (limit != null) {
            sb.append(" LIMIT ").append(limit);
        }
        if (offset != null) {
            // Some dialects (older MySQL) require LIMIT before OFFSET is
            // valid; emit a sentinel "all rows" limit when only offset
            // was specified.
            if (limit == null) {
                sb.append(" LIMIT ").append(Integer.MAX_VALUE);
            }
            sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    @Override
    public boolean supportsIdentity() {
        return true;
    }

    @Override
    public boolean isDuplicateKey(SQLException e) {
        return e != null && "23505".equals(e.getSQLState());
    }

    @Override
    public String name() {
        return "Default";
    }
}
