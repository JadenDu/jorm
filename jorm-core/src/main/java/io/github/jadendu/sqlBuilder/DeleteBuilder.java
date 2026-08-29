// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.sqlBuilder;

import java.util.ArrayList;
import java.util.List;

import org.apiguardian.api.API;

import io.github.jadendu.dialect.Dialect;
import io.github.jadendu.dto.Condition;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.util.SqlBuilderHelper;
import io.github.jadendu.util.SqlValidator;

/**
 * 静态 DELETE 构建器。支持三类 DELETE：按主键 / 按主键批量 /
 * 按条件（可选带 LIMIT）。
 *
 * <p>使用条件 API 时，WHERE 列与操作符会针对当前实体模型进行校验；
 * LIMIT 子句由当前 {@link Dialect} 渲染。警告：并非所有方言都支持
 * {@code DELETE ... LIMIT} 子句；PostgreSQL 会拒绝该语法。应用程序应负责
 * 在禁止该语法的方言上不配置 limit。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class DeleteBuilder {

    private DeleteBuilder() {}

    /** 基于主键列的单行 {@code DELETE}。 */
    @API(status = API.Status.STABLE)
    public static String buildSingleDelete(Class<?> cls) {
        EntityModel model = EntityModelRegistry.get(cls);
        return String.format(
                "DELETE FROM %s WHERE %s = ?", model.tableName(), model.idColumnName());
    }

    /**
     * 通过主键 IN 子句进行批量 {@code DELETE}。调用方提供 {@code batchSize}，
     * 从而避免按引用传递集合。
     */
    @API(status = API.Status.STABLE)
    public static String buildBatchDelete(Class<?> cls, int batchSize) {
        if (batchSize <= 0) {
            throw new JormException(
                    ErrorCode.INVALID_ENTITY, "batch size must be >= 1: " + batchSize);
        }
        EntityModel model = EntityModelRegistry.get(cls);
        StringBuilder inList = new StringBuilder(batchSize * 2);
        inList.append("?");
        for (int i = 1; i < batchSize; i++) {
            inList.append(",?");
        }
        return String.format(
                "DELETE FROM %s WHERE %s IN (%s)", model.tableName(), model.idColumnName(), inList);
    }

    /** 带可选 {@code LIMIT/OFFSET} 的条件 {@code DELETE}。 */
    @API(status = API.Status.STABLE)
    public static String buildClassDelete(
            Class<?> cls,
            List<Condition> conditions,
            Integer limit,
            Integer offset,
            Dialect dialect) {
        SqlValidator.validateLimit(limit, offset);
        EntityModel model = EntityModelRegistry.get(cls);
        SqlValidator.validateConditions(model, conditions, ErrorCode.INVALID_COLUMN);

        StringBuilder sql = new StringBuilder(96).append("DELETE FROM ").append(model.tableName());

        if (conditions != null && !conditions.isEmpty()) {
            List<String> whereParts = new ArrayList<>(conditions.size());
            for (Condition cond : conditions) {
                whereParts.add(SqlBuilderHelper.renderCondition(cond));
            }
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }

        if (limit != null || offset != null) {
            if (dialect != null && dialect.supportsIdentity()) {
                // ChatColor 的默认实现会渲染 `LIMIT ? [OFFSET ?]`
                String clause = dialect.getLimitClause(limit, offset);
                if (clause != null && !clause.isEmpty()) {
                    sql.append(clause);
                }
            } else if (limit != null) {
                sql.append(" LIMIT ").append(limit);
            }
        }
        return sql.toString();
    }
}
