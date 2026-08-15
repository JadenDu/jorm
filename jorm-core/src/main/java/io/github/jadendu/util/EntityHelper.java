// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apiguardian.api.API;

import io.github.jadendu.entity.ColumnMapping;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Thin backward-compatible adapter over {@link EntityModelRegistry}.
 *
 * <p>New code should call {@link EntityModelRegistry} directly; {@code EntityHelper} remains for
 * source-level compatibility with the 1.x API surface.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class EntityHelper {

    private EntityHelper() {}

    /** Insertable fields (excludes @Transient and DB-side PK strategies). */
    public static List<Field> getInsertableFields(Class<?> cls) {
        return toFieldList(EntityModelRegistry.get(cls).insertableColumns());
    }

    /** Primary-key field's physical column name. */
    public static String getIdColumnName(Class<?> cls) {
        return EntityModelRegistry.get(cls).idColumnName();
    }

    /** Primary-key {@link Field}. */
    public static Field getIdField(Class<?> cls) {
        return EntityModelRegistry.get(cls).idField();
    }

    /** Updatable fields (excludes primary key and transient). */
    public static List<Field> getUpdatableFields(Class<?> cls) {
        return toFieldList(EntityModelRegistry.get(cls).updatableColumns());
    }

    /** Primary-key value of an entity instance. */
    public static Object getIdValue(Object entity) throws IllegalAccessException {
        if (entity == null) {
            throw new JormException(ErrorCode.INVALID_ENTITY, "entity must not be null");
        }
        Field id = idFieldOf(entity.getClass());
        return id.get(entity);
    }

    /**
     * Columns that are non-null-and-not-transient — used for partial-update short-circuiting
     * elsewhere.
     */
    public static Map<String, Object> getNonNullFields(Object entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        EntityModel model = EntityModelRegistry.get(entity.getClass());
        for (ColumnMapping col : model.updatableColumns()) {
            try {
                Object value = col.field().get(entity);
                if (value != null) {
                    out.put(col.columnName(), value);
                }
            } catch (IllegalAccessException e) {
                throw new JormException(
                        ErrorCode.REFLECTION_ACCESS_FAILED,
                        "field access failed: " + col.propertyName(),
                        e);
            }
        }
        return out;
    }

    private static Field idFieldOf(Class<?> cls) {
        return EntityModelRegistry.get(cls).idField();
    }

    private static List<Field> toFieldList(List<ColumnMapping> cols) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>(cols.size());
        for (ColumnMapping col : cols) {
            fields.add(col.field());
        }
        return fields;
    }
}
