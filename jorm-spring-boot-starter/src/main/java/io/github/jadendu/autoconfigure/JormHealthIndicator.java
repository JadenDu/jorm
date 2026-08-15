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
 * Spring Boot Actuator health indicator exposing JORM lifecycle and statistics:
 *
 * <ul>
 *   <li>{@code configured} — true when a {@link DataSource} is bound to {@link Jorm};
 *   <li>{@code dialect} — the active {@link io.github.jadendu.dialect.Dialect}'s name;
 *   <li>{@code batchSize} — the configured multi-row INSERT batch;
 *   <li>{@code cache.enabled} — true when L2 cache is active;
 *   <li>{@code statistics.query} and {@code statistics.cache} — the counter snapshots from {@link
 *       StatisticsRegistry}.
 * </ul>
 *
 * <p>The bean is only created when the actuator is on the classpath (see {@link
 * JormAutoConfiguration}).
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

    /** Best-effort probe of an active {@link DataSource}; returns false if unavailable. */
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
