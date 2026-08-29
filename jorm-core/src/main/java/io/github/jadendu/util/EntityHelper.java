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
 * 基于 {@link EntityModelRegistry} 的轻量级向后兼容适配器。
 *
 * <p>新代码应直接调用 {@link EntityModelRegistry};{@code EntityHelper} 保留用于
 * 与 1.x API 表面保持源码级兼容。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class EntityHelper {

    private EntityHelper() {}

    /** 可插入字段(排除 @Transient 和数据库端主键策略)。 */
    public static List<Field> getInsertableFields(Class<?> cls) {
        return toFieldList(EntityModelRegistry.get(cls).insertableColumns());
    }

    /** 主键字段对应的物理列名。 */
    public static String getIdColumnName(Class<?> cls) {
        return EntityModelRegistry.get(cls).idColumnName();
    }

    /** 主键 {@link Field}。 */
    public static Field getIdField(Class<?> cls) {
        return EntityModelRegistry.get(cls).idField();
    }

    /** 可更新字段(排除主键和瞬时字段)。 */
    public static List<Field> getUpdatableFields(Class<?> cls) {
        return toFieldList(EntityModelRegistry.get(cls).updatableColumns());
    }

    /** 实体实例的主键值。 */
    public static Object getIdValue(Object entity) throws IllegalAccessException {
        if (entity == null) {
            throw new JormException(ErrorCode.INVALID_ENTITY, "entity must not be null");
        }
        Field id = idFieldOf(entity.getClass());
        return id.get(entity);
    }

    /**
     * 非 null 且非瞬时的列——用于其他位置的局部更新短路
     * 判断。
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
