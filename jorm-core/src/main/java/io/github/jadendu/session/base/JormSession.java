// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.session.base;

import java.sql.Connection;

import io.github.jadendu.session.DeleteSession;
import io.github.jadendu.session.FindSession;
import io.github.jadendu.session.SaveSession;
import io.github.jadendu.session.UpdateSession;

/**
 * 统一入口类，整合所有操作，通过内部委托实现链式连贯性。使用如下
 *
 * <pre>
 * try (JormSession session = new JormSession(connection))
 * </pre>
 *
 * @author JadenDu
 * @version 1.0
 * @see SaveSession
 * @see FindSession
 * @see UpdateSession
 * @see DeleteSession
 */
public class JormSession implements AutoCloseable {
    private Connection connection;
    private SaveSession saveSession;
    private FindSession findSession;
    private UpdateSession updateSession;
    private DeleteSession deleteSession;

    public JormSession(Connection conn) {
        this.connection = conn;
    }

    public JormSession() {}

    // 懒加载各会话实例
    public SaveSession saveSession() {
        if (saveSession == null) {
            if (connection != null) {
                saveSession = new SaveSession(connection);
            } else {
                saveSession = new SaveSession();
            }
        }
        return saveSession;
    }

    public FindSession findSession() {
        if (findSession == null) {
            if (connection != null) {
                findSession = new FindSession(connection);
            } else {
                findSession = new FindSession();
            }
        }
        return findSession;
    }

    public UpdateSession updateSession() {
        if (updateSession == null) {
            if (connection != null) {
                updateSession = new UpdateSession(connection);
            } else {
                updateSession = new UpdateSession();
            }
        }
        return updateSession;
    }

    public DeleteSession deleteSession() {
        if (deleteSession == null) {
            if (connection != null) {
                deleteSession = new DeleteSession(connection);
            } else {
                deleteSession = new DeleteSession();
            }
        }
        return deleteSession;
    }

    @Override
    public void close() {
        // 统一关闭所有会话
        if (saveSession != null) saveSession.close();
        if (findSession != null) findSession.close();
        if (updateSession != null) updateSession.close();
        if (deleteSession != null) deleteSession.close();
    }
}
