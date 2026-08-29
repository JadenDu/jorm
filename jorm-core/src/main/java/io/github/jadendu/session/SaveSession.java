// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.annotation.Enum.GenerationType;
import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.cache.SecondLevelCache;
import io.github.jadendu.entity.ColumnMapping;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.DuplicateKeyException;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.SaveBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.util.SessionHelper;

/**
 * INSERT 会话。特性:
 *
 * <ul>
 *   <li>自动填充为 {@code null} 的 {@code UUID} 策略主键。
 *   <li>预先校验 {@code @Column(nullable = false)} 字段。
 *   <li>针对大列表的分块批量插入;块大小与 MySQL {@code max_allowed_packet}
 *       边界由 {@link Jorm#batchSize()} 控制。
 *   <li>跨方言的主键冲突会以 {@link DuplicateKeyException} 形式暴露,
 *       由 {@link io.github.jadendu.dialect.Dialect#isDuplicateKey} 分类判定。
 *   <li>所有发出的事件都经过 {@link AfterCommitHooks#register} 处理,因此当 Spring/JORM 事务
 *       激活时,缓存驱逐会等待真正的提交。
 * </ul>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class SaveSession extends BaseSession<SaveSession> {

    private static final Logger log = LoggerFactory.getLogger(SaveSession.class);

    /**
     * 旧 `nonNullableFieldsCache` 中按类缓存非空字段列表的镜像;此处
     * 通过反射实现。
     */
    private static final Map<Class<?>, List<Field>> NON_NULL_FIELDS_CACHE =
            new ConcurrentHashMap<>();

    public SaveSession() {
        super();
    }

    public SaveSession(java.sql.Connection externalConn) {
        super(externalConn);
    }

    // ----------------------------------------------------------------
    //  单行保存
    // ----------------------------------------------------------------

    /** 插入 {@code entity},并回写任何自动生成的主键。 */
    @API(status = API.Status.STABLE)
    public <T> void save(T entity) {
        checkIfClosed();
        if (entity == null) {
            throw new JormException(ErrorCode.INVALID_ENTITY, "entity must not be null");
        }
        autofillUuidId(entity);
        validateEntity(entity);
        String sql;
        try {
            sql = SaveBuilder.buildInsert(entity.getClass());
        } catch (Exception e) {
            log.error("SQL generation failed for {}", entity.getClass(), e);
            throw new JormException(ErrorCode.SQL_GENERATION_FAILED, e);
        }
        long start = System.nanoTime();
        try (PreparedStatement stmt =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            applyQueryOptions(stmt);
            SessionHelper.setInsertParameters(stmt, entity);
            int rows = stmt.executeUpdate();
            log.debug("save affected {} row(s): {}", rows, sql);
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs != null && rs.next() && entityHasGeneratedId(entity.getClass())) {
                    SessionHelper.setIdValue(entity, rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError(elapsedMicros(start));
            throw classifiedSqlException(sql, e);
        } catch (IllegalAccessException e) {
            log.error("parameter binding failed: {}", sql, e);
            StatisticsRegistry.query().recordError(elapsedMicros(start));
            throw new JormException(ErrorCode.PARAMETER_BINDING_FAILED, e);
        }
        StatisticsRegistry.query().recordInsert(elapsedMicros(start));
        evictOnCommit(entity.getClass());
    }

    /** {@link #save} 的已弃用别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> void Save(T entity) {
        save(entity);
    }

    // ----------------------------------------------------------------
    //  分块批量保存
    // ----------------------------------------------------------------

    /**
     * 插入 {@code entities};生成分块的多行 INSERT。每块最多 {@link
     * Jorm#batchSize()} 行,以遵守 {@code max_allowed_packet} 和 Surface 的插入成本预算。
     *
     * @return 生成的主键 id 列表,每个实体对应一个。当数据库不按行填充
     *     {@code getGeneratedKeys()}(例如某些较旧的驱动)时,部分条目可能
     *     为 {@code null}。
     */
    @API(status = API.Status.STABLE)
    public <T> List<Long> batchSave(List<T> entities) {
        checkIfClosed();
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        for (T e : entities) {
            autofillUuidId(e);
            validateEntity(e);
        }
        int chunk = Math.max(1, Jorm.batchSize());
        List<Long> ids = new ArrayList<>(entities.size());
        for (int start = 0; start < entities.size(); start += chunk) {
            int end = Math.min(start + chunk, entities.size());
            ids.addAll(insertChunk(entities, start, end));
        }
        // 将生成的主键 id 回写到实体上(针对 IDENTITY/AUTO 策略),与单行 save() 的行为
        // 保持一致——这样调用方就无需再自行映射返回的列表。
        // UUID 策略实体的 id 已由 autofillUuidId 设置。
        if (entityHasGeneratedId(entities.get(0).getClass())) {
            for (int i = 0; i < entities.size() && i < ids.size(); i++) {
                Long id = ids.get(i);
                if (id != null) {
                    try {
                        SessionHelper.setIdValue(entities.get(i), id);
                    } catch (IllegalAccessException ignored) {
                        // 尽力而为;id 仍会出现在返回的列表中
                    }
                }
            }
        }
        evictOnCommit(entities.get(0).getClass());
        return ids;
    }

    /** {@link #batchSave} 的已弃用别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> List<Long> BatchSave(List<T> entities) {
        return batchSave(entities);
    }

    @SuppressWarnings("unchecked")
    private <T> List<Long> insertChunk(List<T> entities, int start, int end) {
        int size = end - start;
        Class<?> cls = entities.get(start).getClass();
        String sql;
        try {
            sql = SaveBuilder.buildBatchInsert(cls, size);
        } catch (Exception e) {
            log.error("batch insert SQL generation failed for {}", cls, e);
            throw new JormException(ErrorCode.SQL_GENERATION_FAILED, e);
        }
        long t0 = System.nanoTime();
        try (PreparedStatement stmt =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            applyQueryOptions(stmt);
            int paramIndex = 1;
            for (int i = start; i < end; i++) {
                paramIndex = SessionHelper.setInsertParameters(stmt, entities.get(i), paramIndex);
            }
            int rows = stmt.executeUpdate();
            log.debug("batch affected {} row(s): {}", rows, sql);
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                List<Long> ids = new ArrayList<>(size);
                while (rs != null && rs.next()) {
                    long v = rs.getLong(1);
                    if (rs.wasNull()) {
                        ids.add(null);
                    } else {
                        ids.add(v);
                    }
                }
                // 某些驱动对批量操作只返回单个主键——补齐到指定数量。
                while (ids.size() < size) ids.add(null);
                StatisticsRegistry.query().recordBatchInsert(elapsedMicros(t0));
                return ids;
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError(elapsedMicros(t0));
            throw classifiedSqlException(sql, e);
        } catch (IllegalAccessException e) {
            log.error("batch parameter binding failed: {}", sql, e);
            StatisticsRegistry.query().recordError(elapsedMicros(t0));
            throw new JormException(ErrorCode.PARAMETER_BINDING_FAILED, e);
        }
    }

    // ----------------------------------------------------------------
    //  辅助方法
    // ----------------------------------------------------------------

    private static <T> void autofillUuidId(T entity) {
        EntityModel model = EntityModelRegistry.get(entity.getClass());
        if (!model.hasId()) return;
        if (model.idGenerationType() != GenerationType.UUID) return;
        Field id = model.idField();
        try {
            Object current = id.get(entity);
            if (current == null) {
                id.set(entity, UUID.randomUUID());
            }
        } catch (IllegalAccessException e) {
            // 字段已由 ColumnMapping 设置为 setAccessible(true);此处不应发生。
            throw new JormException(
                    ErrorCode.REFLECTION_ACCESS_FAILED,
                    "could not auto-fill UUID for " + entity.getClass(),
                    e);
        }
    }

    private static boolean entityHasGeneratedId(Class<?> cls) {
        EntityModel model = EntityModelRegistry.get(cls);
        GenerationType t = model.idGenerationType();
        return t == GenerationType.IDENTITY
                || t == GenerationType.AUTO
                || t == GenerationType.SEQUENCE
                || t == GenerationType.TABLE;
    }

    private <T> void validateEntity(T entity) {
        Class<?> cls = entity.getClass();
        List<Field> nonNulls =
                NON_NULL_FIELDS_CACHE.computeIfAbsent(cls, SaveSession::collectNonNullFields);
        for (Field f : nonNulls) {
            try {
                if (f.get(entity) == null) {
                    throw new JormException(
                            ErrorCode.INVALID_COLUMN,
                            "column '"
                                    + f.getName()
                                    + "' on "
                                    + cls.getName()
                                    + " is non-nullable: violates @Column(nullable=false)");
                }
            } catch (IllegalAccessException e) {
                throw new JormException(ErrorCode.REFLECTION_ACCESS_FAILED, e);
            }
        }
    }

    private static List<Field> collectNonNullFields(Class<?> cls) {
        EntityModel model = EntityModelRegistry.get(cls);
        List<Field> fields = new ArrayList<>();
        for (ColumnMapping col : model.insertableColumns()) {
            if (!col.nullable()) fields.add(col.field());
        }
        for (ColumnMapping col : model.updatableColumns()) {
            if (!col.nullable()) fields.add(col.field());
        }
        return fields;
    }

    private void evictOnCommit(Class<?> cls) {
        if (!CacheManager.isCacheEnabled()) return;
        SecondLevelCache cache = CacheManager.getSecondLevelCache();
        if (cache == null) return;
        final String region = cls.getName();
        AfterCommitHooks.register(
                () -> {
                    try {
                        cache.clearRegion(region);
                        log.debug("L2 cache cleared for {} after commit", region);
                    } catch (Throwable t) {
                        log.warn("L2 cache clear failed for {}", region, t);
                    }
                });
    }

    private JormException classifiedSqlException(String sql, SQLException e) {
        if (Jorm.dialect() != null && Jorm.dialect().isDuplicateKey(e)) {
            return new DuplicateKeyException("INSERT failed (" + sql + ")", e);
        }
        return new JormException(ErrorCode.SQL_EXECUTION_FAILED, "SQL=" + sql, e);
    }

    @Override
    protected SaveSession self() {
        return this;
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1000L;
    }
}
