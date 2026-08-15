// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jadendu.entity.User;
import io.github.jadendu.session.FindSession;
import io.github.jadendu.session.SaveSession;
import io.github.jadendu.session.UpdateSession;

@Service
public class TransactionalService {

    @Transactional
    public void saveUser(User user) {
        try (SaveSession session = new SaveSession()) {
            session.save(user);
        }
    }

    @Transactional
    public User findUser(Long id) {
        try (FindSession session = new FindSession()) {
            return session.Where("id", id).Find(User.class).stream().findFirst().orElse(null);
        }
    }

    @Transactional
    public void updateUserAge(Long id, int newAge) {
        try (UpdateSession session = new UpdateSession()) {
            session.Model(User.class).Where("id", id).Set("age", newAge).Update();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAndRollback(User user) {
        saveUser(user);
        throw new RuntimeException("Test rollback");
    }
}
