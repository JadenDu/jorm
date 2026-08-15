// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.github.jadendu.cache.CacheManager;
import io.github.jadendu.entity.User;
import io.github.jadendu.session.DeleteSession;
import io.github.jadendu.session.FindSession;
import io.github.jadendu.session.SaveSession;
import io.github.jadendu.session.UpdateSession;
import io.github.jadendu.session.base.JormSession;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.transaction.TransactionManager;
import io.github.jadendu.transaction.TransactionTemplate;

public class H2JormTest {
    private String dbName;
    private String jdbcUrl;
    private TransactionTemplate transactionTemplate;
    private static final Logger log = LoggerFactory.getLogger(SaveSession.class);

    @BeforeEach
    void setUp() throws SQLException {
        // 生成唯一的数据库名
        dbName = "test_" + UUID.randomUUID().toString().replace("-", "");
        // 构建 JDBC URL
        jdbcUrl =
                String.format(
                        "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false",
                        dbName);

        // 初始化事务模板
        transactionTemplate = new TransactionTemplate();
        // 设置数据源给Jorm工厂
        Jorm.setDataSource(new SimpleDataSource(jdbcUrl));

        // 执行 SQL 脚本初始化表结构
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("schema.sql"));
        }

        // 启用缓存测试
        CacheManager.setCacheEnabled(true);
    }

    @AfterEach
    void tearDown() {
        CacheManager.setCacheEnabled(false);
    }

    // 测试自动事务
    @Test
    void testAutoTransactionCRUD() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // 测试保存
            User user = new User("自动事务测试", 25, "active");
            try (SaveSession session = Jorm.saveSession(connection)) {
                session.save(user);
                assertNotNull(user.getId());
            }

            // 测试查询
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> users = session.Where("user_name", "自动事务测试").Find(User.class);
                assertEquals(1, users.size());
                assertEquals("自动事务测试", users.get(0).getName());
            }

            // 测试更新
            try (UpdateSession session = Jorm.updateSession(connection)) {
                session.Model(User.class).Where("user_name", "自动事务测试").Set("age", 30).Update();
            }

            // 验证更新
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> users = session.Where("user_name", "自动事务测试").Find(User.class);
                assertEquals(30, users.get(0).getAge());
            }

            // 测试删除
            try (DeleteSession session = Jorm.deleteSession(connection)) {
                session.Where("user_name", "自动事务测试").Delete(User.class);
            }

            // 验证删除
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> users = session.Where("user_name", "自动事务测试").Find(User.class);
                assertTrue(users.isEmpty());
            }
        }
    }

    // 测试手动事务
    @Test
    void testManualTransaction() throws SQLException {
        // 使用事务管理器开始事务
        try (Connection transactionConn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            TransactionManager.begin();

            try (JormSession session = new JormSession(transactionConn)) {
                // 保存操作
                User user1 = new User("手动事务用户1", 25, "active");
                User user2 = new User("手动事务用户2", 30, "inactive");
                session.saveSession().save(user1);
                session.saveSession().save(user2);

                // 查询验证
                List<User> users = session.findSession().Where("age", ">", 20).Find(User.class);
                assertEquals(2, users.size());

                // 提交事务
                TransactionManager.commit();
            } catch (Exception e) {
                TransactionManager.rollback();
                fail("手动事务测试失败: " + e.getMessage());
            } finally {
                TransactionManager.release();
            }
        }

        // 使用新的连接验证事务提交后的数据
        try (Connection verificationConn = DriverManager.getConnection(jdbcUrl, "sa", "");
                FindSession session = Jorm.findSession(verificationConn)) {
            List<User> users = session.Find(User.class);
            assertEquals(2, users.size());
        }
    }

    // 测试闭包事务
    @Test
    void testClosureTransaction() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            transactionTemplate.execute(
                    () -> {
                        try (JormSession session = new JormSession(connection)) {
                            // 在事务中执行多个操作
                            User user = new User("闭包事务用户", 35, "active");
                            session.saveSession().save(user);

                            // 更新操作
                            session.updateSession()
                                    .Model(User.class)
                                    .Where("user_name", "闭包事务用户")
                                    .Set("status", "inactive")
                                    .Update();

                            return null;
                        }
                    });

            // 验证事务执行结果
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> users = session.Where("user_name", "闭包事务用户").Find(User.class);
                assertEquals(1, users.size());
                assertEquals("inactive", users.get(0).getStatus());
            }
        }
    }

    // 测试事务回滚
    @Test
    void testTransactionRollback() throws SQLException {
        // 先确保数据库中没有测试数据
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                FindSession session = Jorm.findSession(connection)) {
            List<User> existingUsers = session.Where("user_name", "回滚测试用户1").Find(User.class);
            assertTrue(existingUsers.isEmpty());
        }

        // 开始事务
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // 设置手动事务模式
            connection.setAutoCommit(false);

            try (JormSession session = new JormSession(connection)) {
                // 保存第一个用户
                User user1 = new User("回滚测试用户1", 25, "active");
                session.saveSession().save(user1);

                // 验证数据已保存（在事务内）
                List<User> usersInTx =
                        session.findSession().Where("user_name", "回滚测试用户1").Find(User.class);
                assertEquals(1, usersInTx.size());

                // 故意抛出异常触发回滚
                throw new RuntimeException("测试回滚");
            } catch (Exception e) {
                // 回滚事务
                connection.rollback();
            }
        }

        // 使用新的连接验证数据已回滚
        try (Connection verificationConn = DriverManager.getConnection(jdbcUrl, "sa", "");
                FindSession session = Jorm.findSession(verificationConn)) {
            List<User> users = session.Where("user_name", "回滚测试用户1").Find(User.class);
            assertTrue(users.isEmpty(), "数据应该已回滚，但找到了: " + users.size() + " 条记录");
        }
    }

    // 测试链式方法
    @Test
    void testChainedMethods() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // 准备测试数据
            try (SaveSession session = Jorm.saveSession(connection)) {
                Arrays.asList(
                                new User("用户A", 20, "active"),
                                new User("用户B", 30, "inactive"),
                                new User("用户C", 40, "active"),
                                new User("用户D", 50, "inactive"))
                        .forEach(session::save);
            }

            // 测试复杂的链式查询
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> users =
                        session.Select("id, user_name, age, status")
                                .Where("age", ">", 25)
                                .Where("status", "=", "active")
                                .Order("age DESC")
                                .Limit(2)
                                .Find(User.class);

                assertEquals(1, users.size()); // 只有用户C符合条件
                assertEquals("用户C", users.get(0).getName());
            }

            // 测试分组和聚合
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> result =
                        session.Select("COUNT(*) as totalAge")
                                .Select("status") // totalAge字段有@Aggregation注解
                                .Group("status")
                                .Find(User.class);

                assertEquals(2, result.size()); // 两个状态分组
            }
        }
    }

    // 测试批量操作
    @Test
    void testBatchOperations() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // 测试批量保存
            List<User> users =
                    Arrays.asList(
                            new User("批量用户1", 21, "active"),
                            new User("批量用户2", 22, "inactive"),
                            new User("批量用户3", 23, "active"));

            try (SaveSession session = Jorm.saveSession(connection)) {
                List<Long> ids = session.batchSave(users);
                assertEquals(3, ids.size());
                log.info("批量保存的用户ID: " + ids);
                assertNotNull(ids.get(0));

                // 将生成的ID设置回实体对象
                for (int i = 0; i < users.size(); i++) {
                    users.get(i).setId(ids.get(i));
                }
            }

            // 测试批量删除
            try (DeleteSession session = Jorm.deleteSession(connection)) {
                session.Delete(users); // 批量删除实体集合
            }

            // 验证批量删除结果
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> remainingUsers =
                        session.Where("user_name", "LIKE", "批量用户%").Find(User.class);
                assertTrue(remainingUsers.isEmpty());
            }
        }
    }

    // 测试缓存功能
    @Test
    void testCacheFunctionality() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // 第一次查询（会放入缓存）
            List<User> firstResult;
            try (FindSession session = Jorm.findSession(connection)) {
                firstResult = session.Where("status", "active").Find(User.class);
            }

            // 第二次查询（应该从缓存获取）
            List<User> secondResult;
            try (FindSession session = Jorm.findSession(connection)) {
                secondResult = session.Where("status", "active").Find(User.class);
            }

            assertEquals(firstResult.size(), secondResult.size());

            // 更新操作应该清除缓存
            try (UpdateSession session = Jorm.updateSession(connection)) {
                session.Model(User.class)
                        .Where("status", "active")
                        .Set("status", "updated")
                        .Update();
            }

            // 再次查询应该重新从数据库获取
            try (FindSession session = Jorm.findSession(connection)) {
                List<User> updatedResult = session.Where("status", "active").Find(User.class);
                assertTrue(updatedResult.isEmpty()); // 所有active状态都被更新了
            }
        }
    }

    // 测试JormSession统一接口
    @Test
    void testJormSessionIntegration() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                JormSession session = new JormSession(connection)) {
            // 保存
            User user = new User("统一接口测试", 28, "active");
            session.saveSession().save(user);

            // 查询
            List<User> users = session.findSession().Where("user_name", "统一接口测试").Find(User.class);
            assertEquals(1, users.size());

            // 更新
            session.updateSession()
                    .Model(User.class)
                    .Where("user_name", "统一接口测试")
                    .Set("age", 35)
                    .Update();

            // 验证更新
            users = session.findSession().Where("user_name", "统一接口测试").Find(User.class);
            assertEquals(35, users.get(0).getAge());

            // 删除
            session.deleteSession().Where("user_name", "统一接口测试").Delete(User.class);

            // 验证删除
            users = session.findSession().Where("user_name", "统一接口测试").Find(User.class);
            assertTrue(users.isEmpty());
        }
    }

    // 简单DataSource实现用于测试
    private static class SimpleDataSource implements javax.sql.DataSource {
        private final String jdbcUrl;

        public SimpleDataSource(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, "sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        // 实现其他必要方法...
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {}

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {}

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }
}
