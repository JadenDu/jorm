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
 * SQL 构建器层共用的防御性校验器。集中这些校验可确保 {@code SELECT}/{@code UPDATE}/{@code DELETE}
 * 获得一致的 SQL 注入防护。
 *
 * <p>所有失败都会抛出携带特定 {@link ErrorCode} 的 {@link JormException};异常
 * 会在消息中包含违规输入,因此调用方可以原样展示。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SqlValidator {

    /**
     * 仅限 WHERE/HAVING 使用的运算符。逻辑运算符 {@code NOT/AND/OR} 被有意排除——它们
     * 应表达为独立的 {@code . Where(...)} 子句,以确保参数绑定的安全性。
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

    /** 允许的 ORDER BY 排序方向。 */
    private static final Set<String> ALLOWED_DIRECTIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ASC", "DESC")));

    private SqlValidator() {}

    /** 拒绝 null;当断言为肯定前置条件时使用。 */
    public static void require(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new JormException(code);
        }
    }

    /** 通用的非空断言,为兼容旧调用方而保留。 */
    public static void throwAway(Object obj, ErrorCode code) {
        require(obj, code);
    }

    /**
     * 校验每个条件列都存在于实体上。条件按声明顺序
     * 校验;第一个违规立即抛异常并短路。
     */
    public static void validateConditions(
            EntityModel model, List<Condition> conditions, ErrorCode codeToThrow) {
        validateConditions(model, conditions, codeToThrow, false);
    }

    /**
     * 与 {@link #validateConditions(EntityModel, List, ErrorCode)} 相同,但提供选项
     * 允许将聚合表达式(如 {@code COUNT(*)}、{@code SUM(price)})作为条件
     * 列——用于 HAVING 子句,它作用于分组聚合结果而非原始列。
     */
    public static void validateConditions(
            EntityModel model,
            List<Condition> conditions,
            ErrorCode codeToThrow,
            boolean allowAggregate) {
        if (conditions == null) return;
        for (Condition cond : conditions) {
            if (cond == null) continue;
            String col = cond.getColumn();
            if (!model.isValidColumn(col)) {
                if (allowAggregate && isAggregateExpression(col)) {
                    // HAVING 中允许使用聚合表达式。
                } else {
                    throw new JormException(codeToThrow, "unknown column: " + col);
                }
            }
            validateOperator(cond.getOperator());
        }
    }

    /**
     * 当 {@code expr} 看起来像聚合函数调用或由 {@code SELECT ... AS alias} 投影引入的
     * 别名时返回 true。识别作用于某列或 {@code *} 上的 {@code COUNT/SUM/AVG/MIN/MAX/
     * GROUP_CONCAT/DISTINCT},可选地后跟 {@code AS alias}。
     */
    static boolean isAggregateExpression(String expr) {
        if (expr == null || expr.trim().isEmpty()) return false;
        String regex =
                "(?i)(SUM|COUNT|AVG|MAX|MIN|GROUP_CONCAT|DISTINCT)\\s*\\(([*]|[a-zA-Z_][\\w]*)\\)"
                        + "(\\s+AS\\s+[a-zA-Z_][\\w]*)?";
        return expr.trim().matches(regex);
    }

    /** 拒绝空值或未知运算符。 */
    public static void validateOperator(String operator) {
        if (operator == null || !ALLOWED_OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
            throw new JormException(
                    ErrorCode.INVALID_OPERATOR, "operator not allowed: " + operator);
        }
    }

    /** 对照实体白名单校验每个 {@code ORDER BY} 子句。 */
    public static void validateOrderBy(EntityModel model, String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) return;
        for (String part : orderBy.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] pieces = trimmed.split("\\s+");
            String columnName = pieces[0];
            if (columnName.equalsIgnoreCase("ASC") || columnName.equalsIgnoreCase("DESC")) {
                // 容忍前置的排序方向 "DESC my_col" — 不常见但无害。
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
     * 校验逗号分隔的 GROUP BY 表达式。单列和多列表达式
     * 均接受;允许存在空白。
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
     * 拒绝为负数的 {@code limit} 或 {@code offset} 值(offset 允许为零)。
     */
    public static void validateLimit(Integer limit, Integer offset) {
        if (limit != null && limit < 0) {
            throw new JormException(ErrorCode.INVALID_LIMIT, "limit must be >= 0: " + limit);
        }
        if (offset != null && offset < 0) {
            throw new JormException(ErrorCode.INVALID_OFFSET, "offset must be >= 0: " + offset);
        }
    }

    /** SELECT 子句白名单:列名、别名以及对列上的聚合函数。 */
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
