// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import java.util.ArrayList;
import java.util.List;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.transaction.AfterCommitHooks.SpringSupport;

/**
 * Central post-commit callback registry — JORM's hinge for cache eviction.
 *
 * <p>Any code that wants to "do something only after the current transaction commits" should call
 * {@link #register(Runnable)} — the dispatcher figures out which environment is active:
 *
 * <ul>
 *   <li><b>Spring {@code @Transactional}</b> — registers a {@code TransactionSynchronization} via
 *       {@link
 *       org.springframework.transaction.support.TransactionSynchronizationManager#registerSynchronization}
 *       so Spring fires the callback after the real commit, regardless of nested propagation. This
 *       avoids the 1.x bug where cache regions were cleared <em>before</em> the transaction
 *       committed.
 *   <li><b>JORM-managed {@link TransactionTemplate} / {@link TransactionManager}</b> — buffers the
 *       callback in a thread-local list; {@code TransactionTemplate.execute()} drains and runs it
 *       after its commit succeeds.
 *   <li><b>No active transaction</b> — runs the callback immediately and logs any failures
 *       (preserves the legacy 1.x behaviour).
 * </ul>
 *
 * <p>Spring is detected via {@code Class.forName}; its absence on the classpath is normal and never
 * throws. The Spring integration class is loaded on demand, never via static initialiser, so core
 * users without Spring on the classpath pay no risks.
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
     * Register a callback to run after the current transaction commits. If no transaction is
     * active, run immediately and best-effort log any failure.
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
                // fall through
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
        // No transaction: run now.
        runQuietly(callback);
    }

    /**
     * Drain the JORM-managed callback list. Called by {@link TransactionTemplate} right after its
     * commit succeeds — never to be invoked by user code.
     */
    static List<Runnable> drain() {
        List<Runnable> callbacks = JORM_CALLBACKS.get();
        JORM_CALLBACKS.remove();
        return callbacks == null ? java.util.Collections.emptyList() : callbacks;
    }

    /**
     * Run the supplied callbacks, swallowing exceptions so failures cannot break the commit caller.
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

    /** Test-only hook to forget Spring presence (e.g., tests that inject mocks). */
    static void reset() {
        synchronized (AfterCommitHooks.class) {
            JORM_CALLBACKS.remove();
            support = null;
            supportResolved = false;
        }
    }

    /** Strategy interface — implementations live in a separate class that imports Spring types. */
    public interface SpringSupport {
        boolean isTransactionActive();

        void registerAfterCommit(Runnable callback);
    }
}
