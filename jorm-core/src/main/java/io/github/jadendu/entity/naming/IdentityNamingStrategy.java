// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * Pass-through strategy: Java identifiers become physical SQL identifiers verbatim. Useful on
 * databases (PostgreSQL quoted, Oracle) where the schema is already defined in camelCase or
 * PascalCase.
 *
 * <p>Tables are <em>not</em> pluralised; the simple class name is used as-is when no {@code @Table}
 * annotation is present.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class IdentityNamingStrategy implements NamingStrategy {

    /** Singleton instance — the class is stateless. */
    public static final IdentityNamingStrategy INSTANCE = new IdentityNamingStrategy();

    @Override
    public String toColumnName(String propertyName) {
        return propertyName == null ? "" : propertyName;
    }

    @Override
    public String toTableName(String simpleClassName) {
        return simpleClassName == null ? "" : simpleClassName;
    }
}
