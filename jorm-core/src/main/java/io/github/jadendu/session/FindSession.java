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
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.query.Page;
import io.github.jadendu.query.Pageable;
import io.github.jadendu.session.base.BaseSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.sqlBuilder.FindBuilder;
import io.github.jadendu.transaction.AfterCommitHooks;
import io.github.jadendu.transaction.CurrentTransactionConnection;
import io.github.jadendu.util.ResultSetMapper;
import io.github.jadendu.util.SessionHelper;

/**
 * 可链式调用的查询会话。每个实例都应使用 {@code try-with-resources} 包裹——即使在 Spring
 * 管理的上下文中也应如此,这样会话关闭时连接状态才能被正确恢复。
 *
 * <p>同时提供 PascalCase(2.x 中已弃用,将于 3.0 移除)与 camelCase(未来推荐风格)
 * 两种方法。两者共享同一个状态对象——可放心混用而不会出现意外。
 *
 * <p>跨数据库的 SQL 方言通过 {@link io.github.jadendu.dialect.Dialect} 生成;当前激活的
 * 方言保存在 {@link Jorm#dialect()} 中。
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

    // ---------------- SELECT 子句 ----------------

    /** 设置 SELECT 子句;默认为 {@code "*"}。 */
    @API(status = API.Status.STABLE)
    public FindSession select(String selectClause) {
        this.selectClause = selectClause;
        return self();
    }

    /** {@link #select(String)} 的已弃用 PascalCase 别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public FindSession Select(String selectClause) {
        return select(selectClause);
    }

    // ---------------- WHERE 条件 ----------------

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

    // ---------------- HAVING 条件 ----------------

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

    // ---------------- GROUP BY 分组 ----------------

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

    // ---------------- ORDER BY 排序 ----------------

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

    // ---------------- LIMIT / OFFSET / PAGE 分页 ----------------

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
     * 应用一个 {@link Pageable}(页码 + 每页大小)。等价于设置 {@code limit=size} 与 {@code
     * offset=page*size},但更符合人体工学,且复用了下游组件共享的分页抽象。
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

    // ---------------- FIND: 列表查询 ----------------

    /** 执行查询并返回结果列表。 */
    @API(status = API.Status.STABLE)
    public <E> List<E> find(Class<E> clazz) {
        return executeFind(clazz);
    }

    /**
     * 执行查询并返回单行结果。当返回零行时抛出 {@link EmptyResultException},
     * 返回两行及以上时抛出 {@link NonUniqueResultException}。
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
     * 逐行流式返回结果,而不物化中间 {@code ArrayList}。返回的流必须由调用方耗尽或
     * 关闭({@code .close()})——底层的 {@link ResultSet} 和 {@link PreparedStatement}
     * 恰好被释放一次。
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
            SessionHelper.bindExpandedParameters(stmt, params);
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

    /** {@link #find(Class)} 的已弃用 PascalCase 别名。 */
    @Deprecated
    @API(status = API.Status.DEPRECATED)
    public <E> List<E> Find(Class<E> clazz) {
        return find(clazz);
    }

    /**
     * 为 {@link Pageable} 执行查询及配套的 COUNT(*) 统计。返回的 {@link Page} 中包含
     * 总元素数与总页数。内部会重置状态,因此会话可以被复用。
     */
    @API(status = API.Status.STABLE)
    public <E> Page<E> findPage(Class<E> clazz, Pageable pageable) {
        if (pageable == null) {
            throw new JormException(ErrorCode.INVALID_QUERY_OPTION, "pageable must not be null");
        }
        page(pageable);
        List<E> content = executeFind(clazz);
        // COUNT(*) 查询——去除 LIMIT/OFFSET 与 ORDER BY。
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
        long t0 = System.nanoTime();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            applyQueryOptions(stmt);
            SessionHelper.bindExpandedParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<E> result = ResultSetMapper.mapToList(rs, clazz);
                if (CacheManager.isCacheEnabled()
                        && !inActiveTransaction()
                        && result != null
                        && !result.isEmpty()) {
                    CacheManager.getSecondLevelCache()
                            .put(model.entityClass().getName(), generateCacheKey(clazz), result);
                }
                StatisticsRegistry.query().recordSelect((System.nanoTime() - t0) / 1000L);
                return result;
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(
                    ErrorCode.QUERY_EXECUTION_FAILED, "SQL=" + sql + ", params=" + params, e);
        } catch (IllegalAccessException | InstantiationException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(ErrorCode.RESULT_MAPPING_FAILED, e);
        } finally {
            resetAndClean();
        }
    }

    /** 基于当前条件执行 {@code COUNT(*)} 查询(忽略 LIMIT/OFFSET/ORDER BY)。 */
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
        long t0 = System.nanoTime();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            SessionHelper.bindExpandedParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long cnt = rs.getLong(1);
                    StatisticsRegistry.query().recordCount((System.nanoTime() - t0) / 1000L);
                    return cnt;
                }
                StatisticsRegistry.query().recordCount((System.nanoTime() - t0) / 1000L);
                return 0L;
            }
        } catch (SQLException e) {
            StatisticsRegistry.query().recordError((System.nanoTime() - t0) / 1000L);
            throw new JormException(
                    ErrorCode.QUERY_EXECUTION_FAILED, "count SQL=" + sql + ", params=" + params, e);
        }
    }

    private <E> E row(ResultSet rs, Class<E> cls)
            throws SQLException, IllegalAccessException, InstantiationException {
        // 镜像公共映射器,但此处不消费行游标。
        // 通过构造适配器,委托给 ReflectionResultSetMapper 的按行 API。
        // 最廉价的方式:使用单行游标复用 mapToEntity。
        return ResultSetMapper.mapToEntity(rs, cls);
    }

    /**
     * 进行中的事务可能持有未提交的状态——切勿在事务中途读取或填充二级缓存,否则未提交的
     * 行会泄漏到共享缓存中,且"读自己写入"的数据会变得过时。
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
        // 当类加载器不同时,跨不同运行时会话使缓存失效
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
            // 尽力而为
        }
        try {
            if (stmt != null && !stmt.isClosed()) stmt.close();
        } catch (SQLException ignored) {
            // 尽力而为
        }
    }

    @Override
    protected FindSession self() {
        return this;
    }
}
