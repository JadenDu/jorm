// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.autoconfigure;

import java.sql.Connection;

import javax.sql.DataSource;

import org.apiguardian.api.API;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.session.factory.Jorm;

/**
 * Spring Boot Actuator 健康指示器,暴露 JORM 生命周期与统计信息:
 *
 * <ul>
 *   <li>{@code configured} — 当 {@link DataSource} 已绑定到 {@link Jorm} 时为 true;
 *   <li>{@code dialect} — 生效的 {@link io.github.jadendu.dialect.Dialect} 名称;
 *   <li>{@code batchSize} — 配置的多行 INSERT 批量大小;
 *   <li>{@code cache.enabled} — 当二级缓存启用时为 true;
 *   <li>{@code statistics.query} 与 {@code statistics.cache} — 来自 {@link
 *       StatisticsRegistry} 的计数器快照。
 * </ul>
 *
 * <p>仅当 actuator 位于 classpath 上时才会创建该 Bean(参见 {@link
 * JormAutoConfiguration})。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class JormHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        try {
            builder.withDetail("configured", Jorm.isConfigured());
            builder.withDetail("dialect", Jorm.dialect() != null ? Jorm.dialect().name() : "unset");
            builder.withDetail("batchSize", Jorm.batchSize());
            builder.withDetail("cache.enabled", CacheManager.isCacheEnabled());
            builder.withDetail(
                    "statistics.query",
                    io.github.jadendu.metrics.StatisticsRegistry.query().toString());
            builder.withDetail(
                    "statistics.cache",
                    io.github.jadendu.metrics.StatisticsRegistry.cache().toString());
        } catch (Throwable t) {
            builder =
                    Health.down()
                            .withDetail("error", t.getClass().getName() + ": " + t.getMessage());
        }
        return builder.build();
    }

    /** 尽力探测 {@link DataSource} 是否可用;不可用时返回 false。 */
    public static boolean probeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }
}
