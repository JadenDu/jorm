// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.metrics;

import org.apiguardian.api.API;

/**
 * 框架统计信息的全局持有者。这些单例供监控工具
 * (Spring Boot {@code HealthIndicator}、Micrometer 适配器)读取 —— 禁止用户代码写入。
 *
 * <p>统计值只是读取时刻的快照,两次读取之间可能发生变化;请将其视为尽力而为的
 * 应用级指标,而非事务性数据。
 *
 * @author JadenDu
 */
@API(status = API.Status.EXPERIMENTAL)
public final class StatisticsRegistry {

    private static final QueryStatistics QUERY = new QueryStatistics();
    private static final CacheStatistics CACHE = new CacheStatistics();

    private StatisticsRegistry() {}

    @API(status = API.Status.STABLE)
    public static QueryStatistics query() {
        return QUERY;
    }

    @API(status = API.Status.STABLE)
    public static CacheStatistics cache() {
        return CACHE;
    }

    /** 重置两个注册表 —— 通常仅在测试中调用。 */
    @API(status = API.Status.EXPERIMENTAL)
    public static void reset() {
        QUERY.reset();
        CACHE.reset();
    }
}
