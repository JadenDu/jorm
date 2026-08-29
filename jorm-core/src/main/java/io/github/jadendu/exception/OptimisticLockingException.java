// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 由支持乐观锁的 {@code UPDATE} 在版本检查与存储中的行不一致时抛出。
 *
 * <p>JORM 目前还不会自行自动递增版本列——该异常为计划中的 {@code @Version} 功能保留,
 * 现阶段由用户提供的代码显式抛出。
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
