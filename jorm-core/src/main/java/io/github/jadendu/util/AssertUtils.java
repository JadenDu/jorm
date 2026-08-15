// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import org.apiguardian.api.API;

import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Tiny pre-condition helper for non-null assertions. Richer SQL validators are now in {@link
 * SqlValidator}; this class is preserved for backward compatibility with 1.x callers.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class AssertUtils {

    private AssertUtils() {}

    /** Throw {@code JormException(code)} when {@code obj} is null. */
    public static void throwAway(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new JormException(code);
        }
    }
}
