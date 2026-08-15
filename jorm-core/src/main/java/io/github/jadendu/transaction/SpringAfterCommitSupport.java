// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import org.apiguardian.api.API;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.jadendu.transaction.AfterCommitHooks.SpringSupport;

/**
 * Spring integration: register the supplied callback to fire after the current Spring-managed
 * transaction commits. This class is loaded lazily through {@link AfterCommitHooks#register} only
 * when {@code TransactionSynchronizationManager} is present on the runtime classpath; {@code jorm}
 * users without Spring never see this class load.
 *
 * <p>Cache eviction registered by {@link io.github.jadendu.session.SaveSession} now fires when the
 * surrounding {@code @Transactional} commits, not on save-time, so concurrent readers never fill
 * the cache from a not-yet-visible transaction.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SpringAfterCommitSupport implements SpringSupport {

    public SpringAfterCommitSupport() {}

    @Override
    public boolean isTransactionActive() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    public void registerAfterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        callback.run();
                    }
                });
    }
}
