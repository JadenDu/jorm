// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

/**
 * JORM 框架统一异常，封装所有数据库操作相关的错误
 *
 * @author 杜玉杰
 * @version 1.0.0
 */
public class JormException extends RuntimeException {
    private final ErrorCode errorCode;

    public JormException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public JormException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getMessage() + ": " + detailMessage);
        this.errorCode = errorCode;
    }

    public JormException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public JormException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode.getMessage() + ": " + detailMessage, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
