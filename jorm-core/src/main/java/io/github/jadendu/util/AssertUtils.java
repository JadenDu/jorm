// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import org.apiguardian.api.API;

import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * 用于非空断言的小型前置条件辅助类。更完善的 SQL 校验器现位于 {@link
 * SqlValidator};保留此类是为了与 1.x 调用方保持向后兼容。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class AssertUtils {

    private AssertUtils() {}

    /** 当 {@code obj} 为 null 时抛出 {@code JormException(code)}。 */
    public static void throwAway(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new JormException(code);
        }
    }
}
