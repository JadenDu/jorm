// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.jadendu.annotation.Enum.GenerationType;

/** GeneratedValue 注解（用于定义主键生成策略） */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GeneratedValue {
    GenerationType strategy() default GenerationType.AUTO;
}
