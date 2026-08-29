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
 * 用于 INSERT 的、基于 PreparedStatement 的参数绑定辅助类。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SessionHelper {

    private SessionHelper() {}

    /** INSERT 后将自动生成的主键列回写到 {@code entity}。 */
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
        // 常见的拆箱路径:long → Long → 装箱类型等。
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
     * 从参数索引 1 开始的单实体 INSERT 绑定。返回下一个可用索引。
     */
    public static int setInsertParameters(PreparedStatement stmt, Object entity)
            throws IllegalAccessException, SQLException {
        return setInsertParameters(stmt, entity, 1);
    }

    /** 从 {@code startIndex} 开始的单实体 INSERT 绑定。 */
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
            bindParameter(stmt, index, value, col.field().getType());
            index++;
        }
        return index;
    }

    /**
     * 仅供测试的辅助方法:过滤掉任何多余的 {@code @Aggregation} 后,
     * 剩余的可插入字段列表。
     */
    @SuppressWarnings("deprecation")
    public static List<Field> legacyInsertableFields(Class<?> cls) {
        return EntityModelRegistry.get(cls).insertableColumns().stream()
                .filter(f -> !f.field().isAnnotationPresent(Aggregation.class))
                .map(ColumnMapping::field)
                .collect(Collectors.toList());
    }

    /**
     * 在 {@code index} 位置将单个参数值绑定到 {@code stmt},应用写入侧的
     * {@link WriteTypeHandler} SPI。当字段类型存在用户注册的处理器时,将直接
     * 调用它(它可以自行调用 {@code setBigDecimal}、{@code setString} 等)。
     * 否则执行内置转换(Enum → name,UUID → toString),并将结果
     * 传给 {@code setObject}。{@code null} 原样透传。
     */
    public static void bindParameter(
            PreparedStatement stmt, int index, Object value, Class<?> type)
            throws SQLException {
        if (value == null) {
            stmt.setObject(index, null);
            return;
        }
        WriteTypeHandler handler = WriteTypeHandler.forType(type);
        if (handler != null) {
            handler.bind(stmt, index, value, type);
            return;
        }
        stmt.setObject(index, WriteTypeHandler.convert(value, type));
    }

    /**
     * 从索引 1 开始将参数值列表绑定到 {@code stmt},将任意
     * {@link java.util.Collection} 或数组元素展开为多个占位符(用于 {@code IN}
     * 子句)。返回所有绑定完成后的下一个可用参数索引。
     */
    public static int bindExpandedParameters(PreparedStatement stmt, List<Object> params)
            throws SQLException {
        return bindExpandedParametersFrom(stmt, params, 1);
    }

    /**
     * 与 {@link #bindExpandedParameters} 相同,但从 {@code startIndex} 开始绑定。供
     * UPDATE 使用,它先绑定 SET 子句参数,再追加 WHERE 参数。
     */
    public static int bindExpandedParametersFrom(
            PreparedStatement stmt, List<Object> params, int startIndex)
            throws SQLException {
        int index = startIndex;
        for (Object value : params) {
            if (value instanceof java.util.Collection) {
                for (Object element : (java.util.Collection<?>) value) {
                    bindParameter(stmt, index, element, element == null ? null : element.getClass());
                    index++;
                }
            } else if (value != null && value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object element = java.lang.reflect.Array.get(value, i);
                    bindParameter(stmt, index, element, element == null ? null : element.getClass());
                    index++;
                }
            } else {
                bindParameter(stmt, index, value, value == null ? null : value.getClass());
                index++;
            }
        }
        return index;
    }
}
