// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.autoconfigure;

import javax.sql.DataSource;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.cache.SecondLevelCache;
import io.github.jadendu.cache.impl.NoOpSecondLevelCache;
import io.github.jadendu.cache.redis.RedisCacheProperties;
import io.github.jadendu.cache.redis.RedisSecondLevelCache;
import io.github.jadendu.entity.EntityModelRegistry;
import io.github.jadendu.entity.naming.NamingStrategy;
import io.github.jadendu.metrics.StatisticsRegistry;
import io.github.jadendu.session.factory.Jorm;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Spring Boot auto-configuration for JORM.
 *
 * <p>Installs:
 *
 * <ul>
 *   <li>HikariCP {@link DataSource} with the values from {@link JormProperties}.
 *   <li>{@link TransactionAwareDataSourceProxy} wrapping that datasource so any inner {@code new
 *       SaveSession()} picks up the Spring-managed connection. This is what lets
 *       {@code @Transactional} work with bare Session constructors.
 *   <li>{@link PlatformTransactionManager} mirroring the standard Spring Data {@code
 *       DataSourceTransactionManager}.
 *   <li>Auto-detection of the active {@link io.github.jadendu.dialect.Dialect} from the JDBC URL
 *       (override via {@code jorm.dialect}).
 *   <li>An optional {@link RedisSecondLevelCache} bean when Redis is on the classpath and {@code
 *       jorm.cache.redis.enabled=true}.
 *   <li>A Spring Boot Actuator {@link JormHealthIndicator} (if the actuator is on the classpath)
 *       that exposes query and cache statistics.
 * </ul>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
@Configuration
@EnableConfigurationProperties({JormProperties.class, RedisCacheProperties.class})
public class JormAutoConfiguration implements SmartInitializingSingleton {

    private static final Logger logger = LoggerFactory.getLogger(JormAutoConfiguration.class);

    private final JormProperties properties;
    private final RedisCacheProperties cacheProperties;
    private final ApplicationContext applicationContext;

    @Autowired
    public JormAutoConfiguration(
            JormProperties properties,
            RedisCacheProperties cacheProperties,
            ApplicationContext applicationContext) {
        this.properties = properties;
        this.cacheProperties = cacheProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setIdleTimeout(properties.getIdleTimeout());
        config.setMaxLifetime(properties.getMaxLifetime());
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager transactionManager(
            // 必须绑定"原始" DataSource: TransactionAwareDataSourceProxy 内部以 target
            // DataSource 为事务资源 key, 若绑到代理本身, Session 连接将无法加入 Spring 事务.
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public DataSource jormDataSource(DataSource dataSource) {
        return new TransactionAwareDataSourceProxy(dataSource);
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    @ConditionalOnProperty(prefix = "jorm.cache.redis", name = "enabled", havingValue = "true")
    public SecondLevelCache redisSecondLevelCache(RedisTemplate<String, Object> redisTemplate) {
        return new RedisSecondLevelCache(redisTemplate, cacheProperties);
    }

    @Bean
    @ConditionalOnMissingBean(SecondLevelCache.class)
    public SecondLevelCache noOpSecondLevelCache() {
        return new NoOpSecondLevelCache();
    }

    /**
     * Spring Boot Actuator health indicator. Activated automatically when {@code
     * spring-boot-starter-actuator} is on the classpath.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnMissingBean
    public org.springframework.boot.actuate.health.HealthIndicator jormHealthIndicator() {
        logger.info("Installing JORM Actuator HealthIndicator");
        return new JormHealthIndicator();
    }

    @Override
    public void afterSingletonsInstantiated() {
        DataSource ds = applicationContext.getBean("jormDataSource", DataSource.class);
        Jorm.setDataSource(ds);

        // Configure the framework-wide batch size and dialect based on properties.
        Jorm.setBatchSize(properties.getBatchSize());
        Jorm.setDialect(properties.resolveDialect());

        NamingStrategy strategy = properties.resolveNamingStrategy();
        EntityModelRegistry.setNamingStrategy(strategy);
        logger.info(
                "JORM configured: batch-size={}, dialect={}, naming-strategy={}",
                Jorm.batchSize(),
                Jorm.dialect().name(),
                strategy.getClass().getSimpleName());

        SecondLevelCache cache = applicationContext.getBean(SecondLevelCache.class);
        CacheManager.setSecondLevelCache(cache);
        boolean enabled = cacheProperties.isEnabled() && !(cache instanceof NoOpSecondLevelCache);
        if (!enabled) {
            CacheManager.setCacheEnabled(false);
        }
        logger.info(
                "JORM L2 cache status: {}",
                enabled ? "enabled (" + cache.getClass().getSimpleName() + ")" : "disabled");

        // Register the JORM statistics with the Spring context so users
        // can expose them via Micrometer using a simple MeterBinder.
        logger.info(
                "JORM statistics available: query={}, cache={}",
                StatisticsRegistry.query(),
                StatisticsRegistry.cache());
    }
}
