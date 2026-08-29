// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * 在整个框架中一致地将 Java 标识符(类名、字段名)转换为物理 SQL 标识符(表名、列名)。
 *
 * <p>实现必须是无状态的,并且可以安全地在多个线程间共享。当前生效的策略由
 * {@link io.github.jadendu.entity.EntityModel} 在构建实体元数据时查询,因此在实体元数据
 * 被缓存之前更换策略才有效。
 *
 * <p>注解覆盖始终优先——带有 {@code @Column(name = "user_name")} 注解的字段无论采用
 * 何种策略都会保留该确切的列名。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public interface NamingStrategy {

    /**
     * 根据 Java 属性名推导物理列名。
     *
     * @param propertyName 属性的 Java 标识符,永不为 null
     * @return 物理列名,永不为 null
     */
    String toColumnName(String propertyName);

    /**
     * 根据 Java 简单类名(不含包名)推导物理表名。仅当实体未标注
     * {@code @Table(name = "...")} 时使用。
     *
     * @param simpleClassName 简单类名,永不为 null
     * @return 物理表名,永不为 null
     */
    String toTableName(String simpleClassName);
}
