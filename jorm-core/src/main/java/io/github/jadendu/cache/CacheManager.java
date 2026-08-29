// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache;

import org.apiguardian.api.API;

import io.github.jadendu.cache.impl.NoOpSecondLevelCache;

/**
 * 二级缓存的进程级全局单例控制器,在独立使用、Spring 和测试场景间保持一致协调。
 *
 * <p>实际的缓存 SPI 可以是 {@link io.github.jadendu.cache.impl.NoOpSecondLevelCache NoOp}
 * (默认 —— 禁用)、starter 提供的 Spring {@link io.github.jadendu.cache.redis.RedisSecondLevelCache
 * Redis} 实现,或由用户提供的实现。设置非平凡(非 NoOp)的缓存时,
 * 会安装埋点包装器,以便在 {@link
 * io.github.jadendu.metrics.StatisticsRegistry} 中记录命中/未命中。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class CacheManager {

    private static volatile SecondLevelCache secondLevelCache = new NoOpSecondLevelCache();
    private static volatile boolean cacheEnabled = false;

    private CacheManager() {}

    /** 安装一个 {@link SecondLevelCache};会被透明地包裹上指标埋点。 */
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

    /** 禁用缓存;缓存实例将被替换为 {@code NoOp}。 */
    @API(status = API.Status.STABLE)
    public static void setCacheEnabled(boolean enabled) {
        cacheEnabled = enabled;
        if (!enabled) {
            secondLevelCache = new NoOpSecondLevelCache();
        }
    }
}
