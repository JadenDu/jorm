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
 * 基于 Redis 的二级缓存。清空区域或全量缓存时使用 {@code SCAN}(游标迭代)而非
 * {@code KEYS} — KEYS 在 Redis 服务端是 O(N) 阻塞操作,生产环境使用不安全。
 * SCAN 增量遍历键空间,是 Redis 官方文档推荐的生产级模式。
 *
 * <p>当 {@code useKeyPrefix=true} 时,缓存键使用配置的 {@code keyPrefix} 前缀;否则
 * 使用原始的 {@code region:key} 格式。
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
        // RedisTemplate 无法干净地序列化 null 值 — 即使通过配置显式启用了
        // cacheNullValues,底层序列化器仍需要一个实际的哨兵表示来避免
        // 类似 NPE 的行为。
        // 2.0 版本直接跳过 null(等价于 Spring 语境下的缓存未命中),
        // 哨兵 NullValue 缓存留待后续小版本实现。
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
     * 基于游标的删除: 通过 {@code SCAN} 匹配 {@code pattern},并按 {@link #SCAN_BATCH_SIZE}
     * 批量删除命中的键。游标在 {@code try-with-resources} 中关闭,因此即使客户端提前
     * 出错,也不会泄漏绑定连接的迭代器。
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
