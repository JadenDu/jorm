// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache;

import org.apiguardian.api.API;

import io.github.jadendu.cache.impl.NoOpSecondLevelCache;

/**
 * Process-global singleton controller for the second-level cache. Coordinates consistently across
 * standalone use, Spring, and tests.
 *
 * <p>The actual cache SPI can be a {@link io.github.jadendu.cache.impl.NoOpSecondLevelCache NoOp}
 * (default — disabled), the Spring {@link io.github.jadendu.cache.redis.RedisSecondLevelCache
 * Redis} implementation from the starter, or a user-supplied implementation. Setting a non-trivial
 * cache installs the instrumentation wrapper so hits/misses are tracked in {@link
 * io.github.jadendu.metrics.StatisticsRegistry}.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class CacheManager {

    private static volatile SecondLevelCache secondLevelCache = new NoOpSecondLevelCache();
    private static volatile boolean cacheEnabled = false;

    private CacheManager() {}

    /** Install a {@link SecondLevelCache}; transparently wrapped with metrics instrumentation. */
    @API(status = API.Status.STABLE)
    public static void setSecondLevelCache(SecondLevelCache cache) {
        if (cache == null || cache instanceof NoOpSecondLevelCache) {
            secondLevelCache = cache == null ? new NoOpSecondLevelCache() : cache;
            cacheEnabled = false;
            return;
        }
        if (cache instanceof MeasuringSecondLevelCache) {
            secondLevelCache = cache;
        } else {
            secondLevelCache = new MeasuringSecondLevelCache(cache);
        }
        cacheEnabled = true;
    }

    @API(status = API.Status.STABLE)
    public static SecondLevelCache getSecondLevelCache() {
        return secondLevelCache;
    }

    @API(status = API.Status.STABLE)
    public static boolean isCacheEnabled() {
        return cacheEnabled && secondLevelCache != null;
    }

    /** Disable caching; the cache instance is replaced with {@code NoOp}. */
    @API(status = API.Status.STABLE)
    public static void setCacheEnabled(boolean enabled) {
        cacheEnabled = enabled;
        if (!enabled) {
            secondLevelCache = new NoOpSecondLevelCache();
        }
    }
}
