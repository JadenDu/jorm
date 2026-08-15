// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.cache.SecondLevelCache;
import io.github.jadendu.dto.Condition;
import io.github.jadendu.exception.DataIntegrityException;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.DeleteBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.util.EntityHelper;

/**
 * DELETE session. Three forms supported:
 *
 * <ul>
 *   <li>{@code Delete(entity)} and {@code Delete(List<entity>)} — go by primary key.
 *   <li>{@code Delete(Class)} — conditional delete via {@code Where(...)}.
 * </ul>
 *
 * <p>Every emitted event is run-through {@link AfterCommitHooks#register} so cache eviction waits
 * for the real commit when Spring/JORM tx is active — same guarantee offered by {@link SaveSession}
 * and {@link UpdateSession}.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DeleteSession extends BaseSession<DeleteSession> {

    private static final Logger log = LoggerFactory.getLogger(DeleteSession.class);

    private final List<Condition> conditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public DeleteSession() {
        super();
    }

    public DeleteSession(Connection externalConn) {
        super(externalConn);
    }

    @API(status = API.Status.STABLE)
    public DeleteSession where(String column, Object value) {
        conditions.add(new Condition(column, "=", value));
        params.add(value);
        return self();
    }

    @API(status = API.Status.STABLE)
    public DeleteSession where(String column, String operator, Object value) {
        conditions.add(new Condition(column, operator, value));
        params.add(value);
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public DeleteSession Where(String column, Object value) {
        return where(column, value);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public DeleteSession Where(String column, String operator, Object value) {
        return where(column, operator, value);
    }

    @API(status = API.Status.STABLE)
    public DeleteSession limit(Integer limit) {
        this.limit = limit;
        return self();
    }

    @API(status = API.Status.STABLE)
    public DeleteSession offset(Integer offset) {
        this.offset = offset;
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public DeleteSession Limit(int limit) {
        return limit(limit);
    }

    /**
     * Delete the supplied entity instance (or every entity of a passed {@link Collection}) by their
     * primary keys.
     */
    @API(status = API.Status.STABLE)
    public <T> void delete(T entity) {
        if (entity == null) {
            throw new JormException(ErrorCode.INVALID_ENTITY, "entity must not be null");
        }
        if (entity instanceof Collection) {
            deleteBatch((Collection<?>) entity);
        } else {
            deleteSingle(entity);
        }
        evictOnCommit(entity.getClass());
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> void Delete(T entity) {
        delete(entity);
    }

    /**
     * Conditional delete based on previously registered {@code where(...)} clauses (and optional
     * {@code limit(...)}/{@code offset(...)}).
     */
    @API(status = API.Status.STABLE)
    public <T> void delete(Class<T> cls) {
        checkIfClosed();
        if (cls == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        String sql = null;
        try {
            sql = DeleteBuilder.buildClassDelete(cls, conditions, limit, offset, Jorm.dialect());
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                applyQueryOptions(stmt);
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                int rows = stmt.executeUpdate();
                log.debug("Conditional DELETE affected {} row(s): {}", rows, sql);
            }
        } catch (SQLException e) {
            throw new JormException(ErrorCode.CONDITIONAL_DELETE_FAILED, "SQL=" + sql, e);
        } finally {
            resetState();
        }
        evictOnCommit(cls);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> void Delete(Class<T> cls) {
        delete(cls);
    }

    // ----------------------------------------------------------------
    //  Internals
    // ----------------------------------------------------------------

    private <T> void deleteSingle(T entity) {
        checkIfClosed();
        Class<?> cls = entity.getClass();
        String sql = DeleteBuilder.buildSingleDelete(cls);
        log.debug("Single DELETE: {}", sql);
        try {
            Object id = EntityHelper.getIdValue(entity);
            if (id == null) {
                throw new JormException(ErrorCode.INVALID_ENTITY, "delete target has null id");
            }
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                applyQueryOptions(stmt);
                stmt.setObject(1, id);
                int rows = stmt.executeUpdate();
                log.debug("DELETE affected {} row(s): {}", rows, sql);
            }
        } catch (IllegalAccessException e) {
            throw new JormException(ErrorCode.REFLECTION_ACCESS_FAILED, e);
        } catch (SQLException e) {
            throw new DataIntegrityException("DELETE failed: " + sql, e);
        } finally {
            resetState();
        }
    }

    private <T> void deleteBatch(Collection<T> entities) {
        checkIfClosed();
        if (entities == null || entities.isEmpty()) {
            log.warn("delete batch called with empty collection");
            return;
        }
        // Snapshot — collection must not change underneath us.
        List<T> snapshot = new ArrayList<>(entities);
        Class<?> cls = snapshot.get(0).getClass();
        String sql = DeleteBuilder.buildBatchDelete(cls, snapshot.size());
        log.debug("Batch DELETE (size={}): {}", snapshot.size(), sql);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            applyQueryOptions(stmt);
            for (int i = 0; i < snapshot.size(); i++) {
                Object id = EntityHelper.getIdValue(snapshot.get(i));
                if (id == null) {
                    throw new JormException(
                            ErrorCode.INVALID_ENTITY, "entity at index " + i + " has null id");
                }
                stmt.setObject(i + 1, id);
            }
            int rows = stmt.executeUpdate();
            log.debug("Batch DELETE affected {} row(s): {}", rows, sql);
        } catch (IllegalAccessException e) {
            throw new JormException(ErrorCode.REFLECTION_ACCESS_FAILED, e);
        } catch (SQLException e) {
            throw new JormException(ErrorCode.BATCH_DELETE_FAILED, "SQL=" + sql, e);
        } finally {
            resetState();
        }
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
                        log.debug("L2 cache cleared for {} after DELETE commit", region);
                    } catch (Throwable t) {
                        log.warn("L2 cache eviction failed for {}", region, t);
                    }
                });
    }

    private void resetState() {
        this.conditions.clear();
        this.params.clear();
        this.limit = null;
        this.offset = null;
        resetQueryOptions();
    }

    @Override
    protected DeleteSession self() {
        return this;
    }
}
