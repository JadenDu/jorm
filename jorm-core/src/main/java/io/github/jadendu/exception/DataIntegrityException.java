// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown for any {@code SQLException} classified by the active {@link
 * io.github.jadendu.dialect.Dialect} as an integrity violation <em>other than</em> a duplicate key,
 * e.g. a foreign-key violation, a check constraint failure, or a {@code NOT NULL} insert/update.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DataIntegrityException extends JormException {

    private static final long serialVersionUID = 1L;

    public DataIntegrityException(String message, Throwable cause) {
        super(ErrorCode.DATA_INTEGRITY_VIOLATION, message, cause);
    }
}
