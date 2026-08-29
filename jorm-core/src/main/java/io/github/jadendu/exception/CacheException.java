// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.exception;

import org.apiguardian.api.API;

/**
 * 当 L2 缓存层报告严重故障时抛出——例如 Redis 不可达、序列化不匹配或缓存键形状无效。
 * 缓存未命中<em>不会</em>抛出该异常;未命中会静默地落到数据库层处理。
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
