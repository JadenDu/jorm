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
 * 面向依赖注入的友好入口——基于会话工厂的 Spring 风格 Bean。
 *
 * <p>相比于直接使用 {@code new FindSession()}/{@code
 * Jorm.findSession()},推荐 {@code JormTemplate} 的理由:
 *
 * <ul>
 *   <li>更好的可测试性——底层的 {@link JormSession} 访问可以被 mock,而无需
 *       静态装配 {@link io.github.jadendu.session.factory.Jorm}。
 *   <li>在 2.x 次版本中注入横切关注点(统计、追踪)的单一位置,
 *       且不会破坏调用方。
 *   <li>一致的生命周期:每次调用都将会话包裹在 {@code try-with-resources}
 *       中,只对外暴露结果。
 * </ul>
 *
 * <p>独立(非 Spring)用户也可以在调用 {@link
 * io.github.jadendu.session.factory.Jorm#setDataSource} 后直接构造该 Bean 来使用:
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

    /** 执行单次保存,并回写任何自动生成的主键。 */
    @API(status = API.Status.STABLE)
    public <T> void save(T entity) {
        try (SaveSession s = new SaveSession()) {
            s.save(entity);
        }
    }

    /**
     * 执行批量 INSERT 并返回生成的主键 id;按 {@link
     * io.github.jadendu.session.factory.Jorm#batchSize()} 分块执行。
     */
    @API(status = API.Status.STABLE)
    public <T> List<Long> batchSave(List<T> entities) {
        try (SaveSession s = new SaveSession()) {
            return s.batchSave(entities);
        }
    }

    /** 面向需要临时子句的调用方的通用查询方法。 */
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

    /** 流式查询方法;{@link Stream} 会被自动关闭。 */
    @API(status = API.Status.STABLE)
    public <T> List<T> findAsList(Function<FindSession, Stream<T>> action) {
        try (FindSession s = new FindSession();
                Stream<T> stream = action.apply(s)) {
            return stream.collect(java.util.stream.Collectors.toList());
        }
    }

    /** 分页查询:构建 SELECT + COUNT 以绑定总元素数。 */
    @API(status = API.Status.STABLE)
    public <T> Page<T> findPage(Class<T> cls, Pageable pageable) {
        try (FindSession s = new FindSession()) {
            return s.findPage(cls, pageable);
        }
    }

    /** 通用更新;遵循上游注入的校验/安全逻辑。 */
    @API(status = API.Status.STABLE)
    public void update(Function<UpdateSession, UpdateSession> action) {
        try (UpdateSession s = new UpdateSession()) {
            action.apply(s).update();
        }
    }

    /** 按主键删除单个实体。 */
    @API(status = API.Status.STABLE)
    public <T> void delete(T entity) {
        try (DeleteSession s = new DeleteSession()) {
            s.delete(entity);
        }
    }

    /** 条件删除;传入的构建器负责注册 WHERE/LIMIT 子句。 */
    @API(status = API.Status.STABLE)
    public <T> void delete(Class<T> cls, Function<DeleteSession, DeleteSession> action) {
        try (DeleteSession s = new DeleteSession()) {
            action.apply(s).delete(cls);
        }
    }

    /**
     * 将传入的闭包回调视为多语句代码块;开启一个统一的 {@link
     * JormSession},通过共享连接管理全部四种 CRUD 会话。
     */
    @API(status = API.Status.EXPERIMENTAL)
    public <R> R executeIn(JormSession block, Function<JormSession, R> action) {
        try (JormSession s = block) {
            return action.apply(s);
        }
    }

    /**
     * 为需要类型检查的调用方提供对缓存 {@link EntityModel} 注册表的只读
     * 访问入口。
     */
    @API(status = API.Status.EXPERIMENTAL)
    public EntityModel modelOf(Class<?> cls) {
        return io.github.jadendu.entity.EntityModelRegistry.get(cls);
    }
}
