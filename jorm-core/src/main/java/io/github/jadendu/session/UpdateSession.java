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
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.sqlBuilder.UpdateBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;

/**
 * UPDATE session. Conditions and SET-tabled column names are validated by the shared {@link
 * io.github.jadendu.util.SqlValidator}, so passing a non-existent column yields a typed exception
 * rather than an SQL injection vector.
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

    /** Specify which entity type to UPDATE. */
    @API(status = API.Status.STABLE)
    public UpdateSession model(Class<?> entityClass) {
        this.entityClass = entityClass;
        return self();
    }

    /** Deprecated alias for {@link #model}. */
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

    /** Execute the UPDATE statement. */
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
            // Refuse unrestricted UPDATE by default to prevent accidental
            // full-table writes; require the caller to opt in via Where.
            throw new JormException(ErrorCode.CONDITION_NOT_SPECIFIED);
        }

        String sql = null;
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
                for (Object value : updates.values()) {
                    stmt.setObject(parameterIndex++, value);
                }
                for (Condition cond : conditions) {
                    stmt.setObject(parameterIndex++, cond.getValue());
                }
                int affected = stmt.executeUpdate();
                log.debug("UPDATE affected {} row(s): {}", affected, sql);
            }
        } catch (SQLException e) {
            throw new JormException(ErrorCode.UPDATE_EXECUTION_FAILED, "SQL=" + sql, e);
        } finally {
            conditions.clear();
            updates.clear();
            resetQueryOptions();
        }
        evictOnCommit(entityClass);
    }

    /** Deprecated alias for {@link #update}. */
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
