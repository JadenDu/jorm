// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown when a {@code findOne} / {@code findSingle} query returned no rows where exactly one row
 * was expected.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class EmptyResultException extends JormException {

    private static final long serialVersionUID = 1L;

    public EmptyResultException(String message) {
        super(ErrorCode.EMPTY_RESULT, message);
    }
}
