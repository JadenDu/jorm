// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache;

import org.apiguardian.api.API;

import io.github.jadendu.metrics.CacheStatistics;
import io.github.jadendu.metrics.StatisticsRegistry;

/**
 * 缓存装饰器,在共享的 {@link
 * StatisticsRegistry} 中记录命中/未命中/写入/移除计数。由 {@link CacheManager#setSecondLevelCache}
 * 透明地应用,调用方无需修改任何代码。
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

    /** 测量包装器下的原始缓存。 */
    public SecondLevelCache delegate() {
        return delegate;
    }

    @Override
    public void put(String region, String key, Object value) {
        try {
            delegate.put(region, key, value);
            stats.recordPut();
        } catch (RuntimeException e) {
            // 仍然记录一次;写入失败也计入写入尝试次数
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
