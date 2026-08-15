// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * Dialect for PostgreSQL 10+. Defaults to SQL-state based duplicate-key detection ({@code 23505});
 * LIMIT/OFFSET are both supported.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class PostgresDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** Singleton instance. */
    public static final PostgresDialect INSTANCE = new PostgresDialect();

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
            sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    @Override
    public boolean supportsIdentity() {
        // SERIAL/IDENTITY columns exist; IDENTITY since PG10.
        return true;
    }

    @Override
    public boolean isDuplicateKey(SQLException e) {
        return e != null && "23505".equals(e.getSQLState());
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }
}
