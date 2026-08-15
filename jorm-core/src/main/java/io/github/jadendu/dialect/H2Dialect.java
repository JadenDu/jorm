// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * Dialect for the H2 in-memory database. Works in both {@code MySQL} and {@code PostgreSQL}
 * compatibility modes. H2 surfaces duplicate-key violations with SQL-state {@code 23505}
 * (PostgreSQL-style) or {@code 23506} depending on the constraint.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class H2Dialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** Singleton instance. */
    public static final H2Dialect INSTANCE = new H2Dialect();

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
        return true;
    }

    @Override
    public boolean isDuplicateKey(SQLException e) {
        if (e == null) {
            return false;
        }
        String sqlState = e.getSQLState();
        // 23505: unique constraint / primary key violation
        // 23506: referential constraint violation (still integrity, but
        // not strictly dup-key — excluded to stay conservative).
        return "23505".equals(sqlState);
    }

    @Override
    public String name() {
        return "H2";
    }
}
