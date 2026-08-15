// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation.Enum;

import org.apiguardian.api.API;

/**
 * Primary-key generation strategy.
 *
 * <ul>
 *   <li>{@link #AUTO} — let the framework choose; treated as {@code IDENTITY} on supported dialects
 *       (DB auto-increments).
 *   <li>{@link #IDENTITY} — database identity/auto-increment column. The field is excluded from
 *       INSERT and read back via {@code getGeneratedKeys()}.
 *   <li>{@link #SEQUENCE} — database sequence (resolves via the dialect; not yet auto-managed by
 *       JORM — supply the value externally and treat as DB-side for this release).
 *   <li>{@link #TABLE} — legacy table-based surrogate-key strategy. Same caveat as {@link
 *       #SEQUENCE}.
 *   <li>{@link #UUID} — application-level {@code java.util.UUID} primary key. The field is included
 *       in INSERT and JORM fills a {@code null} value automatically with a random UUID.
 * </ul>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public enum GenerationType {
    AUTO,
    IDENTITY,
    SEQUENCE,
    TABLE,
    UUID
}
