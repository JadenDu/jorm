// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity.naming;

import org.apiguardian.api.API;

/**
 * 直通策略:Java 标识符原样成为物理 SQL 标识符。适用于模式已按 camelCase 或 PascalCase
 * 定义好的数据库(PostgreSQL 引号标识符、Oracle)。
 *
 * <p>表名不会被复数化;当没有 {@code @Table} 注解时,直接使用简单类名作为表名。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class IdentityNamingStrategy implements NamingStrategy {

    /** 单例实例——该类是无状态的。 */
    public static final IdentityNamingStrategy INSTANCE = new IdentityNamingStrategy();

    @Override
    public String toColumnName(String propertyName) {
        return propertyName == null ? "" : propertyName;
    }

    @Override
    public String toTableName(String simpleClassName) {
        return simpleClassName == null ? "" : simpleClassName;
    }
}
