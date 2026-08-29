// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 当活动的 {@link io.github.jadendu.dialect.Dialect} 将某个 {@code SQLException} 归类为
 * 完整性违反(<em>除</em>主键冲突外)时抛出,例如外键违反、检查约束失败,
 * 或 {@code NOT NULL} 约束下的插入/更新。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DataIntegrityException extends JormException {

    private static final long serialVersionUID = 1L;

    public DataIntegrityException(String message, Throwable cause) {
        super(ErrorCode.DATA_INTEGRITY_VIOLATION, message, cause);
    }
}
