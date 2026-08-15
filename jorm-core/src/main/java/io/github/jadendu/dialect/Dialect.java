// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.io.Serializable;
import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * Dialect SPI describing the SQL-flavoured specifics of a database.
 *
 * <p>A {@code Dialect} is consulted by the SQL builders to:
 *
 * <ul>
 *   <li>emit a {@code LIMIT/OFFSET} clause in the database's own grammar,
 *   <li>detect {@code duplicate-primary-key} violations in a portable way,
 *   <li>describe which primary-key generation strategies the database supports.
 * </ul>
 *
 * <p>Implementations should be stateless and safe to share across threads. Select the active
 * dialect through {@link io.github.jadendu.session.factory.Jorm#setDialect(Dialect)} — the starter
 * picks one automatically from the JDBC URL when no explicit dialect is configured.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public interface Dialect extends Serializable {

    /**
     * Render a {@code LIMIT/OFFSET} clause into the SQL statement.
     *
     * @param limit the maximum number of rows; null if unspecified
     * @param offset the row offset (zero-based); null if unspecified
     * @return SQL fragment appended to a {@code SELECT}/{@code DELETE}, possibly empty if both
     *     arguments are null
     */
    String getLimitClause(Integer limit, Integer offset);

    /**
     * Whether this database supports the JDBC {@code getGeneratedKeys()} mechanism for {@code
     * IDENTITY}-style primary keys.
     */
    boolean supportsIdentity();

    /**
     * Decide whether {@code e} represents a {@code duplicate-primary-key} / unique-key violation on
     * this database. Implementations should be conservative — if uncertain, return false so a
     * generic SQL-error is thrown instead.
     */
    boolean isDuplicateKey(SQLException e);

    /** Human-readable name for logging (e.g. {@code "MySQL"}, {@code "H2"}). */
    String name();
}
