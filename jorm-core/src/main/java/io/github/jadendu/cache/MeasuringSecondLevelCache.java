// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache;

import org.apiguardian.api.API;

import io.github.jadendu.metrics.CacheStatistics;
import io.github.jadendu.metrics.StatisticsRegistry;

/**
 * Cache-decorator that records hit/miss/put/eviction counts in the shared {@link
 * StatisticsRegistry}. Applied transparently by {@link CacheManager#setSecondLevelCache} so callers
 * don't need to change a single line.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class MeasuringSecondLevelCache implements SecondLevelCache {

    private final SecondLevelCache delegate;
    private final CacheStatistics stats;

    public MeasuringSecondLevelCache(SecondLevelCache delegate) {
        this(delegate, StatisticsRegistry.cache());
    }

    public MeasuringSecondLevelCache(SecondLevelCache delegate, CacheStatistics stats) {
        this.delegate =
                delegate == null
                        ? new io.github.jadendu.cache.impl.NoOpSecondLevelCache()
                        : delegate;
        this.stats = stats == null ? StatisticsRegistry.cache() : stats;
    }

    /** Original cache under the measurement wrapper. */
    public SecondLevelCache delegate() {
        return delegate;
    }

    @Override
    public void put(String region, String key, Object value) {
        try {
            delegate.put(region, key, value);
            stats.recordPut();
        } catch (RuntimeException e) {
            // still record something; the put failed but we count it as a put attempt
            stats.recordPut();
            throw e;
        }
    }

    @Override
    public Object get(String region, String key) {
        Object val = delegate.get(region, key);
        stats.recordGet(val != null);
        return val;
    }

    @Override
    public void remove(String region, String key) {
        delegate.remove(region, key);
        stats.recordRemove();
    }

    @Override
    public void clearRegion(String region) {
        delegate.clearRegion(region);
        stats.recordRegionClear();
    }

    @Override
    public void clearAll() {
        delegate.clearAll();
        stats.recordAllClear();
    }
}
