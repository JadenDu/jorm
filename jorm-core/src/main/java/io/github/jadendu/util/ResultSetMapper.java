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
 * 将 {@link java.sql.ResultSet} 行映射为实体实例,并为每个字段选择正确的 {@link
 * TypeHandler}。映射两类字段:
 *
 * <ul>
 *   <li>通过 {@link EntityModel#findByName(String)} 映射持久化字段——它们使用元数据构建时
 *       一次性解析出的物理列名;以及
 *   <li>{@code @Transient} / 遗留的 {@code @Aggregation} 投影字段也会按其
 *       Java 属性名(或 {@code AS} 别名)映射,因此像 {@code SUM(...) AS
 *       totalAge} 这样的聚合函数仍会像以前一样精确地回填到实体上。
 * </ul>
 *
 * <p>结果集的列标签采用不区分大小写的方式比较,以同时兼容
 * 大写折叠数据库(H2/PostgreSQL)和小写折叠数据库(Linux 上的 MySQL)。
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
        // ---- 持久化字段:来自缓存的模型映射 ----
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
        // ---- 投影字段(@Transient / 已弃用的 @Aggregation) ----
        for (Field field : allDeclaredFields(cls)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            if (model.isValidColumn(field.getName())) continue;
            boolean isTransient =
                    field.isAnnotationPresent(io.github.jadendu.annotation.Transient.class)
                            || field.isAnnotationPresent(
                                    io.github.jadendu.annotation.Aggregation.class);
            if (!isTransient) continue;
            // 原样使用属性名(转为小写后比较)。
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
     * 类型层级中的所有已声明字段(排除 {@link Object} 和合成字段)。
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

    /** 为缓存反射句柄的调用方初始化字段的 setAccessible 标志。 */
    static Field persistentFieldFor(Class<?> cls, String name) {
        return io.github.jadendu.entity.EntityModelRegistry.get(cls).findByName(name).field();
    }
}
