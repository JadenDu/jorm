// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apiguardian.api.API;

import io.github.jadendu.entity.EntityModel;
import io.github.jadendu.query.Page;
import io.github.jadendu.query.Pageable;
import io.github.jadendu.session.base.JormSession;

/**
 * DI-friendly entry point — Spring-style bean over a session factory.
 *
 * <p>Reasons to prefer {@code JormTemplate} over direct {@code new FindSession()}/{@code
 * Jorm.findSession()}:
 *
 * <ul>
 *   <li>Better testability — the underlying {@link JormSession} access can be mocked without
 *       statically wiring {@link io.github.jadendu.session.factory.Jorm}.
 *   <li>Single place to inject cross-cutting concerns (statistics, tracing) in 2.x point releases
 *       without breaking callers.
 *   <li>Consistent lifecycle: every invocation wraps its session in a {@code try-with-resources}
 *       and only the result is exposed.
 * </ul>
 *
 * <p>Standalone users can also use it by constructing the bean directly after calling {@link
 * io.github.jadendu.session.factory.Jorm#setDataSource}:
 *
 * <pre>{@code
 * JormTemplate jorm = new JormTemplate();
 * jorm.save(new User("Alice", 30, "active"));
 * List<User> active = jorm.find(s -> s.where("status", "active").find(User.class));
 * }</pre>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class JormTemplate {

    /** Run a single save and write back any auto-generated primary key. */
    @API(status = API.Status.STABLE)
    public <T> void save(T entity) {
        try (SaveSession s = new SaveSession()) {
            s.save(entity);
        }
    }

    /**
     * Run a batch INSERT and return the generated ids; chunked per {@link
     * io.github.jadendu.session.factory.Jorm#batchSize()}.
     */
    @API(status = API.Status.STABLE)
    public <T> List<Long> batchSave(List<T> entities) {
        try (SaveSession s = new SaveSession()) {
            return s.batchSave(entities);
        }
    }

    /** Generic finder for callers requiring ad-hoc clauses. */
    @API(status = API.Status.STABLE)
    public <T> List<T> find(Function<FindSession, List<T>> action) {
        try (FindSession s = new FindSession()) {
            return action.apply(s);
        }
    }

    @API(status = API.Status.STABLE)
    public <T> Optional<T> findOne(Function<FindSession, T> action) {
        try (FindSession s = new FindSession()) {
            return Optional.ofNullable(action.apply(s));
        }
    }

    /** Stream-style finder; the {@link Stream} is closed automatically. */
    @API(status = API.Status.STABLE)
    public <T> List<T> findAsList(Function<FindSession, Stream<T>> action) {
        try (FindSession s = new FindSession();
                Stream<T> stream = action.apply(s)) {
            return stream.collect(java.util.stream.Collectors.toList());
        }
    }

    /** Paginated query: builds SELECT + COUNT for total-element binding. */
    @API(status = API.Status.STABLE)
    public <T> Page<T> findPage(Class<T> cls, Pageable pageable) {
        try (FindSession s = new FindSession()) {
            return s.findPage(cls, pageable);
        }
    }

    /** Generic update; respects validation/security injected upstream. */
    @API(status = API.Status.STABLE)
    public void update(Function<UpdateSession, UpdateSession> action) {
        try (UpdateSession s = new UpdateSession()) {
            action.apply(s).update();
        }
    }

    /** Delete a single entity by its primary key. */
    @API(status = API.Status.STABLE)
    public <T> void delete(T entity) {
        try (DeleteSession s = new DeleteSession()) {
            s.delete(entity);
        }
    }

    /** Conditional delete; the supplied builder registers WHERE/LIMIT clauses. */
    @API(status = API.Status.STABLE)
    public <T> void delete(Class<T> cls, Function<DeleteSession, DeleteSession> action) {
        try (DeleteSession s = new DeleteSession()) {
            action.apply(s).delete(cls);
        }
    }

    /**
     * Treat the supplied closed callback as a multi-statement block; opens a unified {@link
     * JormSession} that manages all four CRUD sessions through a shared connection.
     */
    @API(status = API.Status.EXPERIMENTAL)
    public <R> R executeIn(JormSession block, Function<JormSession, R> action) {
        try (JormSession s = block) {
            return action.apply(s);
        }
    }

    /**
     * Read-only hook into the cached {@link EntityModel} registry for callers that need it for type
     * inspection.
     */
    @API(status = API.Status.EXPERIMENTAL)
    public EntityModel modelOf(Class<?> cls) {
        return io.github.jadendu.entity.EntityModelRegistry.get(cls);
    }
}
