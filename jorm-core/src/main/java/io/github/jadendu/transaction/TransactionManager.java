// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;
import io.github.jadendu.session.factory.Jorm;

/**
 * 事务管理器
 *
 * <p>TransactionManager用于处理手动事务和闭包事务，需要用户手动开启事务、提交事务、回滚事务、释放连接。 示例代码如下：
 *
 * <pre>        Connection connection = TransactionManager.begin();
 *         try (JormSession session = new JormSession(connection)) {
 *             ...
 *             TransactionManager.commit();
 *             ...
 *         }catch (Exception e){
 *             TransactionManager.rollback();
 *             throw ...
 *         }finally {
 *             TransactionManager.release();
 *         }</pre>
 *
 * <p>注意：需要在最后释放连接，否则会造成连接泄漏。
 *
 * @author JadenDu
 * @version 1.0
 */
public class TransactionManager {
    // ThreadLocal保证线程安全
    private static final ThreadLocal<Connection> transactionConnectionHolder = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);

    /**
     * 开启事务
     *
     * <p>示例代码如下：
     *
     * <pre>Connection connection = TransactionManager.begin();</pre>
     *
     * @return 事务连接
     * @throws JormException 30001 事务开启失败
     */
    public static Connection begin() {
        Connection conn = transactionConnectionHolder.get();
        if (conn != null) {
            log.error(
                    "[ErrorCode={}] 事务开启失败,当前线程已存在事务连接，无法开启新的事务",
                    ErrorCode.TRANSACTION_BEGIN_FAILED.getCode());
            throw new JormException(ErrorCode.TRANSACTION_BEGIN_FAILED);
        }
        conn = Jorm.getConnection();
        try {
            conn.setAutoCommit(false);
            transactionConnectionHolder.set(conn);
            return conn;
        } catch (SQLException e) {
            log.error("[ErrorCode={}] 事务开启失败", ErrorCode.TRANSACTION_BEGIN_FAILED.getCode(), e);
            throw new JormException(ErrorCode.TRANSACTION_BEGIN_FAILED, e);
        }
    }

    /**
     * 提交事务
     *
     * <p>示例代码如下：
     *
     * <pre>TransactionManager.commit();</pre>
     *
     * @throws JormException 30002 事务提交失败
     */
    public static void commit() {
        Connection conn = transactionConnectionHolder.get();
        if (conn == null) {
            log.error("连接已从线程变量中移除，无法提交事务");
            throw new JormException(ErrorCode.TRANSACTION_COMMIT_FAILED);
        }
        try {
            conn.commit();
        } catch (SQLException e) {
            log.error("[ErrorCode={}] 事务提交失败", ErrorCode.TRANSACTION_COMMIT_FAILED.getCode(), e);
            throw new JormException(ErrorCode.TRANSACTION_COMMIT_FAILED, e);
        }
    }

    /**
     * 回滚事务
     *
     * <p>示例代码如下：
     *
     * <pre>TransactionManager.rollback();</pre>
     *
     * @throws JormException 30003 事务回滚失败
     */
    public static void rollback() {
        Connection conn = transactionConnectionHolder.get();
        if (conn == null) {
            log.warn("连接已从线程变量中移除，无需回滚操作");
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.error("[ErrorCode={}] 事务回滚失败", ErrorCode.TRANSACTION_ROLLBACK_FAILED.getCode(), e);
            throw new JormException(ErrorCode.TRANSACTION_ROLLBACK_FAILED, e);
        }
    }

    /**
     * 关闭事务
     *
     * <p>示例代码如下：
     *
     * <pre>TransactionManager.release();</pre>
     *
     * @throws JormException 30004 事务关闭失败
     */
    public static void release() {
        Connection conn = transactionConnectionHolder.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("[ErrorCode={}] 连接关闭失败", ErrorCode.TRANSACTION_CLOSE_FAILED.getCode(), e);
                throw new JormException(ErrorCode.TRANSACTION_CLOSE_FAILED, e);
            } finally {
                transactionConnectionHolder.remove();
            }
        } else {
            log.warn("连接已从线程变量中移除，无需关闭操作");
        }
    }

    // 获取当前事务连接（用于嵌套操作）
    public static Connection currentConnection() {
        return transactionConnectionHolder.get();
    }
}
