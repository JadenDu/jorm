// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * Dialect for MySQL 5.7+ and MariaDB.
 *
 * <p>Duplicate-key violations surface as {@code SQLState 23000} with vendor error code {@code 1062}
 * (or {@code 1022} for duplicate index).
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class MySqlDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** Singleton instance. */
    public static final MySqlDialect INSTANCE = new MySqlDialect();

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_DUP_KEY = 1022;

    @Override
    public String getLimitClause(Integer limit, Integer offset) {
        if (limit == null && offset == null) {
            return "";
        }
        // MySQL emits "LIMIT offset, count" when both args are present,
        // "LIMIT count" when only count is present, and rejects bare
        // "OFFSET". We translate "offset only" into "LIMIT 18446744073709551615 OFFSET ?".
        if (limit != null) {
            if (offset != null) {
                return " LIMIT " + offset + ", " + limit;
            }
            return " LIMIT " + limit;
        }
        return " LIMIT " + Long.MAX_VALUE + " OFFSET " + offset;
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
        // MySQL JDBC sometimes surfaces 23000 for many integrity issues;
        // narrow to the true duplicate-key error codes.
        int ec = e.getErrorCode();
        return ec == ER_DUP_ENTRY || ec == ER_DUP_KEY;
    }

    @Override
    public String name() {
        return "MySQL";
    }
}
