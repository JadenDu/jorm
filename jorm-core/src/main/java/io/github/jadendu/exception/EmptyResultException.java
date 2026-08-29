// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 当 {@code findOne} / {@code findSingle} 查询本应返回恰好一行,
 * 却一行都没有返回时抛出。
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
