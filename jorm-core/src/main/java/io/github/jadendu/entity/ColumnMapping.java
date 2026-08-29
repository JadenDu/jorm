// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import java.lang.reflect.Field;

import org.apiguardian.api.API;

/**
 * Java 反射 {@link Field} 与其解析后的物理 SQL 列之间的不可变配对。
 * 由 {@link EntityModel} 在实体元数据构建期间生成,
 * 并在类的整个生命周期内缓存。
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
        // 为每个被缓存的字段预先授权一次反射访问;
        // 另一种做法——每次读/查询时都调用 setAccessible(true)——在热路径上
        // 会白白浪费性能。
        field.setAccessible(true);
    }

    /** Java 反射句柄。已调用过 {@code setAccessible(true)}。 */
    public Field field() {
        return field;
    }

    /** Java 属性名。 */
    public String propertyName() {
        return propertyName;
    }

    /**
     * 物理 SQL 列名(经过 {@link io.github.jadendu.entity.naming.NamingStrategy} 处理后)。
     */
    public String columnName() {
        return columnName;
    }

    /** 当 {@code @Column(nullable = false)} 禁止 null 时返回 {@code false}。 */
    public boolean nullable() {
        return nullable;
    }
}
