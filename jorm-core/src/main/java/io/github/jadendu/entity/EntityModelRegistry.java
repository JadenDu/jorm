// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.annotation.Aggregation;
import io.github.jadendu.annotation.Column;
import io.github.jadendu.annotation.Enum.GenerationType;
import io.github.jadendu.annotation.GeneratedValue;
import io.github.jadendu.annotation.Id;
import io.github.jadendu.annotation.Table;
import io.github.jadendu.annotation.Transient;
import io.github.jadendu.entity.naming.DefaultNamingStrategy;
import io.github.jadendu.entity.naming.IdentityNamingStrategy;
import io.github.jadendu.entity.naming.NamingStrategy;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * {@link EntityModel} 的缓存接口,以实体类为键。
 *
 * <p>实体类型的反射成本被摊薄:每个唯一的类只承担一次开销(首次查找时),之后的查找
 * 通过 {@link ConcurrentHashMap} 完成,实际运行时为读密集。
 *
 * <p>当前生效的 {@link NamingStrategy} 以静态方式持有,并在构建模型时被查询。更换策略
 * 会通过 {@linkplain #setNamingStrategy(NamingStrategy) 自动重置缓存}——但启动后再更换
 * 策略较为少见,且必须由单个线程协调,或是在 DI/Spring Boot 自动配置期间进行。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class EntityModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(EntityModelRegistry.class);

    private static final Map<Class<?>, EntityModel> CACHE = new ConcurrentHashMap<>();

    private static volatile NamingStrategy namingStrategy = DefaultNamingStrategy.INSTANCE;

    private EntityModelRegistry() {}

    /**
     * 解析 {@code cls} 对应的 {@link EntityModel},首次遇到时构建并缓存。
     * 当该类不是有效的 JORM 实体时抛出 {@link JormException}(缺少 {@code @Table}
     * 是允许的——默认命名策略会合成一个表名——但缺少 {@code @Id} 会被拒绝)。
     */
    public static EntityModel get(Class<?> cls) {
        if (cls == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        return CACHE.computeIfAbsent(cls, EntityModelRegistry::build);
    }

    /** 当前策略;永不为 null。 */
    public static NamingStrategy namingStrategy() {
        return namingStrategy;
    }

    /**
     * 替换策略并使模型缓存失效。仅应在启动阶段调用;若在运行中变更,
     * 已构建的模型仍会保留之前的物理命名。
     */
    public static void setNamingStrategy(NamingStrategy strategy) {
        if (strategy == null) {
            strategy = IdentityNamingStrategy.INSTANCE;
        }
        CACHE.clear();
        namingStrategy = strategy;
        log.debug(
                "NamingStrategy replaced with {}; entity cache cleared",
                strategy.getClass().getSimpleName());
    }

    /** 按 Java 属性名或物理列名查找列映射。 */
    public static ColumnMapping columnOf(Class<?> cls, String name) {
        return get(cls).findByName(name);
    }

    private static EntityModel build(Class<?> cls) {
        log.debug("Building EntityModel for {}", cls.getName());

        String tableName = resolveTableName(cls);
        Map<String, ColumnMapping> byProp = new LinkedHashMap<>();
        Map<String, ColumnMapping> byColumn = new LinkedHashMap<>();
        List<String> validColumns = new ArrayList<>();

        ColumnMapping idMapping = null;
        GenerationType idGenerationType = null;

        // 从实体子类向上遍历到(但不包括)java.lang.Object。
        // 可插入 / 可更新 / 白名单的语义都在循环内计算,以保持本方法为线性复杂度。
        List<ColumnMapping> insertable = new ArrayList<>();
        List<ColumnMapping> updatable = new ArrayList<>();

        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;
                boolean isTransient =
                        field.isAnnotationPresent(Transient.class)
                                || field.isAnnotationPresent(Aggregation.class);
                if (isTransient) continue;

                Column column = field.getAnnotation(Column.class);
                String propName = field.getName();
                String columnName =
                        (column != null && !column.name().isEmpty())
                                ? column.name()
                                : namingStrategy().toColumnName(propName);
                boolean nullable = column == null || column.nullable();
                ColumnMapping mapping = new ColumnMapping(field, propName, columnName, nullable);

                // 同时以 Java 标识符和 SQL 标识符为键,支持按名查找。
                byProp.put(propName, mapping);
                byColumn.put(columnName, mapping);
                validColumns.add(columnName);

                boolean isId = field.isAnnotationPresent(Id.class);
                if (isId) {
                    if (idMapping != null) {
                        throw new JormException(
                                ErrorCode.INVALID_ENTITY,
                                "class " + cls.getName() + " declares multiple @Id fields");
                    }
                    idMapping = mapping;
                    GeneratedValue gv = field.getAnnotation(GeneratedValue.class);
                    idGenerationType = gv != null ? gv.strategy() : null;
                    boolean excludeFromInsert = idGenerationExcludedFromInsert(idGenerationType);
                    if (!excludeFromInsert) {
                        insertable.add(mapping);
                    }
                    continue;
                }

                insertable.add(mapping);
                updatable.add(mapping);
            }
        }

        if (idMapping == null) {
            throw new JormException(
                    ErrorCode.INVALID_ENTITY, "class " + cls.getName() + " has no @Id field");
        }

        return new EntityModel(
                cls,
                tableName,
                idMapping,
                idGenerationType,
                insertable,
                updatable,
                byProp,
                byColumn,
                validColumns);
    }

    /** 用户提供的 PK → 包含;{@code AUTO/IDENTITY/SEQUENCE/TABLE} → 排除(由数据库侧生成)。 */
    private static boolean idGenerationExcludedFromInsert(GenerationType t) {
        if (t == null) return false;
        switch (t) {
            case IDENTITY:
            case SEQUENCE:
            case TABLE:
                return true;
            case UUID:
            case AUTO:
            default:
                return false;
        }
    }

    private static String resolveTableName(Class<?> cls) {
        Table table = cls.getAnnotation(Table.class);
        if (table != null && !table.name().isEmpty()) return table.name();
        return namingStrategy().toTableName(cls.getSimpleName());
    }

    /** 仅供测试的钩子:使缓存失效而不改动策略。 */
    static void clear() {
        CACHE.clear();
    }
}
