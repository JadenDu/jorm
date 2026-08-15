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
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.SaveBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.util.SessionHelper;

/**
 * INSERT session. Features:
 *
 * <ul>
 *   <li>Auto-fill of {@code null} {@code UUID}-strategy primary keys.
 *   <li>Validation of {@code @Column(nullable = false)} fields up-front.
 *   <li>Chunked batch insert for large lists; chunk size and the MySQL {@code max_allowed_packet}
 *       boundary are gated by {@link Jorm#batchSize()}.
 *   <li>Duplicate-key violations are surfaced as {@link DuplicateKeyException} across dialects,
 *       classified by {@link io.github.jadendu.dialect.Dialect#isDuplicateKey}.
 *   <li>All emitted events run through {@link AfterCommitHooks#register} so cache eviction waits
 *       for a real commit when Spring/JORM tx is active.
 * </ul>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class SaveSession extends BaseSession<SaveSession> {

    private static final Logger log = LoggerFactory.getLogger(SaveSession.class);

    /**
     * Mirror of the per-class nullable-field cache kept in old `nonNullableFieldsCache`; here via
     * reflection.
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
    //  Single-row save
    // ----------------------------------------------------------------

    /** Insert {@code entity} and write back any auto-generated primary key. */
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
            throw classifiedSqlException(sql, e);
        } catch (IllegalAccessException e) {
            log.error("parameter binding failed: {}", sql, e);
            throw new JormException(ErrorCode.PARAMETER_BINDING_FAILED, e);
        }
        evictOnCommit(entity.getClass());
    }

    /** Deprecated alias for {@link #save}. */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> void Save(T entity) {
        save(entity);
    }

    // ----------------------------------------------------------------
    //  Chunked batch save
    // ----------------------------------------------------------------

    /**
     * Insert {@code entities}; emit chunked multi-row INSERTs. Each chunk is at most {@link
     * Jorm#batchSize()} rows to respect {@code max_allowed_packet} and Surface insert-cost budgets.
     *
     * @return list of generated primary-key ids, one per entity. When the database does not
     *     populate {@code getGeneratedKeys()} per-row (e.g. some older drivers), some entries may
     *     be {@code null}.
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
        evictOnCommit(entities.get(0).getClass());
        return ids;
    }

    /** Deprecated alias for {@link #batchSave}. */
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
                // Some drivers collapse to a single key for batch — pad to size.
                while (ids.size() < size) ids.add(null);
                return ids;
            }
        } catch (SQLException e) {
            throw classifiedSqlException(sql, e);
        } catch (IllegalAccessException e) {
            log.error("batch parameter binding failed: {}", sql, e);
            throw new JormException(ErrorCode.PARAMETER_BINDING_FAILED, e);
        }
    }

    // ----------------------------------------------------------------
    //  Helpers
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
            // Field is setAccessible(true) by ColumnMapping; not expected.
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
}
