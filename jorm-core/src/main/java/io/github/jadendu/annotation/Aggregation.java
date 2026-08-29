// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apiguardian.api.API;

/**
 * 将实体属性标记为非持久化。建议使用更名后的 {@link Transient} 注解——
 * 本类仅为保持源码兼容性而保留,计划在 3.0 中移除。
 *
 * @author JadenDu
 * @deprecated 自 2.0 起弃用,请改用 {@link Transient}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Deprecated
@API(status = API.Status.DEPRECATED)
public @interface Aggregation {}
