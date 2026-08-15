// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apiguardian.api.API;

import io.github.jadendu.dto.Condition;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Defensive validators shared by the SQL builder layer. Centralising these guarantees identical
 * SQL-injection protection for {@code SELECT}/{@code UPDATE}/{@code DELETE}.
 *
 * <p>All failures raise a {@link JormException} with a specific {@link ErrorCode}; the exception
 * carries the offending input in the message, so callers can surface it verbatim.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SqlValidator {

    /**
     * WHERE/HAVING-only operators. Logical {@code NOT/AND/OR} are intentionally excluded — they
     * should be expressed as separate {@code . Where(...)} clauses for safe parameter binding.
     */
    private static final Set<String> ALLOWED_OPERATORS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "=",
                                    "!=",
                                    "<>",
                                    ">",
                                    "<",
                                    ">=",
                                    "<=",
                                    "LIKE",
                                    "NOT LIKE",
                                    "IN",
                                    "IS",
                                    "IS NOT")));

    /** Allowed ORDER BY directions. */
    private static final Set<String> ALLOWED_DIRECTIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ASC", "DESC")));

    private SqlValidator() {}

    /** Reject null; required when the assertion is positive pre-condition. */
    public static void require(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new JormException(code);
        }
    }

    /** Generic non-null assertion, kept for back-compat with old call sites. */
    public static void throwAway(Object obj, ErrorCode code) {
        require(obj, code);
    }

    /**
     * Verify that every condition column exists on the entity. Conditions are validated in
     * declaration order; the first violation throws and short-circuits.
     */
    public static void validateConditions(
            EntityModel model, List<Condition> conditions, ErrorCode codeToThrow) {
        if (conditions == null) return;
        for (Condition cond : conditions) {
            if (cond == null) continue;
            if (!model.isValidColumn(cond.getColumn())) {
                throw new JormException(codeToThrow, "unknown column: " + cond.getColumn());
            }
            validateOperator(cond.getOperator());
        }
    }

    /** Reject empty/unknown operators. */
    public static void validateOperator(String operator) {
        if (operator == null || !ALLOWED_OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
            throw new JormException(
                    ErrorCode.INVALID_OPERATOR, "operator not allowed: " + operator);
        }
    }

    /** Verify each {@code ORDER BY} clause against the entity whitelist. */
    public static void validateOrderBy(EntityModel model, String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) return;
        for (String part : orderBy.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] pieces = trimmed.split("\\s+");
            String columnName = pieces[0];
            if (columnName.equalsIgnoreCase("ASC") || columnName.equalsIgnoreCase("DESC")) {
                // Tolerate leading direction "DESC my_col" — unusual but not harmful.
                if (pieces.length < 2) {
                    throw new JormException(
                            ErrorCode.INVALID_SORT_EXPRESSION, "ambiguous ORDER BY: " + trimmed);
                }
                columnName = pieces[1];
            }
            if (!model.isValidColumn(columnName)) {
                throw new JormException(
                        ErrorCode.INVALID_COLUMN, "ORDER BY unknown column: " + columnName);
            }
            if (pieces.length > 1) {
                String direction = pieces[pieces.length - 1].toUpperCase(Locale.ROOT);
                if (!ALLOWED_DIRECTIONS.contains(direction)) {
                    throw new JormException(
                            ErrorCode.INVALID_ORDER_DIRECTION,
                            "ORDER BY direction not allowed: " + direction);
                }
            }
        }
    }

    /**
     * Verify a comma-separated GROUP BY expression. Single-column and multi-column expressions are
     * both accepted; whitespace is tolerated.
     */
    public static void validateGroupBy(EntityModel model, String groupBy) {
        if (groupBy == null || groupBy.trim().isEmpty()) return;
        for (String piece : groupBy.split(",")) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) continue;
            if (!model.isValidColumn(trimmed)) {
                throw new JormException(
                        ErrorCode.INVALID_COLUMN, "GROUP BY unknown column: " + trimmed);
            }
        }
    }

    /**
     * Reject {@code limit} or {@code offset} values that are not strictly positive or zero (for
     * offset).
     */
    public static void validateLimit(Integer limit, Integer offset) {
        if (limit != null && limit < 0) {
            throw new JormException(ErrorCode.INVALID_LIMIT, "limit must be >= 0: " + limit);
        }
        if (offset != null && offset < 0) {
            throw new JormException(ErrorCode.INVALID_OFFSET, "offset must be >= 0: " + offset);
        }
    }

    /** Select-clause whitelist: column names, aliases, and aggregate functions over a column. */
    public static void validateSelectClause(EntityModel model, String selectClause) {
        if (selectClause == null || selectClause.equals("*") || selectClause.trim().isEmpty())
            return;
        for (String piece : selectClause.split(",")) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) continue;
            String regex =
                    "(?i)([a-zA-Z_][\\w]*(\\s+AS\\s+[a-zA-Z_][\\w]*)?"
                            + "|(SUM|COUNT|AVG|MAX|MIN|GROUP_CONCAT|DISTINCT)\\s*\\(([*]|([a-zA-Z_][\\w]*))\\)(\\s+AS\\s+[a-zA-Z_][\\w]*)?"
                            + "|[*])";
            if (!trimmed.matches(regex)) {
                throw new JormException(
                        ErrorCode.INVALID_SELECT_CLAUSE, "illegal SELECT fragment: " + trimmed);
            }
        }
    }
}
