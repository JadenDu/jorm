// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * Thrown by optimistic-locking-aware {@code UPDATE}s whose version check did not match the row in
 * storage.
 *
 * <p>JORM does not yet auto-increment version columns itself — the exception is reserved for the
 * planned {@code @Version} feature and emitted explicitly by user-supplied code in the meantime.
 *
 * @author JadenDu
 */
@API(status = API.Status.EXPERIMENTAL)
public class OptimisticLockingException extends JormException {

    private static final long serialVersionUID = 1L;

    public OptimisticLockingException(String message) {
        super(ErrorCode.OPTIMISTIC_LOCKING_FAILED, message);
    }
}
