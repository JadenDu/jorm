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
 * 手动事务管理器。
 *
 * <p>通过 ThreadLocal 管理当前线程的事务连接，需要用户手动调用 {@link #begin()} 开启事务、
 * {@link #commit()} 提交事务、{@link #rollback()} 回滚事务、{@link #release()} 释放连接。
 *
 * <p><b>重要：连接语义说明</b>。{@code TransactionManager} 维护的是<b>自己的</b> ThreadLocal
 * ({@code transactionConnectionHolder})，并不会将事务连接发布到 {@link
 * CurrentTransactionConnection}。因此：
 *
 * <ul>
 *   <li>裸的 {@code new SaveSession()} / {@code new FindSession()} 会通过 {@link
 *       io.github.jadendu.session.factory.Jorm#getConnection()} 获取<b>另一条</b>自动提交的
 *       连接，而<b>不会</b>加入本事务。
 *   <li>必须把 {@link #begin()} 返回的连接<b>显式传入</b>会话构造器，例如
 *       {@code new JormSession(conn)} 或 {@code new SaveSession(conn)}，才能让操作在同一事务内执行。
 * </ul>
 *
 * <p>示例代码：
 *
 * <pre>{@code
 * Connection conn = TransactionManager.begin();
 * try (JormSession session = new JormSession(conn)) {
 *     session.saveSession().save(user1);
 *     session.saveSession().save(user2);
 *     TransactionManager.commit();
 * } catch (Exception e) {
 *     TransactionManager.rollback();
 *     throw e;
 * } finally {
 *     TransactionManager.release();   // 必须释放，否则连接泄漏
 * }
 * }</pre>
 *
 * <p>如果希望闭包内裸 {@code new SaveSession()} 自动加入事务，请改用 {@link TransactionTemplate}——
 * 它会将连接发布到 {@link CurrentTransactionConnection}。
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
