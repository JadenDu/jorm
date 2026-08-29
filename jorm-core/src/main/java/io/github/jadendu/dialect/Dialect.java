// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.dialect;

import java.io.Serializable;
import java.sql.SQLException;

import org.apiguardian.api.API;

/**
 * 描述数据库 SQL 方言特性的 Dialect SPI。
 *
 * <p>SQL 构建器会借助 {@code Dialect} 来:
 *
 * <ul>
 *   <li>以数据库自身的语法生成 {@code LIMIT/OFFSET} 子句,
 *   <li>以可移植的方式检测 {@code duplicate-primary-key} 违规,
 *   <li>描述数据库支持哪些主键生成策略。
 * </ul>
 *
 * <p>实现应当是无状态的,并且可跨线程安全共享。可通过
 * {@link io.github.jadendu.session.factory.Jorm#setDialect(Dialect)} 选择启用的方言——当未显式
 * 配置方言时,starter 会根据 JDBC URL 自动选择一个。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public interface Dialect extends Serializable {

    /**
     * 向 SQL 语句中渲染 {@code LIMIT/OFFSET} 子句。
     *
     * @param limit 最大行数;未指定时为 null
     * @param offset 行偏移量(从零开始);未指定时为 null
     * @return 追加到 {@code SELECT}/{@code DELETE} 语句后的 SQL 片段;若两个参数均为 null,
     *     则为空
     */
    String getLimitClause(Integer limit, Integer offset);

    /**
     * 此数据库是否支持通过 JDBC {@code getGeneratedKeys()} 机制获取 {@code
     * IDENTITY} 风格的主键。
     */
    boolean supportsIdentity();

    /**
     * 判断 {@code e} 是否表示此数据库上的 {@code duplicate-primary-key} / 唯一键违规。
     * 实现应当保持保守——如果不确定则返回 false,以便改为抛出通用 SQL 错误。
     */
    boolean isDuplicateKey(SQLException e);

    /** 用于日志记录的人类可读名称(例如 {@code "MySQL"}、{@code "H2"})。 */
    String name();
}
