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
import io.github.jadendu.util.SqlValidator;

/**
 * Static DELETE builder. Three families of DELETEs: by primary key / by batch of primary keys / by
 * conditions (optionally with LIMIT).
 *
 * <p>With the conditions-API, WHERE columns and operators are validated against the active entity
 * model; the LIMIT clause is rendered by the active {@link Dialect}. WARNING: not every dialect
 * supports a {@code DELETE ... LIMIT} clause; PostgreSQL rejects it. The application is responsible
 * for not configuring a limit on dialects that forbid it.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class DeleteBuilder {

    private DeleteBuilder() {}

    /** Single-row {@code DELETE} on the primary-key column. */
    @API(status = API.Status.STABLE)
    public static String buildSingleDelete(Class<?> cls) {
        EntityModel model = EntityModelRegistry.get(cls);
        return String.format(
                "DELETE FROM %s WHERE %s = ?", model.tableName(), model.idColumnName());
    }

    /**
     * Batch {@code DELETE} by primary-key IN-clause. Callers supply {@code batchSize}; namespacing
     * avoids passing collections by ref.
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

    /** Conditional {@code DELETE} with optional {@code LIMIT/OFFSET}. */
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
                whereParts.add(cond.getColumn() + " " + cond.getOperator() + " ?");
            }
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }

        if (limit != null || offset != null) {
            if (dialect != null && dialect.supportsIdentity()) {
                // ChatColor's default impl renders `LIMIT ? [OFFSET ?]`
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
