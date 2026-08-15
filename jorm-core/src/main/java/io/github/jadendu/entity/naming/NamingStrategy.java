// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * Translates Java identifiers (class names, field names) into physical SQL identifiers (table
 * names, column names) consistently across the entire framework.
 *
 * <p>Implementations are stateless and must be safe to share across threads. The active strategy is
 * consulted by {@link io.github.jadendu.entity.EntityModel} when building entity metadata, so
 * swapping a strategy is only effective before entity metadata is cached.
 *
 * <p>Annotation overrides always take precedence — a field annotated with {@code @Column(name =
 * "user_name")} keeps that exact column name regardless of the strategy.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public interface NamingStrategy {

    /**
     * Derive a physical column name from a Java property name.
     *
     * @param propertyName the property's Java identifier, never null
     * @return the physical column name, never null
     */
    String toColumnName(String propertyName);

    /**
     * Derive a physical table name from a simple Java class name (no package). Used only when the
     * entity is not annotated with {@code @Table(name = "...")}.
     *
     * @param simpleClassName the simple class name, never null
     * @return the physical table name, never null
     */
    String toTableName(String simpleClassName);
}
