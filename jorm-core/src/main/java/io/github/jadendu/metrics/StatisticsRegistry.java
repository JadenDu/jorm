// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.metrics;

import org.apiguardian.api.API;

/**
 * Global holder for framework statistics. The singletons are intended to be read by monitoring
 * tools (Spring Boot {@code HealthIndicator}, Micrometer adapter) — never written by user code.
 *
 * <p>Stats values are snapshots at read time and may shift between two reads; treat as best-effort
 * app-level metrics, not transactional data.
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

    /** Reset both registries — usually only called in tests. */
    @API(status = API.Status.EXPERIMENTAL)
    public static void reset() {
        QUERY.reset();
        CACHE.reset();
    }
}
