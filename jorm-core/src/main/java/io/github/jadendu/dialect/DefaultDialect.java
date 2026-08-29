// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * 兜底方言。针对 {@code LIMIT ? OFFSET ?} 子句系列(MySQL 5+、PostgreSQL、
 * MySQL 模式下的 H2、MariaDB、SQLite)。当没有已知的数据库专属方言时使用本方言。
 *
 * <p>这里的重复键检测覆盖 PostgreSQL 与 H2 使用的 {@code SQLState 23505} 系列;
 * 采用自定义约定的厂商(MySQL:{@code 23000} + 1062)会覆写 {@link
 * #isDuplicateKey}。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DefaultDialect implements Dialect {

    private static final long serialVersionUID = 1L;

    /** 单例实例——方言是无状态的。 */
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
            // 某些方言(旧版 MySQL)要求 LIMIT 必须位于 OFFSET 之前才能生效;
            // 当只指定了 offset 时,发出一个表示"全部行"的哨兵 limit。
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
