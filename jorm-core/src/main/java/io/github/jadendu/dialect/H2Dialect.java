// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * H2 内存数据库的方言。可在 {@code MySQL} 与 {@code PostgreSQL} 兼容模式下工作。
 * 根据约束类型的不同,H2 以 SQL 状态 {@code 23505}(PostgreSQL 风格)或 {@code 23506}
 * 呈现重复键违规。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class H2Dialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** 单例实例。 */
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
        // 23505:唯一约束 / 主键违规
        // 23506:引用约束违规(仍属完整性错误,但
        // 不严格属于重复键——为保持保守而排除)。
        return "23505".equals(sqlState);
    }

    @Override
    public String name() {
        return "H2";
    }
}
