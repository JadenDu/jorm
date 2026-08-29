// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.annotation.Enum;

import org.apiguardian.api.API;

/**
 * 主键生成策略。
 *
 * <ul>
 *   <li>{@link #AUTO} — 由框架自行选择;在支持的方言上按 {@code IDENTITY} 处理
 *       (数据库自增)。
 *   <li>{@link #IDENTITY} — 数据库标识/自增列。该字段不会出现在 INSERT
 *       语句中,并通过 {@code getGeneratedKeys()} 回读生成的值。
 *   <li>{@link #SEQUENCE} — 数据库序列(通过方言解析;JORM 尚未自动管理该策略——
 *       需在外部提供值,并在本版本中视为数据库侧处理)。
 *   <li>{@link #TABLE} — 传统的基于表的替代主键策略。与 {@link
 *       #SEQUENCE} 有同样的注意事项。
 *   <li>{@link #UUID} — 应用层 {@code java.util.UUID} 主键。该字段会包含在 INSERT
 *       语句中,当值为 {@code null} 时 JORM 会自动以随机 UUID 填充。
 * </ul>
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public enum GenerationType {
    AUTO,
    IDENTITY,
    SEQUENCE,
    TABLE,
    UUID
}
