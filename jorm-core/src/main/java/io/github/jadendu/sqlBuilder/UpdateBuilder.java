// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.sqlBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apiguardian.api.API;

import io.github.jadendu.dto.Condition;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.util.SqlValidator;

/**
 * Static UPDATE builder. SET and WHERE column names are both validated against the entity's
 * whitelist exactly as {@link FindBuilder} validates WHERE columns — so injecting raw SQL via a
 * {@code Set()} column name is impossible.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class UpdateBuilder {

    private UpdateBuilder() {}

    @API(status = API.Status.STABLE)
    public static String buildUpdateSql(
            Class<?> cls, List<Condition> conditions, Map<String, Object> updates) {
        EntityModel model = EntityModelRegistry.get(cls);
        String table = model.tableName();

        // --- SET column whitelist ---
        for (String col : updates.keySet()) {
            if (!model.isValidColumn(col)) {
                throw new JormException(ErrorCode.INVALID_COLUMN, "unknown SET column: " + col);
            }
        }
        // --- WHERE conditions still get their column + operator validated ---
        SqlValidator.validateConditions(model, conditions, ErrorCode.INVALID_COLUMN);

        String setClause =
                updates.keySet().stream()
                        .map(col -> col + " = ?")
                        .collect(Collectors.joining(", "));
        String whereClause =
                conditions.stream()
                        .map(cond -> cond.getColumn() + " " + cond.getOperator() + " ?")
                        .collect(Collectors.joining(" AND "));
        return String.format("UPDATE %s SET %s WHERE %s", table, setClause, whereClause);
    }
}
