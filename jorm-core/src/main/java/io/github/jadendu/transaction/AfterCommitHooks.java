// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import java.util.ArrayList;
import java.util.List;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.transaction.AfterCommitHooks.SpringSupport;

/**
 * 提交后回调的中央注册表——JORM 缓存失效的关键枢纽。
 *
 * <p>任何想要"仅在当前事务提交后才执行某些操作"的代码都应调用
 * {@link #register(Runnable)}——调度器会判断当前处于哪种环境：
 *
 * <ul>
 *   <li><b>Spring {@code @Transactional}</b>——通过
 *       {@link
 *       org.springframework.transaction.support.TransactionSynchronizationManager#registerSynchronization}
 *       注册一个 {@code TransactionSynchronization}，使 Spring 在真正提交后触发回调，无论嵌套传播
 *       方式如何。这避免了 1.x 版本中缓存区域在事务<em>提交之前</em>
 *       就被清空的 bug。
 *   <li><b>JORM 管理的 {@link TransactionTemplate} / {@link TransactionManager}</b>——将回调缓冲到
 *       线程本地列表中；{@code TransactionTemplate.execute()} 在其提交成功后
 *       取出并执行这些回调。
 *   <li><b>无活动事务</b>——立即执行回调并记录任何失败
 *       （保留 1.x 旧版行为）。
 * </ul>
 *
 * <p>通过 {@code Class.forName} 检测 Spring；其不在 classpath 上是正常情况，永远不会
 * 抛出异常。Spring 集成类按需加载，绝不通过静态初始化器加载，因此核心
 * 用户即使 classpath 上没有 Spring 也不会有任何风险。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class AfterCommitHooks {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitHooks.class);

    private static volatile SpringSupport support;
    private static volatile boolean supportResolved = false;

    private static final ThreadLocal<List<Runnable>> JORM_CALLBACKS = new ThreadLocal<>();

    private AfterCommitHooks() {}

    /**
     * 注册一个在当前事务提交后执行的回调。如果没有活动事务，
     * 则立即执行，并尽力记录任何失败。
     */
    @API(status = API.Status.STABLE)
    public static void register(Runnable callback) {
        if (callback == null) {
            return;
        }
        SpringSupport s = resolveSupport();
        if (s != null && s.isTransactionActive()) {
            try {
                s.registerAfterCommit(callback);
                return;
            } catch (Throwable t) {
                log.warn(
                        "Spring after-commit registration failed; falling back to Jorm thread-local",
                        t);
                // 继续向下执行
            }
        }
        if (CurrentTransactionConnection.hasTransaction()) {
            List<Runnable> callbacks = JORM_CALLBACKS.get();
            if (callbacks == null) {
                callbacks = new ArrayList<>();
                JORM_CALLBACKS.set(callbacks);
            }
            callbacks.add(callback);
            return;
        }
        // 无事务：立即执行。
        runQuietly(callback);
    }

    /**
     * 当前线程上是否存在 Spring 管理的事务。当 classpath 中没有 Spring
     * 或检测失败时，始终为 {@code false}。
     */
    @API(status = API.Status.INTERNAL)
    public static boolean isSpringTransactionActive() {
        SpringSupport s = resolveSupport();
        try {
            return s != null && s.isTransactionActive();
        } catch (Throwable t) {
            log.debug("Spring transaction detection failed", t);
            return false;
        }
    }

    /**
     * 取出 JORM 管理的回调列表。由 {@link TransactionTemplate} 在其提交成功后立即调用——
     * 用户代码不得调用。
     */
    static List<Runnable> drain() {
        List<Runnable> callbacks = JORM_CALLBACKS.get();
        JORM_CALLBACKS.remove();
        return callbacks == null ? java.util.Collections.emptyList() : callbacks;
    }

    /**
     * 执行提供的回调，吞掉异常，使失败不会破坏提交调用方。
     */
    static void flush(List<Runnable> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) return;
        for (Runnable r : callbacks) {
            runQuietly(r);
        }
    }

    private static void runQuietly(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log.error("afterCommit callback failed", t);
        }
    }

    private static SpringSupport resolveSupport() {
        if (supportResolved) return support;
        synchronized (AfterCommitHooks.class) {
            if (supportResolved) return support;
            try {
                Class.forName(
                        "org.springframework.transaction.support.TransactionSynchronizationManager",
                        false,
                        Thread.currentThread().getContextClassLoader());
                try {
                    support =
                            (SpringSupport)
                                    Class.forName(
                                                    "io.github.jadendu.transaction.SpringAfterCommitSupport")
                                            .getDeclaredConstructor()
                                            .newInstance();
                } catch (ReflectiveOperationException e) {
                    log.debug("Spring integration class unavailable", e);
                    support = null;
                }
            } catch (ClassNotFoundException ignored) {
                support = null;
            }
            supportResolved = true;
        }
        return support;
    }

    /** 仅用于测试的钩子，用于清除 Spring 检测结果（例如注入 mock 的测试）。 */
    static void reset() {
        synchronized (AfterCommitHooks.class) {
            JORM_CALLBACKS.remove();
            support = null;
            supportResolved = false;
        }
    }

    /** 策略接口——实现位于单独导入 Spring 类型的类中。 */
    public interface SpringSupport {
        boolean isTransactionActive();

        void registerAfterCommit(Runnable callback);
    }
}
