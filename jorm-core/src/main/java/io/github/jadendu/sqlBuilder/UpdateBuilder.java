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
import io.github.jadendu.util.SqlBuilderHelper;
import io.github.jadendu.util.SqlValidator;

/**
 * 静态 UPDATE 构建器。SET 与 WHERE 的列名都会像 {@link FindBuilder} 校验 WHERE 列那样
 * 针对实体白名单进行校验——因此无法通过
 * {@code Set()} 列名注入原始 SQL。
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

        // --- SET 列白名单 ---
        for (String col : updates.keySet()) {
            if (!model.isValidColumn(col)) {
                throw new JormException(ErrorCode.INVALID_COLUMN, "unknown SET column: " + col);
            }
        }
        // --- WHERE 条件仍会对列与操作符进行校验 ---
        SqlValidator.validateConditions(model, conditions, ErrorCode.INVALID_COLUMN);

        String setClause =
                updates.keySet().stream()
                        .map(col -> col + " = ?")
                        .collect(Collectors.joining(", "));
        String whereClause =
                conditions.stream()
                        .map(SqlBuilderHelper::renderCondition)
                        .collect(Collectors.joining(" AND "));
        return String.format("UPDATE %s SET %s WHERE %s", table, setClause, whereClause);
    }
}
