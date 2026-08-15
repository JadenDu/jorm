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
 * Cache interface for {@link EntityModel}s, keyed by entity class.
 *
 * <p>Reflection over entity types is amortised: each unique class pays the cost once (at first
 * lookup), subsequent lookups go through a {@link ConcurrentHashMap} that is read-hot in practice.
 *
 * <p>The active {@link NamingStrategy} is held statically and is consulted at model-build time.
 * Changing the strategy {@linkplain #setNamingStrategy(NamingStrategy) re-sets the cache}
 * automatically — but doing so after bootstrap is rare and must be coordinated by a single thread
 * or during DI/Spring Boot auto-config.
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
     * Resolve the {@link EntityModel} for {@code cls}, building & caching on first encounter.
     * Throws a {@link JormException} when the class is not a valid JORM entity (missing
     * {@code @Table} is allowed — the default naming strategy synthesises a name — but a missing
     * {@code @Id} is rejected).
     */
    public static EntityModel get(Class<?> cls) {
        if (cls == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        return CACHE.computeIfAbsent(cls, EntityModelRegistry::build);
    }

    /** Current strategy; never null. */
    public static NamingStrategy namingStrategy() {
        return namingStrategy;
    }

    /**
     * Replace the strategy and invalidate the model cache. Call only during bootstrap; mutating in
     * flight may already-built models keep the previous physical naming.
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

    /** Look up a column mapping by Java property name or physical column name. */
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

        // Walk from the entity subclass up to (but excluding)
        // java.lang.Object. Insertable / updatable / whitelist semantics
        // are computed inside the loop to keep this method linear.
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

                // By-name lookup keyed on both Java & SQL identifier.
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

    /** User-supplied PK → include; {@code AUTO/IDENTITY/SEQUENCE/TABLE} → exclude (DB-side). */
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

    /** Test-only hook: invalidate the cache without touching the strategy. */
    static void clear() {
        CACHE.clear();
    }
}
