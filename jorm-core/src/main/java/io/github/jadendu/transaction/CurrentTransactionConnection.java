// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.transaction;

import java.sql.Connection;

/** 持有当前线程的事务连接 */
public class CurrentTransactionConnection {
    private static final ThreadLocal<Connection> currentConnection = new ThreadLocal<>();

    public static Connection get() {
        return currentConnection.get();
    }

    public static void set(Connection connection) {
        currentConnection.set(connection);
    }

    public static void clear() {
        currentConnection.remove();
    }

    public static boolean hasTransaction() {
        return currentConnection.get() != null;
    }
}
