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
import io.github.jadendu.util.SqlValidator;

/**
 * Static SELECT builder. Uses the cached {@link EntityModel} for the active table, validates every
 * condition column/operator against the entity whitelist to prevent SQL injection, and renders the
 * {@code LIMIT/OFFSET} clause through the active {@link Dialect} so cross-DB portability is
 * respected.
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
        SqlValidator.validateConditions(model, havingConditions, ErrorCode.INVALID_COLUMN);
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
                parts.add(cond.getColumn() + " " + cond.getOperator() + " ?");
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
                parts.add(cond.getColumn() + " " + cond.getOperator() + " ?");
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
