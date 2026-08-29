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
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.DeleteBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.util.EntityHelper;
import io.github.jadendu.util.SessionHelper;

/**
 * DELETE 会话。支持三种形式:
 *
 * <ul>
 *   <li>{@code Delete(entity)} 和 {@code Delete(List<entity>)} — 按主键删除。
 *   <li>{@code Delete(Class)} — 通过 {@code Where(...)} 进行条件删除。
 * </ul>
 *
 * <p>所有发出的事件都会经过 {@link AfterCommitHooks#register} 处理,因此当 Spring/JORM 事务激活
 * 时,缓存驱逐会等待真正的提交——{@link SaveSession} 和 {@link UpdateSession} 同样提供
 * 这一保证。
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
     * 按主键删除传入的实体实例(或传入的 {@link Collection} 中的所有实体),
     * 二者均依据各自的主键执行删除。
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
     * 基于先前注册的 {@code where(...)} 子句执行条件删除(可配合可选的
     * {@code limit(...)}/{@code offset(...)})。
     */
    @API(status = API.Status.STABLE)
    public <T> void delete(Class<T> cls) {
        checkIfClosed();
        if (cls == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        String sql = null;
        long t0 = System.nanoTime();
        try {
            sql = DeleteBuilder.buildClassDelete(cls, conditions, limit, offset, Jorm.dialect());
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                applyQueryOptions(stmt);
                SessionHelper.bindExpandedParameters(stmt, params);
                int rows = stmt.executeUpdate();
                log.debug("Conditional DELETE affected {} row(s): {}", rows, sql);
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.CONDITIONAL_DELETE_FAILED, "SQL=" + sql, e);
        } finally {
            resetState();
        }
        StatisticsRegistry.query().recordDelete((System.nanoTime() - t0) / 1000L);
        evictOnCommit(cls);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <T> void Delete(Class<T> cls) {
        delete(cls);
    }

    // ----------------------------------------------------------------
    //  内部实现
    // ----------------------------------------------------------------

    private <T> void deleteSingle(T entity) {
        checkIfClosed();
        Class<?> cls = entity.getClass();
        String sql = DeleteBuilder.buildSingleDelete(cls);
        log.debug("Single DELETE: {}", sql);
        long t0 = System.nanoTime();
        try {
            Object id = EntityHelper.getIdValue(entity);
            if (id == null) {
                throw new JormException(ErrorCode.INVALID_ENTITY, "delete target has null id");
            }
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                applyQueryOptions(stmt);
                SessionHelper.bindParameter(stmt, 1, id, id.getClass());
                int rows = stmt.executeUpdate();
                log.debug("DELETE affected {} row(s): {}", rows, sql);
            }
        } catch (IllegalAccessException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.REFLECTION_ACCESS_FAILED, e);
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new DataIntegrityException("DELETE failed: " + sql, e);
        } finally {
            resetState();
        }
        StatisticsRegistry.query().recordDelete((System.nanoTime() - t0) / 1000L);
    }

    private <T> void deleteBatch(Collection<T> entities) {
        checkIfClosed();
        if (entities == null || entities.isEmpty()) {
            log.warn("delete batch called with empty collection");
            return;
        }
        // 快照——集合不得在我们执行期间被外部修改。
        List<T> snapshot = new ArrayList<>(entities);
        Class<?> cls = snapshot.get(0).getClass();
        String sql = DeleteBuilder.buildBatchDelete(cls, snapshot.size());
        log.debug("Batch DELETE (size={}): {}", snapshot.size(), sql);
        long t0 = System.nanoTime();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            applyQueryOptions(stmt);
            for (int i = 0; i < snapshot.size(); i++) {
                Object id = EntityHelper.getIdValue(snapshot.get(i));
                if (id == null) {
                    throw new JormException(
                            ErrorCode.INVALID_ENTITY, "entity at index " + i + " has null id");
                }
                SessionHelper.bindParameter(stmt, i + 1, id, id.getClass());
            }
            int rows = stmt.executeUpdate();
            log.debug("Batch DELETE affected {} row(s): {}", rows, sql);
        } catch (IllegalAccessException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.REFLECTION_ACCESS_FAILED, e);
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.BATCH_DELETE_FAILED, "SQL=" + sql, e);
        } finally {
            resetState();
        }
        StatisticsRegistry.query().recordDelete((System.nanoTime() - t0) / 1000L);
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
