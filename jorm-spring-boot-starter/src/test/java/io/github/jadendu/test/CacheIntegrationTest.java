// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jadendu.entity.User;
import io.github.jadendu.session.FindSession;
import io.github.jadendu.test.service.TransactionalService;

public class CacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @Autowired private TransactionalService transactionalService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().flushAll();
    }

    @AfterEach
    void cleanTable() {
        // 非事务用例的数据会真实提交, 清理避免影响其他用例和重复运行.
        // Hikari 默认 autoCommit=false, DELETE 必须显式走事务提交才生效.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                status ->
                        jdbcTemplate.update(
                                "DELETE FROM users WHERE user_name IN ('CachedUser', 'ToUpdateUser')"));
    }

    @Test
    void testQueryResultCached() {
        User user = new User("CachedUser", 40, "active");
        transactionalService.saveUser(user);

        FindSession session1 = new FindSession();
        List<User> result1 = session1.Where("user_name", "CachedUser").Find(User.class);
        assertEquals(1, result1.size());

        FindSession session2 = new FindSession();
        List<User> result2 = session2.Where("user_name", "CachedUser").Find(User.class);
        assertEquals(1, result2.size());

        assertFalse(Objects.requireNonNull(redisTemplate.keys("*")).isEmpty());
    }

    @Test
    @Transactional
    void testCacheEvictionOnUpdate() {
        User user = new User("ToUpdateUser", 50, "active");
        transactionalService.saveUser(user);

        FindSession findSession = new FindSession();
        findSession.Where("user_name", "ToUpdateUser").Find(User.class);

        transactionalService.updateUserAge(user.getId(), 60);

        List<User> result = findSession.Where("user_name", "ToUpdateUser").Find(User.class);
        assertEquals(60, result.get(0).getAge());
    }
}
