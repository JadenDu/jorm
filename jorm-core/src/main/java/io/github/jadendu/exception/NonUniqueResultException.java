// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown when a {@code findOne} / {@code findSingle} query returned two or more rows where exactly
 * one row was expected.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class NonUniqueResultException extends JormException {

    private static final long serialVersionUID = 1L;

    public NonUniqueResultException(String message) {
        super(ErrorCode.NON_UNIQUE_RESULT, message);
    }
}
