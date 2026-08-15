// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache.redis;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import io.github.jadendu.cache.SecondLevelCache;

/**
 * Redis-backed L2 cache. Uses {@code SCAN} (cursor-iteration) instead of {@code KEYS} for region
 * and global clears — KEYS is O(N) blocking on the Redis server and is unsafe in production
 * deployments. SCAN walks keyspaces incrementally and is the documented production-grade pattern in
 * the Redis docs.
 *
 * <p>Cache keys use the configured {@code keyPrefix} when {@code useKeyPrefix=true}; otherwise the
 * raw {@code region:key} format applies.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class RedisSecondLevelCache implements SecondLevelCache {

    private static final Logger log = LoggerFactory.getLogger(RedisSecondLevelCache.class);
    private static final int SCAN_BATCH_SIZE = 500;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheProperties properties;

    public RedisSecondLevelCache(
            RedisTemplate<String, Object> redisTemplate, RedisCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void put(String region, String key, Object value) {
        // RedisTemplate cannot serialise a null value cleanly — even when
        // the user explicitly opted in to cacheNullValues via the
        // properties, we still need an actual sentinel representation
        // to avoid NPE-like behaviour in the underlying serializer.
        // For 2.0 we skip nulls entirely (a no-ops cache miss in Spring
        // terms) — a sentinel NullValue cache is tracked for a later
        // point release.
        if (value == null) {
            return;
        }
        String cacheKey = getCacheKey(region, key);
        if (properties.getDefaultExpiration() > 0) {
            redisTemplate
                    .opsForValue()
                    .set(cacheKey, value, properties.getDefaultExpiration(), TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(cacheKey, value);
        }
    }

    @Override
    public Object get(String region, String key) {
        String cacheKey = getCacheKey(region, key);
        return redisTemplate.opsForValue().get(cacheKey);
    }

    @Override
    public void remove(String region, String key) {
        String cacheKey = getCacheKey(region, key);
        redisTemplate.delete(cacheKey);
    }

    @Override
    public void clearRegion(String region) {
        String pattern =
                properties.isUseKeyPrefix()
                        ? properties.getKeyPrefix() + region + ":*"
                        : region + ":*";
        deleteByScan(pattern);
    }

    @Override
    public void clearAll() {
        String pattern = properties.isUseKeyPrefix() ? properties.getKeyPrefix() + "*" : "*";
        deleteByScan(pattern);
    }

    /**
     * Cursor-based deletion: matches {@code pattern} via {@code SCAN} and deletes the resulting
     * keys in batches of {@link #SCAN_BATCH_SIZE}. The cursor is closed in a {@code
     * try-with-resources} so even early client errors don't leak a connection-bound iterator.
     */
    private void deleteByScan(String pattern) {
        ScanOptions options =
                ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
        Set<String> batch = new HashSet<>(SCAN_BATCH_SIZE);
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        } catch (Exception e) {
            log.warn(
                    "SCAN delete failed for pattern {} (Redis server connection issue?)",
                    pattern,
                    e);
        }
    }

    private String getCacheKey(String region, String key) {
        if (properties.isUseKeyPrefix()) {
            return properties.getKeyPrefix() + region + ":" + key;
        }
        return region + ":" + key;
    }
}
