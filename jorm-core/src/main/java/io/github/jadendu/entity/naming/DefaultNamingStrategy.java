// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * 转换规则:将 Java 标识符(camelCase 或 PascalCase)转换为 {@code snake_case}
 * 形式的物理 SQL 标识符。这是 JORM 开箱即用的策略,与主流的 Java/SQL 惯例保持一致。
 *
 * <p>示例:
 *
 * <ul>
 *   <li>{@code firstName} {@code ->} {@code first_name}
 *   <li>{@code HTTPExecutor} {@code ->} {@code h_t_t_p_executor}(连续的大写缩写会被拆分;参见
 *       <b>局限性</b>)
 *   <li>{@code User} {@code ->} {@code users}(单数 {@code ->} 复数)
 *   <li>{@code OrderItem} {@code ->} {@code order_items}
 * </ul>
 *
 * <p><b>局限性:</b> 复数化规则较为简单——只添加一个 {@code "s"} 并将结尾的
 * {@code "y" -> "ies"}。当你的领域需要更智能的规则时,请提供自定义的 {@link NamingStrategy}。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DefaultNamingStrategy implements NamingStrategy {

    /** 单例实例——该类是无状态的。 */
    public static final DefaultNamingStrategy INSTANCE = new DefaultNamingStrategy();

    @Override
    public String toColumnName(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(propertyName.length() + 4);
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toTableName(String simpleClassName) {
        String snake = toColumnName(simpleClassName);
        if (snake.isEmpty()) {
            return snake;
        }
        if (snake.endsWith("y") && !endsWithVowelBeforeY(snake)) {
            return snake.substring(0, snake.length() - 1) + "ies";
        }
        if (snake.endsWith("s")
                || snake.endsWith("x")
                || snake.endsWith("ch")
                || snake.endsWith("sh")) {
            return snake + "es";
        }
        return snake + "s";
    }

    private static boolean endsWithVowelBeforeY(String snake) {
        if (snake.length() < 2) {
            return false;
        }
        char prev = snake.charAt(snake.length() - 2);
        return prev == 'a' || prev == 'e' || prev == 'i' || prev == 'o' || prev == 'u';
    }
}
