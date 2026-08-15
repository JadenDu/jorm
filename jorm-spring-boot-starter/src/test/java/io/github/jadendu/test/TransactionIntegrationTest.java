// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.jadendu.entity.User;
import io.github.jadendu.test.service.TransactionalService;

public class TransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TransactionalService transactionalService;

    @Test
    @Transactional
    void testJormOperationInSpringTransaction() {
        User user = new User("TransactionUser", 30, "active");

        transactionalService.saveUser(user);
        assertNotNull(user.getId());

        User found = transactionalService.findUser(user.getId());
        assertNotNull(found);
        assertEquals("TransactionUser", found.getName());
    }

    @Test
    @Transactional
    void testRollbackInSpringTransaction() {
        User user = new User("RollbackUser", 25, "active");

        assertThrows(
                RuntimeException.class,
                () -> {
                    transactionalService.saveAndRollback(user);
                });

        // 强制 flush 事务
        transactionalService.findUser(user.getId()); // 应该返回 null
    }
}
