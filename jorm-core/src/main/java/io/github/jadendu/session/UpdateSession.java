// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.cache.SecondLevelCache;
import io.github.jadendu.dto.Condition;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.sqlBuilder.UpdateBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.util.SessionHelper;

/**
 * UPDATE 会话。条件与 SET 子句中的列名由共享的 {@link
 * io.github.jadendu.util.SqlValidator} 校验,因此传入不存在的列会得到类型化异常,
 * 而不是留下 SQL 注入的隐患。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class UpdateSession extends BaseSession<UpdateSession> {

    private static final Logger log = LoggerFactory.getLogger(UpdateSession.class);

    private final List<Condition> conditions = new ArrayList<>();
    private Class<?> entityClass;
    private final Map<String, Object> updates = new LinkedHashMap<>();

    public UpdateSession() {
        super();
    }

    public UpdateSession(Connection externalConn) {
        super(externalConn);
    }

    /** 指定要 UPDATE 的实体类型。 */
    @API(status = API.Status.STABLE)
    public UpdateSession model(Class<?> entityClass) {
        this.entityClass = entityClass;
        return self();
    }

    /** {@link #model} 的已弃用别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public UpdateSession Model(Class<?> entityClass) {
        return model(entityClass);
    }

    @API(status = API.Status.STABLE)
    public UpdateSession where(String column, Object value) {
        conditions.add(new Condition(column, "=", value));
        return self();
    }

    @API(status = API.Status.STABLE)
    public UpdateSession where(String column, String operator, Object value) {
        conditions.add(new Condition(column, operator, value));
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public UpdateSession Where(String column, Object value) {
        return where(column, value);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public UpdateSession Where(String column, String operator, Object value) {
        return where(column, operator, value);
    }

    @API(status = API.Status.STABLE)
    public UpdateSession set(String column, Object value) {
        updates.put(column, value);
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public UpdateSession Set(String column, Object value) {
        return set(column, value);
    }

    /** 执行 UPDATE 语句。 */
    @API(status = API.Status.STABLE)
    public void update() {
        checkIfClosed();
        if (entityClass == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED, "Update target class not set");
        }
        if (updates.isEmpty()) {
            throw new JormException(ErrorCode.UPDATE_FIELD_EMPTY);
        }
        if (conditions.isEmpty()) {
            // 默认拒绝无条件的 UPDATE,以防止意外的
            // 全表写入;要求调用方通过 Where 显式选择。
            throw new JormException(ErrorCode.CONDITION_NOT_SPECIFIED);
        }

        String sql = null;
        long t0 = System.nanoTime();
        try {
            sql = UpdateBuilder.buildUpdateSql(entityClass, conditions, updates);
            log.debug(
                    "UPDATE sql={} | SET params={} | WHERE params={}",
                    sql,
                    updates.values(),
                    conditions.stream().map(Condition::getValue).collect(Collectors.toList()));
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                applyQueryOptions(stmt);
                int parameterIndex = 1;
                // SET 子句:每个被更新的列一个占位符,不做 IN 展开。
                for (Object value : updates.values()) {
                    SessionHelper.bindParameter(stmt, parameterIndex, value, value == null ? null : value.getClass());
                    parameterIndex++;
                }
                // WHERE 子句:将 IN 集合展开为多个占位符。
                List<Object> whereParams =
                        conditions.stream().map(Condition::getValue).collect(Collectors.toList());
                parameterIndex = SessionHelper.bindExpandedParametersFrom(stmt, whereParams, parameterIndex);
                int affected = stmt.executeUpdate();
                log.debug("UPDATE affected {} row(s): {}", affected, sql);
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.UPDATE_EXECUTION_FAILED, "SQL=" + sql, e);
        } finally {
            conditions.clear();
            updates.clear();
            resetQueryOptions();
        }
        StatisticsRegistry.query().recordUpdate((System.nanoTime() - t0) / 1000L);
        evictOnCommit(entityClass);
    }

    /** {@link #update} 的已弃用别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public void Update() {
        update();
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
                        log.debug("L2 cache cleared for {} after UPDATE commit", region);
                    } catch (Throwable t) {
                        log.warn("L2 cache eviction failed for {}", region, t);
                    }
                });
    }

    @Override
    protected UpdateSession self() {
        return this;
    }
}
