// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Map a {@link java.sql.ResultSet} row into an entity instance, choosing the correct {@link
 * TypeHandler} per field. Two kinds of fields are mapped:
 *
 * <ul>
 *   <li>persistent fields via {@link EntityModel#findByName(String)} — these use the physical
 *       column name resolved once at metadata-build time; and
 *   <li>{@code @Transient} / legacy {@code @Aggregation} projection fields are also mapped by their
 *       Java property name (or {@code AS} alias), so aggregate functions like {@code SUM(...) AS
 *       totalAge} continue to land back on the entity exactly as before.
 * </ul>
 *
 * <p>Result-set column labels are compared case-insensitively to be friendly to BOTH
 * uppercase-folding databases (H2/PostgreSQL) and lowercase-folding ones (MySQL on Linux).
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class ResultSetMapper {

    private static final Logger log = LoggerFactory.getLogger(ResultSetMapper.class);

    public static <T> T mapToEntity(java.sql.ResultSet rs, Class<T> cls)
            throws java.sql.SQLException, IllegalAccessException, InstantiationException {
        if (!rs.next()) return null;
        return mapRowToEntity(rs, cls);
    }

    public static <T> java.util.List<T> mapToList(java.sql.ResultSet rs, Class<T> cls)
            throws java.sql.SQLException, IllegalAccessException, InstantiationException {
        java.util.List<T> list = new java.util.ArrayList<>();
        while (rs.next()) {
            list.add(mapRowToEntity(rs, cls));
        }
        return list;
    }

    private static <T> T mapRowToEntity(java.sql.ResultSet rs, Class<T> cls)
            throws java.sql.SQLException, IllegalAccessException, InstantiationException {
        T entity = cls.newInstance();
        java.sql.ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        Set<String> columnNamesLower = new HashSet<>(count);
        java.util.Map<String, String> aliasToLowerName = new java.util.HashMap<>(count);
        for (int i = 1; i <= count; i++) {
            String label = md.getColumnLabel(i);
            if (label == null || label.isEmpty()) {
                label = md.getColumnName(i);
            }
            String lower = label.toLowerCase();
            columnNamesLower.add(lower);
            aliasToLowerName.put(lower, label);
        }

        EntityModel model = EntityModelRegistry.get(cls);
        // ---- Persistent fields: from the cached model mapping ----
        for (io.github.jadendu.entity.ColumnMapping mapping : model.insertableColumns()) {
            fillField(
                    rs,
                    entity,
                    mapping.field(),
                    mapping.columnName(),
                    columnNamesLower,
                    aliasToLowerName);
        }
        for (io.github.jadendu.entity.ColumnMapping mapping : model.updatableColumns()) {
            fillField(
                    rs,
                    entity,
                    mapping.field(),
                    mapping.columnName(),
                    columnNamesLower,
                    aliasToLowerName);
        }
        if (model.hasId()) {
            io.github.jadendu.entity.ColumnMapping id = model.idMapping();
            fillField(rs, entity, id.field(), id.columnName(), columnNamesLower, aliasToLowerName);
        }
        // ---- Projection fields (@Transient / deprecated @Aggregation) ----
        for (Field field : allDeclaredFields(cls)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            if (model.isValidColumn(field.getName())) continue;
            boolean isTransient =
                    field.isAnnotationPresent(io.github.jadendu.annotation.Transient.class)
                            || field.isAnnotationPresent(
                                    io.github.jadendu.annotation.Aggregation.class);
            if (!isTransient) continue;
            // Use the property name verbatim (lower-case comparison).
            fillField(rs, entity, field, field.getName(), columnNamesLower, aliasToLowerName);
        }
        return entity;
    }

    private static void fillField(
            java.sql.ResultSet rs,
            Object entity,
            Field field,
            String columnName,
            Set<String> columnNamesLower,
            java.util.Map<String, String> aliasToLowerName)
            throws IllegalAccessException {
        String key = columnName.toLowerCase();
        if (!columnNamesLower.contains(key)) return;
        String physical = aliasToLowerName.getOrDefault(key, columnName);
        Object value = getValueByFieldType(rs, physical, field.getType());
        if (value != null) {
            field.set(entity, value);
        }
    }

    /**
     * All declared fields across the type hierarchy (excludes {@link Object} and synthetic ones).
     */
    private static Iterable<Field> allDeclaredFields(Class<?> cls) {
        java.util.List<Field> out = new java.util.ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out;
    }

    private static Object getValueByFieldType(
            java.sql.ResultSet rs, String columnName, Class<?> fieldType) throws JormException {
        try {
            TypeHandler handler = TypeHandler.forType(fieldType);
            return handler != null
                    ? handler.handle(rs, columnName, fieldType)
                    : rs.getObject(columnName);
        } catch (java.sql.SQLException e) {
            String name;
            try {
                int idx = rs.findColumn(columnName);
                name = rs.getMetaData().getColumnTypeName(idx);
            } catch (java.sql.SQLException ignored) {
                name = "UNKNOWN";
            }
            throw new JormException(
                    ErrorCode.TYPE_MISMATCH,
                    "Column '"
                            + columnName
                            + "' (SQL type: "
                            + name
                            + ") cannot map to Java type "
                            + fieldType.getName(),
                    e);
        }
    }

    /** Initialise the field's setAccessible flag for callers that cache reflection handles. */
    static Field persistentFieldFor(Class<?> cls, String name) {
        return io.github.jadendu.entity.EntityModelRegistry.get(cls).findByName(name).field();
    }
}
