// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * PostgreSQL 10+ 的方言。默认采用基于 SQL 状态的重复键检测({@code 23505});
 * LIMIT/OFFSET 均受支持。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class PostgresDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** 单例实例。 */
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
        // SERIAL/IDENTITY 列均可用;IDENTITY 自 PG10 起可用。
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
