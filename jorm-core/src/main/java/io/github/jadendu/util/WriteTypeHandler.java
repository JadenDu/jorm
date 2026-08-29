// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apiguardian.api.API;

/**
 * 在 INSERT / UPDATE 写入路径上,将 Java 值转换为传给 {@link PreparedStatement#setObject(int, Object)}
 * 的形式。
 *
 * <p><b>为什么需要它。</b> 读取侧的 {@link TypeHandler} 只覆盖 {@code ResultSet} 读取。
 * 在此 SPI 出现之前,框架对每个实体字段都直接使用 {@code
 * stmt.setObject(index, value)} 绑定。在 MySQL / Connector/J 8.x 上,该路径会把任何 {@code
 * Serializable} 值(尤其是 {@link Enum} 和 {@link UUID})序列化为 Java 序列化 blob(以
 * {@code \xAC\xED...} 开头),并且对不可序列化的自定义
 * 值类型抛出 {@code NotSerializableException}——因此枚举、UUID 和自定义值对象完全无法持久化。
 *
 * <p>此处理器在 {@code setObject} <em>之前</em> 运行,将值转换为 JDBC 原生
 * 形式(通常是 {@code String})。内置转换:
 *
 * <ul>
 *   <li>{@link Enum} → {@code enum.name()}(字符串)
 *   <li>{@link UUID} → {@code uuid.toString()}(字符串)
 *   <li>其他一切 → 原样透传(由驱动处理标准 JDBC 类型)
 * </ul>
 *
 * <p>自定义值类型可以用与读取侧处理器相同的方式注册:
 *
 * <pre>{@code
 * WriteTypeHandler.register(Money.class, (stmt, idx, value, type) -> stmt.setBigDecimal(idx, ((Money) value).amount()));
 * }</pre>
 *
 * <p>已注册的 {@link WriteTypeHandler} 优先于内置的 enum/uuid
 * 转换,让用户完全掌控其类型的写入方式。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
@FunctionalInterface
public interface WriteTypeHandler {

    /**
     * 将 {@code value}(类型为 {@code type})绑定到 {@code stmt} 的参数索引 {@code index}。
     *
     * @param stmt 当前活动的预编译语句
     * @param index 基于 1 的参数索引
     * @param value 实体字段值(可能为 {@code null})
     * @param type 字段声明的 Java 类型
     */
    void bind(PreparedStatement stmt, int index, Object value, Class<?> type)
            throws SQLException;

    /**
     * 为 {@code type} 注册(或覆盖)写入处理器。用户注册优先于
     * 内置的 enum / uuid 透传转换。
     */
    @API(status = API.Status.STABLE)
    static void register(Class<?> type, WriteTypeHandler handler) {
        Registry.register(type, handler);
    }

    /** 移除先前注册的处理器。 */
    @API(status = API.Status.EXPERIMENTAL)
    static void unregister(Class<?> type) {
        Registry.unregister(type);
    }

    /**
     * 将 {@code value} 转换为应传给 {@code stmt.setObject(index, ...)} 的对象,当 {@code type}
     * 不存在用户注册的处理器时应用内置的 enum / uuid 转换。{@code null} 原样返回。
     *
     * <p>这是 {@code SessionHelper} 在 INSERT 路径上使用的便捷入口;UPDATE
     * 路径也会调用它。需要完全控制(例如直接设置 BigDecimal)的调用方
     * 应改为注册 {@link WriteTypeHandler}。
     */
    static Object convert(Object value, Class<?> type) {
        return Registry.convert(value, type);
    }

    /**
     * 查找 {@code type} 对应的用户注册处理器;未注册时返回 {@code null}
     * (此时调用方回退到 {@link #convert})。
     */
    static WriteTypeHandler forType(Class<?> type) {
        return Registry.forType(type);
    }

    // -----------------------------------------------------------------
    // 注册表
    // -----------------------------------------------------------------

    final class Registry {

        private static final ConcurrentHashMap<Class<?>, WriteTypeHandler> HANDLERS =
                new ConcurrentHashMap<>();

        private Registry() {}

        static void register(Class<?> type, WriteTypeHandler handler) {
            HANDLERS.put(type, handler);
        }

        static void unregister(Class<?> type) {
            HANDLERS.remove(type);
        }

        static WriteTypeHandler forType(Class<?> type) {
            if (type == null) {
                return null;
            }
            WriteTypeHandler h = HANDLERS.get(type);
            if (h == null && type.isEnum()) {
                // 枚举子类不会单独注册;回退到下方静态代码块安装的
                // 共享 Enum.class 透传处理器。
                return HANDLERS.get(Enum.class);
            }
            return h;
        }

        static Object convert(Object value, Class<?> type) {
            if (value == null) {
                return null;
            }
            // 用户注册的处理器优先。
            WriteTypeHandler h = forType(type);
            if (h != null) {
                // 该处理器应自行调用 stmt.setXxx,因此不应走 convert 路径。
                // 对于纯粹的 convert() 便捷方法,我们仍需要一个值——
                // 所以在注册了自定义处理器时原样返回值;INSERT
                // 路径将改为直接调用处理器(参见 SessionHelper)。
                return value;
            }
            // 对驱动否则会错误处理的类型执行内置转换。
            if (value instanceof Enum) {
                return ((Enum<?>) value).name();
            }
            if (value instanceof UUID) {
                return value.toString();
            }
            // 标准 JDBC 类型直接透传。
            return value;
        }

        // 静态初始化块:安装共享的 Enum 处理器,使 forType(枚举子类型)能够解析。
        static {
            HANDLERS.put(
                    Enum.class,
                    (stmt, index, value, type) -> stmt.setString(index, ((Enum<?>) value).name()));
            HANDLERS.put(
                    UUID.class,
                    (stmt, index, value, type) -> stmt.setString(index, value.toString()));
        }
    }
}
