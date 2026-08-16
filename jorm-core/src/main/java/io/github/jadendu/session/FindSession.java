// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.dto.Condition;
import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.exception.EmptyResultException;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.exception.NonUniqueResultException;
import io.github.jadendu.query.Page;
import io.github.jadendu.query.Pageable;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.FindBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.transaction.CurrentTransactionConnection;
import io.github.jadendu.util.ResultSetMapper;

/**
 * Chainable query session. Use {@code try-with-resources} around every instance — even in
 * Spring-managed contexts, so the connection state is restored when the session closes.
 *
 * <p>Both PascalCase (2.x deprecated for removal in 3.0) and camelCase (going-forward style)
 * methods are exposed. The two share the same state object — feel free to mix without surprises.
 *
 * <p>Cross-database SQL-flavours are emitted through {@link io.github.jadendu.dialect.Dialect}; the
 * active dialect is held on {@link Jorm#dialect()}.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class FindSession extends BaseSession<FindSession> {

    private static final Logger log = LoggerFactory.getLogger(FindSession.class);

    private final List<Condition> conditions = new ArrayList<>();
    private final List<Condition> havingConditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private String group;
    private String selectClause = "*";
    private String orderBy;
    private Integer limit;
    private Integer offset;

    public FindSession() {
        super();
    }

    public FindSession(java.sql.Connection externalConn) {
        super(externalConn);
    }

    // ---------------- SELECT ----------------

    /** Set the SELECT clause; defaults to {@code "*"}. */
    @API(status = API.Status.STABLE)
    public FindSession select(String selectClause) {
        this.selectClause = selectClause;
        return self();
    }

    /** Deprecated PascalCase alias for {@link #select(String)}. */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Select(String selectClause) {
        return select(selectClause);
    }

    // ---------------- WHERE ----------------

    @API(status = API.Status.STABLE)
    public FindSession where(String column, Object value) {
        conditions.add(new Condition(column, "=", value));
        params.add(value);
        return self();
    }

    @API(status = API.Status.STABLE)
    public FindSession where(String column, String operator, Object value) {
        conditions.add(new Condition(column, operator, value));
        params.add(value);
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Where(String column, Object value) {
        return where(column, value);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Where(String column, String operator, Object value) {
        return where(column, operator, value);
    }

    // ---------------- HAVING ----------------

    @API(status = API.Status.STABLE)
    public FindSession having(String column, String operator, Object value) {
        havingConditions.add(new Condition(column, operator, value));
        params.add(value);
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Having(String column, String operator, Object value) {
        return having(column, operator, value);
    }

    // ---------------- GROUP BY ----------------

    @API(status = API.Status.STABLE)
    public FindSession groupBy(String group) {
        this.group = group;
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Group(String group) {
        return groupBy(group);
    }

    // ---------------- ORDER BY ----------------

    @API(status = API.Status.STABLE)
    public FindSession orderBy(String orderBy) {
        this.orderBy = orderBy;
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Order(String orderBy) {
        return orderBy(orderBy);
    }

    // ---------------- LIMIT / OFFSET / PAGE ----------------

    @API(status = API.Status.STABLE)
    public FindSession limit(Integer limit) {
        this.limit = limit;
        return self();
    }

    @API(status = API.Status.STABLE)
    public FindSession limit(Integer limit, Integer offset) {
        this.limit = limit;
        this.offset = offset;
        return self();
    }

    @API(status = API.Status.STABLE)
    public FindSession offset(Integer offset) {
        this.offset = offset;
        return self();
    }

    /**
     * Apply a {@link Pageable} (page + size). Equivalent to setting {@code limit=size} and {@code
     * offset=page*size}, but more ergonomic and reuses the pagination abstraction downstream
     * components share.
     */
    @API(status = API.Status.STABLE)
    public FindSession page(Pageable pageable) {
        if (pageable == null) {
            return self();
        }
        this.limit = pageable.pageSize();
        this.offset = pageable.offset();
        return self();
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Limit(Integer limit) {
        return limit(limit);
    }

    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Limit(Integer limit, Integer offset) {
        return limit(limit, offset);
    }

    // ---------------- FIND: list ----------------

    /** Execute the query and return the result list. */
    @API(status = API.Status.STABLE)
    public <E> List<E> find(Class<E> clazz) {
        return executeFind(clazz);
    }

    /**
     * Execute the query and return a single row. Throws {@link EmptyResultException} when zero rows
     * returned, {@link NonUniqueResultException} when two or more.
     */
    @API(status = API.Status.STABLE)
    public <E> E findOne(Class<E> clazz) {
        List<E> rows = executeFind(clazz);
        if (rows == null || rows.isEmpty()) {
            throw new EmptyResultException("no row returned for " + clazz.getName());
        }
        if (rows.size() > 1) {
            throw new NonUniqueResultException(
                    rows.size()
                            + " rows returned where exactly one was expected for "
                            + clazz.getName());
        }
        return rows.get(0);
    }

    /**
     * Stream the result, row-by-row, without materialising an intermediate {@code ArrayList}. The
     * returned stream must be either exhausted or closed ({@code .close()}) by the caller — the
     * underlying {@link ResultSet} and {@link PreparedStatement} are released exactly once.
     */
    @API(status = API.Status.STABLE)
    public <E> Stream<E> findStream(Class<E> clazz) {
        checkIfClosed();
        EntityModel model = EntityModelRegistry.get(clazz);
        String cacheKey = generateCacheKey(clazz);
        if (CacheManager.isCacheEnabled() && !inActiveTransaction()) {
            Object cached =
                    CacheManager.getSecondLevelCache().get(model.entityClass().getName(), cacheKey);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                Stream<E> fromCache = ((List<E>) cached).stream();
                return fromCache.onClose(() -> resetAndClean());
            }
        }

        final String sql =
                FindBuilder.buildFindSelect(
                        clazz,
                        conditions,
                        limit,
                        offset,
                        orderBy,
                        group,
                        havingConditions,
                        selectClause,
                        Jorm.dialect());

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement(sql);
            applyQueryOptions(stmt);
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            rs = stmt.executeQuery();
        } catch (SQLException e) {
            closeQuiet(stmt, rs);
            resetAndClean();
            throw new JormException(
                    ErrorCode.QUERY_EXECUTION_FAILED, "SQL=" + sql + ", params=" + params, e);
        }

        final PreparedStatement stmtFinal = stmt;
        final ResultSet rsFinal = rs;
        Stream<E> stream =
                StreamSupport.stream(
                        new Spliterators.AbstractSpliterator<E>(
                                Long.MAX_VALUE,
                                Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL) {
                            @Override
                            public boolean tryAdvance(Consumer<? super E> action) {
                                try {
                                    if (!rsFinal.next()) return false;
                                    E row = row(rsFinal, clazz);
                                    action.accept(row);
                                    return true;
                                } catch (SQLException
                                        | IllegalAccessException
                                        | InstantiationException e) {
                                    throw new JormException(ErrorCode.RESULT_MAPPING_FAILED, e);
                                }
                            }
                        },
                        false);
        return stream.onClose(
                () -> {
                    closeQuiet(stmtFinal, rsFinal);
                    resetAndClean();
                });
    }

    /** Deprecated PascalCase alias for {@link #find(Class)}. */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <E> List<E> Find(Class<E> clazz) {
        return find(clazz);
    }

    /**
     * Execute the query + accompanying COUNT(*) for a {@link Pageable}. Returns a {@link Page} that
     * includes total element count and total pages. Resets state internally so the session can be
     * reused.
     */
    @API(status = API.Status.STABLE)
    public <E> Page<E> findPage(Class<E> clazz, Pageable pageable) {
        if (pageable == null) {
            throw new JormException(ErrorCode.INVALID_QUERY_OPTION, "pageable must not be null");
        }
        page(pageable);
        List<E> content = executeFind(clazz);
        // COUNT(*) query — strip LIMIT/OFFSET and ORDER BY.
        long total = countAll(clazz);
        return new Page<>(content, total, pageable.pageNumber(), pageable.pageSize());
    }

    private <E> List<E> executeFind(Class<E> clazz) {
        checkIfClosed();
        if (clazz == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        EntityModel model = EntityModelRegistry.get(clazz);
        String cacheKey = generateCacheKey(clazz);
        if (CacheManager.isCacheEnabled() && !inActiveTransaction()) {
            Object cached =
                    CacheManager.getSecondLevelCache().get(model.entityClass().getName(), cacheKey);
            if (cached != null) {
                log.debug("L2 hit for {} key={}", clazz.getName(), cacheKey);
                @SuppressWarnings("unchecked")
                List<E> fromCache = (List<E>) cached;
                return fromCache;
            }
        }
        String sql =
                FindBuilder.buildFindSelect(
                        clazz,
                        conditions,
                        limit,
                        offset,
                        orderBy,
                        group,
                        havingConditions,
                        selectClause,
                        Jorm.dialect());
        log.debug("SQL={} params={}", sql, params);
        try {
            return bindAndExecuteQuery(model, sql, clazz);
        } catch (JormException e) {
            resetAndClean();
            throw e;
        }
    }

    private <E> List<E> bindAndExecuteQuery(EntityModel model, String sql, Class<E> clazz) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            applyQueryOptions(stmt);
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<E> result = ResultSetMapper.mapToList(rs, clazz);
                if (CacheManager.isCacheEnabled()
                        && !inActiveTransaction()
                        && result != null
                        && !result.isEmpty()) {
                    CacheManager.getSecondLevelCache()
                            .put(model.entityClass().getName(), generateCacheKey(clazz), result);
                }
                return result;
            }
        } catch (SQLException e) {
            throw new JormException(
                    ErrorCode.QUERY_EXECUTION_FAILED, "SQL=" + sql + ", params=" + params, e);
        } catch (IllegalAccessException | InstantiationException e) {
            throw new JormException(ErrorCode.RESULT_MAPPING_FAILED, e);
        } finally {
            resetAndClean();
        }
    }

    /** Run {@code COUNT(*)} on the current conditions (ignores LIMIT/OFFSET/ORDER BY). */
    private long countAll(Class<?> cls) {
        checkIfClosed();
        if (cls == null) {
            throw new JormException(ErrorCode.MODEL_NOT_SPECIFIED);
        }
        String sql =
                FindBuilder.buildFindSelect(
                        cls,
                        conditions,
                        null,
                        null,
                        null,
                        group,
                        havingConditions,
                        "COUNT(*)",
                        Jorm.dialect());
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new JormException(
                    ErrorCode.QUERY_EXECUTION_FAILED, "count SQL=" + sql + ", params=" + params, e);
        }
    }

    private <E> E row(ResultSet rs, Class<E> cls)
            throws SQLException, IllegalAccessException, InstantiationException {
        // Mirror the public mapper but without consuming the row cursor here.
        // Delegate to ReflectionResultSetMapper's per-row API by constructing an
        // adapter. Cheapest path: re-use mapToEntity with a one-row cursor.
        return ResultSetMapper.mapToEntity(rs, cls);
    }

    /**
     * Active transactions may hold uncommitted state — never read from or populate the L2 cache
     * mid-transaction, otherwise uncommitted rows leak into the shared cache and read-your-own
     * writes go stale.
     */
    private static boolean inActiveTransaction() {
        return CurrentTransactionConnection.hasTransaction()
                || AfterCommitHooks.isSpringTransactionActive();
    }

    private String generateCacheKey(Class<?> cls) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("select:").append(selectClause);
        if (!conditions.isEmpty()) {
            sb.append(":where:");
            for (Condition c : conditions) {
                sb.append(c.getColumn()).append(c.getOperator()).append(c.getValue()).append(';');
            }
        }
        if (group != null) sb.append(":group:").append(group);
        if (!havingConditions.isEmpty()) {
            sb.append(":having:");
            for (Condition c : havingConditions) {
                sb.append(c.getColumn()).append(c.getOperator()).append(c.getValue()).append(';');
            }
        }
        if (orderBy != null) sb.append(":order:").append(orderBy);
        if (limit != null) sb.append(":limit:").append(limit);
        if (offset != null) sb.append(":offset:").append(offset);
        // Invalidate cache across different runtime sessions when classloader differs
        sb.append(":cls:").append(cls.getName());
        return sb.toString();
    }

    private void resetAndClean() {
        this.conditions.clear();
        this.havingConditions.clear();
        this.params.clear();
        this.group = null;
        this.selectClause = "*";
        this.orderBy = null;
        this.limit = null;
        this.offset = null;
        resetQueryOptions();
    }

    private void closeQuiet(PreparedStatement stmt, ResultSet rs) {
        try {
            if (rs != null && !rs.isClosed()) rs.close();
        } catch (SQLException ignored) {
            // best-effort
        }
        try {
            if (stmt != null && !stmt.isClosed()) stmt.close();
        } catch (SQLException ignored) {
            // best-effort
        }
    }

    @Override
    protected FindSession self() {
        return this;
    }
}
