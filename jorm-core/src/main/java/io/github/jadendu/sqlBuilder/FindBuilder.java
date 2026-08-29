// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.sqlBuilder;

import java.util.ArrayList;
import java.util.List;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.dialect.Dialect;
import io.github.jadendu.dto.Condition;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.util.SqlBuilderHelper;
import io.github.jadendu.util.SqlValidator;

/**
 * 静态 SELECT 构建器。使用当前表的缓存 {@link EntityModel}，针对实体白名单校验每个
 * 条件列/操作符以防止 SQL 注入，并通过当前 {@link Dialect} 渲染
 * {@code LIMIT/OFFSET} 子句，从而保证跨数据库的可移植性。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class FindBuilder {

    private static final Logger log = LoggerFactory.getLogger(FindBuilder.class);

    private FindBuilder() {}

    @API(status = API.Status.STABLE)
    public static String buildFindSelect(
            Class<?> cls,
            List<Condition> conditions,
            Integer limit,
            Integer offset,
            String orderBy,
            String group,
            List<Condition> havingConditions,
            String selectClause,
            Dialect dialect) {
        SqlValidator.validateLimit(limit, offset);
        EntityModel model = EntityModelRegistry.get(cls);

        SqlValidator.validateConditions(model, conditions, ErrorCode.INVALID_COLUMN);
        SqlValidator.validateConditions(model, havingConditions, ErrorCode.INVALID_COLUMN, true);
        SqlValidator.validateGroupBy(model, group);
        SqlValidator.validateOrderBy(model, orderBy);
        SqlValidator.validateSelectClause(model, selectClause);
        String table = model.tableName();

        StringBuilder sql = new StringBuilder(128);
        sql.append("SELECT ").append(selectClause).append(" FROM ").append(table);

        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> parts = new ArrayList<>(conditions.size());
            for (Condition cond : conditions) {
                parts.add(SqlBuilderHelper.renderCondition(cond));
            }
            sql.append(String.join(" AND ", parts));
        }

        if (group != null && !group.trim().isEmpty()) {
            sql.append(" GROUP BY ").append(group);
        }

        if (havingConditions != null && !havingConditions.isEmpty()) {
            sql.append(" HAVING ");
            List<String> parts = new ArrayList<>(havingConditions.size());
            for (Condition cond : havingConditions) {
                parts.add(SqlBuilderHelper.renderCondition(cond));
            }
            sql.append(String.join(" AND ", parts));
        }

        if (orderBy != null && !orderBy.trim().isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }

        if (dialect != null) {
            String limitClause = dialect.getLimitClause(limit, offset);
            if (limitClause != null && !limitClause.isEmpty()) {
                sql.append(limitClause);
            }
        } else if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }

        if (log.isDebugEnabled()) {
            log.debug("Built SELECT: {}", sql);
        }
        return sql.toString();
    }
}
