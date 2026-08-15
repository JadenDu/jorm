// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apiguardian.api.API;

/**
 * Marks an entity property as <em>non-persistent</em>: the property will not be saved, updated, or
 * filtered on, and the corresponding column is excluded from the entity's whitelist of valid SQL
 * identifiers.
 *
 * <p>Typical use: a projection / computed field that exists only to receive an aggregate function
 * result from a {@code SELECT SUM(...)} or {@code COUNT(*)} query.
 *
 * @author JadenDu
 * @see Aggregation Deprecated alias for this annotation (kept until 3.0).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@API(status = API.Status.STABLE)
public @interface Transient {}
