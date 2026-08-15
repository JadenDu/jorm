// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.apiguardian.api.API;

import io.github.jadendu.annotation.Aggregation;
import io.github.jadendu.entity.ColumnMapping;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Per-prepared-statement parameter-binding helpers for INSERTs.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SessionHelper {

    private SessionHelper() {}

    /** Write the auto-generated primary-key column back into {@code entity} after INSERT. */
    public static <T> void setIdValue(T entity, Object idValue) throws IllegalAccessException {
        if (entity == null) return;
        Field idField = EntityModelRegistry.get(entity.getClass()).idField();
        Class<?> type = idField.getType();
        if (idValue == null) {
            idField.set(entity, null);
            return;
        }
        if (type.isAssignableFrom(idValue.getClass())) {
            idField.set(entity, idValue);
            return;
        }
        // Common unboxing path: long → Long → boxed, etc.
        if (idValue instanceof Number) {
            Number n = (Number) idValue;
            if (type == Long.class || type == long.class) {
                idField.set(entity, n.longValue());
                return;
            }
            if (type == Integer.class || type == int.class) {
                idField.set(entity, n.intValue());
                return;
            }
            if (type == Short.class || type == short.class) {
                idField.set(entity, n.shortValue());
                return;
            }
            if (type == Byte.class || type == byte.class) {
                idField.set(entity, n.byteValue());
                return;
            }
            if (type == java.util.UUID.class) {
                idField.set(entity, java.util.UUID.fromString(idValue.toString()));
                return;
            }
        }
        idField.set(entity, idValue);
    }

    /**
     * One-entity INSERT binding starting at parameter index 1. Returns the next available index.
     */
    public static int setInsertParameters(PreparedStatement stmt, Object entity)
            throws IllegalAccessException, SQLException {
        return setInsertParameters(stmt, entity, 1);
    }

    /** One-entity INSERT binding starting at {@code startIndex}. */
    public static int setInsertParameters(PreparedStatement stmt, Object entity, int startIndex)
            throws IllegalAccessException, SQLException {
        EntityModel model = EntityModelRegistry.get(entity.getClass());
        int index = startIndex;
        for (ColumnMapping col : model.insertableColumns()) {
            Object value = col.field().get(entity);
            if (value == null && !col.nullable()) {
                throw new JormException(
                        ErrorCode.INVALID_COLUMN,
                        "column '"
                                + col.columnName()
                                + "' on "
                                + entity.getClass().getName()
                                + " is declared nullable=false and cannot be null");
            }
            stmt.setObject(index++, value);
        }
        return index;
    }

    /**
     * Test-only helper: the remaining insertable Field list, filtering out any stray
     * {@code @Aggregation}.
     */
    @SuppressWarnings("deprecation")
    public static List<Field> legacyInsertableFields(Class<?> cls) {
        return EntityModelRegistry.get(cls).insertableColumns().stream()
                .filter(f -> !f.field().isAnnotationPresent(Aggregation.class))
                .map(ColumnMapping::field)
                .collect(Collectors.toList());
    }
}
