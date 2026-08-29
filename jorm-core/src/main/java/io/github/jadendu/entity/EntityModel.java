// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apiguardian.api.API;

import io.github.jadendu.annotation.Enum.GenerationType;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * JORM 映射实体类的已缓存元数据。
 *
 * <p>每个类由 {@link EntityModelRegistry} 构建一个实例,并被其后的每个 {@code
 * Session} / {@code Builder} 复用,因此每个类加载器最多只做一次反射。该模型
 * 一次性解析由 {@link io.github.jadendu.entity.naming.NamingStrategy} 驱动的物理列名与
 * 表名,并保存解析结果。
 *
 * <p>超类上声明的字段也会被纳入。合成字段(编译器生成的桥接字段、Lambda
 * 表达式...)会被跳过。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class EntityModel {

    private final Class<?> entityClass;
    private final String tableName;

    private final ColumnMapping idMapping;
    private final GenerationType idGenerationType;

    private final List<ColumnMapping> insertableColumns;
    private final List<ColumnMapping> updatableColumns;
    private final Map<String, ColumnMapping> columnsByPropertyName;
    private final Map<String, ColumnMapping> columnsByColumnName;
    private final List<String> validColumnNames; // 用于 SQL 注入白名单

    EntityModel(
            Class<?> entityClass,
            String tableName,
            ColumnMapping idMapping,
            GenerationType idGenerationType,
            List<ColumnMapping> insertableColumns,
            List<ColumnMapping> updatableColumns,
            Map<String, ColumnMapping> columnsByPropertyName,
            Map<String, ColumnMapping> columnsByColumnName,
            List<String> validColumnNames) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.idMapping = idMapping;
        this.idGenerationType = idGenerationType;
        this.insertableColumns = Collections.unmodifiableList(new ArrayList<>(insertableColumns));
        this.updatableColumns = Collections.unmodifiableList(new ArrayList<>(updatableColumns));
        this.columnsByPropertyName =
                Collections.unmodifiableMap(new LinkedHashMap<>(columnsByPropertyName));
        this.columnsByColumnName =
                Collections.unmodifiableMap(new LinkedHashMap<>(columnsByColumnName));
        this.validColumnNames = Collections.unmodifiableList(new ArrayList<>(validColumnNames));
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public String tableName() {
        return tableName;
    }

    public boolean hasId() {
        return idMapping != null;
    }

    public ColumnMapping idMapping() {
        if (idMapping == null) {
            throw new JormException(
                    ErrorCode.INVALID_ENTITY,
                    "class " + entityClass.getName() + " has no @Id field");
        }
        return idMapping;
    }

    public String idColumnName() {
        return idMapping().columnName();
    }

    public Field idField() {
        return idMapping().field();
    }

    public GenerationType idGenerationType() {
        return idGenerationType == null ? GenerationType.AUTO : idGenerationType;
    }

    /** 要插入的列——排除主键自动生成策略字段和瞬态字段。 */
    public List<ColumnMapping> insertableColumns() {
        return insertableColumns;
    }

    /** 参与更新的列——排除主键和瞬态字段。 */
    public List<ColumnMapping> updatableColumns() {
        return updatableColumns;
    }

    public ColumnMapping findByName(String name) {
        ColumnMapping byProp = columnsByPropertyName.get(name);
        return byProp != null ? byProp : columnsByColumnName.get(name);
    }

    /**
     * 物理列名白名单,SQL 构建器用它阻止通过 {@code Where("1=1 OR ...")}
     * 形式的输入进行 SQL 注入。按物理列名排序(字母顺序并非约定——只有元素本身有意义)。
     */
    public List<String> validColumnNames() {
        return validColumnNames;
    }

    public boolean isValidColumn(String name) {
        return columnsByColumnName.containsKey(name);
    }
}
