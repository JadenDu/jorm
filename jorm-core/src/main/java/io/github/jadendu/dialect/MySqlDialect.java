// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * MySQL 5.7+ 与 MariaDB 的方言。
 *
 * <p>重复键违规表现为 {@code SQLState 23000} 配合厂商错误码 {@code 1062}
 * (重复索引则为 {@code 1022})。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class MySqlDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** 单例实例。 */
    public static final MySqlDialect INSTANCE = new MySqlDialect();

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_DUP_KEY = 1022;

    @Override
    public String getLimitClause(Integer limit, Integer offset) {
        if (limit == null && offset == null) {
            return "";
        }
        // 当两个参数都存在时,MySQL 生成 "LIMIT offset, count";
        // 只有 count 时生成 "LIMIT count";并拒绝单独的
        // "OFFSET"。我们将"仅 offset"的情形转换为 "LIMIT 18446744073709551615 OFFSET ?"。
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
        // MySQL JDBC 有时会对许多完整性错误返回 23000;
        // 这里收窄到真正的重复键错误码。
        int ec = e.getErrorCode();
        return ec == ER_DUP_ENTRY || ec == ER_DUP_KEY;
    }

    @Override
    public String name() {
        return "MySQL";
    }
}
