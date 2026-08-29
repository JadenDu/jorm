// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 当 {@code INSERT} 违反 {@code UNIQUE} 或 {@code PRIMARY KEY} 约束时抛出,
 * 由活动的 {@link io.github.jadendu.dialect.Dialect} 报告。
 *
 * <p>迁移说明:2.0 之前,该情况以通用的 {@link
 * ErrorCode#SQL_EXECUTION_FAILED} 呈现。现在捕获 {@code DuplicateKeyException}
 * 是文档化的应对方式。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public class DuplicateKeyException extends JormException {

    private static final long serialVersionUID = 1L;

    public DuplicateKeyException(String detailMessage, Throwable cause) {
        super(ErrorCode.DUPLICATE_KEY, detailMessage, cause);
    }

    public DuplicateKeyException(Throwable cause) {
        super(ErrorCode.DUPLICATE_KEY, cause);
    }
}
