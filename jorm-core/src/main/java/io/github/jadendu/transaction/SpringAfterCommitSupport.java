// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import org.apiguardian.api.API;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.jadendu.transaction.AfterCommitHooks.SpringSupport;

/**
 * Spring 集成：注册提供的回调，使其在当前 Spring 管理的事务提交后触发。本类仅在运行时
 * classpath 上存在 {@code TransactionSynchronizationManager} 时通过
 * {@link AfterCommitHooks#register} 懒加载；classpath 上没有 Spring 的 {@code jorm}
 * 用户永远不会看到本类被加载。
 *
 * <p>由 {@link io.github.jadendu.session.SaveSession} 注册的缓存驱逐操作现在会在外层
 * {@code @Transactional} 提交时触发，而非保存时，因此并发读者永远不会从
 * 尚未可见的事务中填充缓存。
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
