// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import java.lang.reflect.Field;

import org.apiguardian.api.API;

/**
 * Immutable pairing between a Java reflection {@link Field} and its resolved physical SQL column.
 * Produced by {@link EntityModel} during entity-metadata construction and cached for the life of
 * the class.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class ColumnMapping {

    private final Field field;
    private final String propertyName;
    private final String columnName;
    private final boolean nullable;

    ColumnMapping(Field field, String propertyName, String columnName, boolean nullable) {
        this.field = field;
        this.propertyName = propertyName;
        this.columnName = columnName;
        this.nullable = nullable;
        // Pre-authorise reflective access once per cached field; the
        // alternative — calling setAccessible(true) on every read/query —
        // is wasteful in a hot path.
        field.setAccessible(true);
    }

    /** The Java reflection handle. Already {@code setAccessible(true)}. */
    public Field field() {
        return field;
    }

    /** The Java property name. */
    public String propertyName() {
        return propertyName;
    }

    /**
     * The physical SQL column name (after {@link io.github.jadendu.entity.naming.NamingStrategy}).
     */
    public String columnName() {
        return columnName;
    }

    /** {@code false} when {@code @Column(nullable = false)} forbids nulls. */
    public boolean nullable() {
        return nullable;
    }
}
