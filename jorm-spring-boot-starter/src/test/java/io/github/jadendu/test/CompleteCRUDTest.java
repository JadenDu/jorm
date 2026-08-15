// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.jadendu.entity.User;
import io.github.jadendu.session.*;
import io.github.jadendu.session.factory.Jorm;
import io.github.jadendu.test.service.TransactionalService;

@SpringBootTest(properties = "debug=true")
public class CompleteCRUDTest extends AbstractIntegrationTest {

    @Autowired private TransactionalService transactionalService;

    @Test
    @Transactional
    void testBatchOperations() {
        List<User> users =
                Arrays.asList(
                        new User("BatchUser1", 20, "active"),
                        new User("BatchUser2", 30, "inactive"),
                        new User("BatchUser3", 40, "active"));

        try (SaveSession session = new SaveSession()) {
            List<Long> ids = session.batchSave(users);
            assertEquals(3, ids.size());
            // 将生成的ID设置回实体对象
            for (int i = 0; i < users.size(); i++) {
                users.get(i).setId(ids.get(i));
            }
        }

        try (DeleteSession session = new DeleteSession()) {
            session.Delete(users);
        }

        try (FindSession session = new FindSession()) {
            List<User> found = session.Where("user_name", "like", "BatchUser%").Find(User.class);
            assertTrue(found.isEmpty());
        }
    }

    @Test
    @Transactional
    void testComplexQuery() throws SQLException {
        // ① 打印当前拿到的连接 autocommit 值
        Connection c = Jorm.getConnection();
        System.out.println(">>> autocommit = " + c.getAutoCommit());

        // ② 打印当前加载的 jorm 版本
        System.out.println(
                ">>> jorm jar location = "
                        + Jorm.class.getProtectionDomain().getCodeSource().getLocation());

        transactionalService.saveUser(new User("ComplexUser1", 25, "active"));
        transactionalService.saveUser(new User("ComplexUser2", 35, "inactive"));
        transactionalService.saveUser(new User("ComplexUser3", 45, "active"));

        try (FindSession session = new FindSession()) {
            List<User> result =
                    session.Select("id, user_name, age, status")
                            .Where("age", ">", 30)
                            .Where("status", "active")
                            .Order("age DESC")
                            .Limit(10)
                            .Find(User.class);

            assertEquals(1, result.size());
            assertEquals("ComplexUser3", result.get(0).getName());
        }
    }
}
