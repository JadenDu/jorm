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
 * JORM 的 Spring Boot 自动配置。
 *
 * <p>安装以下组件:
 *
 * <ul>
 *   <li>基于 {@link JormProperties} 配置值的 HikariCP {@link DataSource}。
 *   <li>{@link TransactionAwareDataSourceProxy} 包装该数据源,使内部任意 {@code new
 *       SaveSession()} 都能获取 Spring 管理的连接,从而让裸 Session 构造器也能配合
 *       {@code @Transactional} 正常工作。
 *   <li>与标准 Spring Data {@code DataSourceTransactionManager} 对齐的 {@link
 *       PlatformTransactionManager}。
 *   <li>根据 JDBC URL 自动探测生效的 {@link io.github.jadendu.dialect.Dialect}(可通过
 *       {@code jorm.dialect} 覆盖)。
 *   <li>当 Redis 位于 classpath 且 {@code jorm.cache.redis.enabled=true} 时,提供可选的
 *       {@link RedisSecondLevelCache} Bean。
 *   <li>Spring Boot Actuator 的 {@link JormHealthIndicator}(当 actuator 位于 classpath 时),
 *       用于暴露查询与缓存统计信息。
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
     * Spring Boot Actuator 健康指示器。当 {@code spring-boot-starter-actuator} 位于 classpath
     * 上时自动激活。
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

        // 根据配置属性设置框架级的批量大小与方言。
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

        // 将 JORM 统计信息注册到 Spring 上下文,使用户可以通过一个简单的
        // MeterBinder 借助 Micrometer 暴露这些指标。
        logger.info(
                "JORM statistics available: query={}, cache={}",
                StatisticsRegistry.query(),
                StatisticsRegistry.cache());
    }
}
