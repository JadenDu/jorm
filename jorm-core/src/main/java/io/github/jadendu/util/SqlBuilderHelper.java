// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.util.Collection;

import org.apiguardian.api.API;

import io.github.jadendu.dto.Condition;

/**
 * SQL 构建器({@code FindBuilder}、{@code UpdateBuilder}、{@code
 * DeleteBuilder})共用的辅助类。集中实现 {@code IN} 集合展开逻辑,使每个构建器都以相同的方式
 * 渲染 {@code WHERE col IN (collection)}——即 {@code col IN (?, ?, ...)},每个元素一个
 * 占位符,而不是驱动无法绑定的有问题的 {@code col IN ?} + {@code setObject(list)}。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SqlBuilderHelper {

    private SqlBuilderHelper() {}

    /**
     * 将单个条件渲染为 SQL 片段。对于值为 {@link Collection} 或数组的 {@code IN},
     * 片段为 {@code col IN (?, ?, ...)},每个元素一个占位符。
     * 对于其他所有运算符,片段为 {@code col op ?}。
     */
    public static String renderCondition(Condition cond) {
        String op = cond.getOperator() == null ? "=" : cond.getOperator().toUpperCase();
        if ("IN".equals(op)) {
            int count = inElementCount(cond.getValue());
            if (count > 0) {
                StringBuilder placeholders = new StringBuilder(count * 2 + 4);
                placeholders.append(cond.getColumn()).append(" IN (");
                placeholders.append("?");
                for (int i = 1; i < count; i++) {
                    placeholders.append(", ?");
                }
                placeholders.append(")");
                return placeholders.toString();
            }
            // 空集合:渲染一个恒为假的谓词,使查询不返回任何行
            // (与 SQL 标准中 IN () 的语义一致)。
            return "1 = 0";
        }
        return cond.getColumn() + " " + cond.getOperator() + " ?";
    }

    /**
     * 返回 {@code IN} 值中可绑定的元素个数:集合和数组会
     * 展开;任何其他(标量)值都视为单个元素,因此 {@code where(col, "IN",
     * scalar)} 仍会渲染为 {@code col IN (?)}。
     */
    public static int inElementCount(Object value) {
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value != null && value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        return 1;
    }
}
