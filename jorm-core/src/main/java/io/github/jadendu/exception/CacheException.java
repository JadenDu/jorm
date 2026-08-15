// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown when the L2 cache layer reports a serious failure — e.g. an unreachable Redis, a
 * serialisation mismatch, or an invalid cache-key shape. Cache misses are <em>not</em> raised; they
 * fall through to the database silently.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class CacheException extends JormException {

    private static final long serialVersionUID = 1L;

    public CacheException(String message, Throwable cause) {
        super(ErrorCode.CACHE_FAILURE, message, cause);
    }

    public CacheException(ErrorCode code, String detailMessage, Throwable cause) {
        super(code, detailMessage, cause);
    }
}
