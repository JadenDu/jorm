// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.entity.User;
import io.github.jadendu.session.base.JormSession;
import io.github.jadendu.session.factory.Jorm;

public class MySQLJormTest {
    private DataSource dataSource;
    private Connection connection;
    private DataSource originalDataSource;
    private boolean originalCacheEnabled;

    @BeforeEach
    void setUp() throws SQLException {
        // 保存全局状态, 用例结束后恢复, 避免污染同 JVM 中后续运行的其他用例.
        originalDataSource = Jorm.dataSource();
        originalCacheEnabled = CacheManager.isCacheEnabled();

        // 创建MySQL数据源
        dataSource =
                new DriverManagerDataSource(
                        "jdbc:mysql://localhost:3306/orm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai",
                        "root",
                        "root");

        // 设置Jorm的数据源
        Jorm.setDataSource(dataSource);

        // 获取连接用于后续测试
        connection = dataSource.getConnection();

        // 确保缓存被禁用
        CacheManager.setCacheEnabled(false);

        // 清空测试数据
        try (var statement = connection.createStatement()) {
            statement.execute("DELETE FROM users");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (originalDataSource != null) {
            Jorm.setDataSource(originalDataSource);
        }
        CacheManager.setCacheEnabled(originalCacheEnabled);
    }

    @Test
    void testSaveAndFind() {
        try (var saveSession = Jorm.saveSession(connection)) {
            User user = new User("测试用户", 25, "active");
            saveSession.save(user);
            assertNotNull(user.getId());
        }

        try (var findSession = Jorm.findSession(connection)) {
            List<User> users = findSession.Where("user_name", "测试用户").Find(User.class);
            assertEquals(1, users.size());
            assertEquals("测试用户", users.get(0).getName());
            assertEquals(25, users.get(0).getAge());
        }
    }

    @Test
    void testUpdate() {
        // 先保存一个用户
        try (var saveSession = Jorm.saveSession(connection)) {
            User user = new User("更新前", 20, "active");
            saveSession.save(user);
        }

        // 更新用户
        try (var updateSession = Jorm.updateSession(connection)) {
            updateSession
                    .Model(User.class)
                    .Where("user_name", "更新前")
                    .Set("user_name", "更新后")
                    .Set("age", 30)
                    .Update();
        }

        // 验证更新结果
        try (var findSession = Jorm.findSession(connection)) {
            List<User> users = findSession.Where("user_name", "更新后").Find(User.class);
            assertEquals(1, users.size());
            assertEquals(30, users.get(0).getAge());
        }
    }

    @Test
    void testDelete() {
        // 先保存一个用户
        try (var saveSession = Jorm.saveSession(connection)) {
            User user = new User("待删除用户", 25, "active");
            saveSession.save(user);
        }

        // 删除用户
        try (var deleteSession = Jorm.deleteSession(connection)) {
            deleteSession.Where("user_name", "待删除用户").Delete(User.class);
        }

        // 验证删除结果
        try (var findSession = Jorm.findSession(connection)) {
            List<User> users = findSession.Where("user_name", "待删除用户").Find(User.class);
            assertTrue(users.isEmpty());
        }
    }

    @Test
    void testJormSession() {
        try (JormSession session = new JormSession(connection)) {
            // 保存用户
            User user = new User("JormSession测试", 35, "active");
            session.saveSession().save(user);
            assertNotNull(user.getId());

            // 查询用户
            List<User> users =
                    session.findSession().Where("user_name", "JormSession测试").Find(User.class);
            assertEquals(1, users.size());

            // 更新用户
            session.updateSession()
                    .Model(User.class)
                    .Where("user_name", "JormSession测试")
                    .Set("age", 40)
                    .Update();

            // 验证更新
            users = session.findSession().Where("user_name", "JormSession测试").Find(User.class);
            assertEquals(40, users.get(0).getAge());

            // 删除用户
            session.deleteSession().Where("user_name", "JormSession测试").Delete(User.class);

            // 验证删除
            users = session.findSession().Where("user_name", "JormSession测试").Find(User.class);
            assertTrue(users.isEmpty());
        }
    }

    @Test
    void testAggregationField() {
        try (var saveSession = Jorm.saveSession(connection)) {
            User user1 = new User("用户1", 20, "active");
            User user2 = new User("用户2", 30, "active");
            saveSession.save(user1);
            saveSession.save(user2);
        }

        // 测试聚合查询（totalAge字段有@Aggregation注解）
        try (var findSession = Jorm.findSession(connection)) {
            List<User> users =
                    findSession
                            .Select("SUM(age) as totalAge")
                            .Group("status")
                            .Having("status", "=", "active")
                            .Find(User.class);

            assertFalse(users.isEmpty());
            User result = users.get(0);
            assertEquals(50, result.getTotalAge()); // 20 + 30 = 50
        }
    }
}
