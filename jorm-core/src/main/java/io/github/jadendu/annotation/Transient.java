// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apiguardian.api.API;

/**
 * 将实体属性标记为<em>非持久化</em>:该属性不会被保存、更新或用于过滤,
 * 对应的列也会从实体有效 SQL 标识符白名单中排除。
 *
 * <p>典型用途:投影/计算字段,仅用于接收 {@code SELECT SUM(...)} 或 {@code COUNT(*)}
 * 查询的聚合函数结果。
 *
 * @author JadenDu
 * @see Aggregation 本注解的已弃用别名(保留至 3.0)。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@API(status = API.Status.STABLE)
public @interface Transient {}
