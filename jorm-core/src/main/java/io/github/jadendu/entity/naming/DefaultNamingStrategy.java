// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * Conversion: Java identifier (camelCase or PascalCase) {@code ->} physical SQL identifier in
 * {@code snake_case}. This is JORM's out-of-the-box strategy and aligns with the dominant Java/SQL
 * convention.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code firstName} {@code ->} {@code first_name}
 *   <li>{@code HTTPExecutor} {@code ->} {@code h_t_t_p_executor} (grouped acronyms are split; see
 *       <b>Limitations</b>)
 *   <li>{@code User} {@code ->} {@code users} (singular {@code ->} plural)
 *   <li>{@code OrderItem} {@code ->} {@code order_items}
 * </ul>
 *
 * <p><b>Limitations:</b> the pluralisation rule is naive — it adds an {@code "s"} and converts
 * trailing {@code "y" -> "ies"}. Supply a custom {@link NamingStrategy} when your domain needs
 * smarter rules.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DefaultNamingStrategy implements NamingStrategy {

    /** Singleton instance — the class is stateless. */
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
