// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown when an {@code INSERT} violates a {@code UNIQUE} or {@code PRIMARY KEY} constraint, as
 * reported by the active {@link io.github.jadendu.dialect.Dialect}.
 *
 * <p>Migration note: pre-2.0 this case surfaced as the generic {@link
 * ErrorCode#SQL_EXECUTION_FAILED}. Catching {@code DuplicateKeyException} is now the documented way
 * to react.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DuplicateKeyException extends JormException {

    private static final long serialVersionUID = 1L;

    public DuplicateKeyException(String detailMessage, Throwable cause) {
        super(ErrorCode.DUPLICATE_KEY, detailMessage, cause);
    }

    public DuplicateKeyException(Throwable cause) {
        super(ErrorCode.DUPLICATE_KEY, cause);
    }
}
