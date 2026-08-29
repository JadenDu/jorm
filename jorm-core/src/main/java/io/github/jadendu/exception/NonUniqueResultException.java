// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 当 {@code findOne} / {@code findSingle} 查询本应返回恰好一行,
 * 却返回了两行或多行时抛出。
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
