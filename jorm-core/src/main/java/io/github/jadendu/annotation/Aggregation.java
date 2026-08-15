// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apiguardian.api.API;

/**
 * Marks an entity property as non-persistent. Prefer the renamed {@link Transient} annotation —
 * this class is retained only for source compatibility and is scheduled for removal in 3.0.
 *
 * @author JadenDu
 * @deprecated since 2.0, use {@link Transient} instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Deprecated
@API(status = API.Status.DEPRECATED)
public @interface Aggregation {}
