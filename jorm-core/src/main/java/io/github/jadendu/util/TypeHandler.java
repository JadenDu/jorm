// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.util;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apiguardian.api.API;

/**
 * 将数据库列读取为所请求的 Java 类型。
 *
 * <p>默认注册表覆盖了框架开箱即用所需的所有类型:基本类型的装箱类与
 * 包装类、{@link String}、{@link BigDecimal}、{@link BigInteger}、字节数组、{@code
 * java.util.Date}、{@code java.time} 家族、{@link UUID} 和 {@link Enum}。用户可以通过
 * {@link #register(Class, TypeHandler)} 注册额外的处理器。
 *
 * <pre>{@code
 * TypeHandler.register(Money.class, (rs, col, type) -> Money.of(rs.getBigDecimal(col)));
 * }</pre>
 *
 * <p>传给 {@code register} 的函数会在热路径上被调用;请让它们保持零分配,且除非确实
 * 无效,否则不要在内部抛出异常。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
@FunctionalInterface
public interface TypeHandler {

    /**
     * 从 {@code rs} 读取列 {@code col},并返回与 {@code type} 兼容的值。
     * 实现负责正确处理 SQL NULL——参见 {@link
     * #nullAware(java.sql.ResultSet, Object, boolean)},这是避免每次重复编写
     * {@code wasNull()} 判断的最省事方案。
     */
    Object handle(ResultSet rs, String col, Class<?> type) throws SQLException;

    /**
     * 为 {@code type} 注册(或覆盖)处理器。运行时调用在底层的
     * {@link ConcurrentHashMap} 支撑下是线程安全的;用户提供的处理器会优先通过 {@code
     * TypeHandler.forType(type)} 查找命中。
     */
    @API(status = API.Status.STABLE)
    static void register(Class<?> type, TypeHandler handler) {
        Registry.register(type, handler);
    }

    /** 移除先前注册的处理器。 */
    @API(status = API.Status.EXPERIMENTAL)
    static void unregister(Class<?> type) {
        Registry.unregister(type);
    }

    /**
     * 查找 {@code type} 对应的处理器;未注册时返回 {@code null}。
     * 此时调用方(尤其是 {@link ResultSetMapper})会回退到 {@link ResultSet#getObject(String)}。
     */
    static TypeHandler forType(Class<?> type) {
        return Registry.forType(type);
    }

    // -----------------------------------------------------------------
    // 用于无样板代码地组合容忍 wasNull 的处理器的辅助方法。
    // -----------------------------------------------------------------

    /**
     * 封装"SQL NULL 时返回 X"这一惯用写法:先读取传入的 {@code raw},当
     * {@link ResultSet#wasNull()} 返回 true 时,改为返回传入的 {@code nullValue}。
     */
    static Object nullAware(ResultSet rs, Object raw, Object nullValue) throws SQLException {
        return rs.wasNull() ? nullValue : raw;
    }

    /**
     * {@link #nullAware} 的变体,根据 {@code type} 是基本类型还是包装类,
     * 将 NULL 转换为带类型的默认值(0 / false / null)。
     */
    static Object primitiveNull(ResultSet rs, Object raw, Class<?> type, Object primitiveDefault)
            throws SQLException {
        return rs.wasNull() ? (type.isPrimitive() ? primitiveDefault : null) : raw;
    }

    /**
     * 由 {@link ConcurrentHashMap} 支撑的内置注册表。每对数值类型会注册两次:
     * {@code int.class} 和 {@code Integer.class} 共用同一个处理器
     * 实现,但通过 null 装箱区分基本类型与包装类。
     */
    final class Registry {

        private static final Map<Class<?>, TypeHandler> HANDLERS = new ConcurrentHashMap<>();

        static {
            // ---- 数值类型对 ----
            TypeHandler intRead = (rs, col, type) -> primitiveNull(rs, rs.getInt(col), type, 0);
            TypeHandler longRead = (rs, col, type) -> primitiveNull(rs, rs.getLong(col), type, 0L);
            TypeHandler shortRead =
                    (rs, col, type) -> primitiveNull(rs, rs.getShort(col), type, (short) 0);
            TypeHandler byteRead =
                    (rs, col, type) -> primitiveNull(rs, rs.getByte(col), type, (byte) 0);
            TypeHandler floatRead =
                    (rs, col, type) -> primitiveNull(rs, rs.getFloat(col), type, 0.0f);
            TypeHandler doubleRead =
                    (rs, col, type) -> primitiveNull(rs, rs.getDouble(col), type, 0.0d);
            TypeHandler booleanRead =
                    (rs, col, type) -> primitiveNull(rs, rs.getBoolean(col), type, false);

            registerPair(int.class, Integer.class, intRead);
            registerPair(long.class, Long.class, longRead);
            registerPair(short.class, Short.class, shortRead);
            registerPair(byte.class, Byte.class, byteRead);
            registerPair(float.class, Float.class, floatRead);
            registerPair(double.class, Double.class, doubleRead);
            registerPair(boolean.class, Boolean.class, booleanRead);

            register(BigDecimal.class, (rs, col, type) -> rs.getBigDecimal(col));
            register(
                    BigInteger.class,
                    (rs, col, type) -> {
                        BigDecimal bd = rs.getBigDecimal(col);
                        return bd == null ? null : bd.toBigInteger();
                    });

            // ---- 字符串族 ----
            register(String.class, (rs, col, type) -> rs.getString(col));

            // ---- 二进制 ----
            register(byte[].class, (rs, col, type) -> rs.getBytes(col));
            register(InputStream.class, (rs, col, type) -> rs.getBinaryStream(col));
            register(Blob.class, (rs, col, type) -> rs.getBlob(col));
            register(Clob.class, (rs, col, type) -> rs.getClob(col));
            register(Reader.class, (rs, col, type) -> rs.getCharacterStream(col));

            // ---- 日期(java.util.* / java.sql.*) ----
            register(java.util.Date.class, (rs, col, type) -> rs.getTimestamp(col));
            register(Timestamp.class, (rs, col, type) -> rs.getTimestamp(col));
            register(java.sql.Date.class, (rs, col, type) -> rs.getDate(col));
            register(Time.class, (rs, col, type) -> rs.getTime(col));

            // ---- java.time ----
            // 先尝试 JDBC 4.2 的 getObject(col, Class);失败则回退到基于 Timestamp 的转换。

            register(
                    LocalDate.class,
                    (rs, col, type) -> {
                        try {
                            return rs.getObject(col, LocalDate.class);
                        } catch (LinkageError | SQLException ignored) {
                            java.sql.Date d = rs.getDate(col);
                            return d == null ? null : d.toLocalDate();
                        }
                    });
            register(
                    LocalDateTime.class,
                    (rs, col, type) -> {
                        try {
                            return rs.getObject(col, LocalDateTime.class);
                        } catch (LinkageError | SQLException ignored) {
                            Timestamp ts = rs.getTimestamp(col);
                            return ts == null ? null : ts.toLocalDateTime();
                        }
                    });
            register(
                    LocalTime.class,
                    (rs, col, type) -> {
                        try {
                            return rs.getObject(col, LocalTime.class);
                        } catch (LinkageError | SQLException ignored) {
                            Time t = rs.getTime(col);
                            return t == null ? null : t.toLocalTime();
                        }
                    });
            register(
                    OffsetDateTime.class,
                    (rs, col, type) -> {
                        try {
                            return rs.getObject(col, OffsetDateTime.class);
                        } catch (LinkageError | SQLException ignored) {
                            return null;
                        }
                    });

            // ---- UUID ----
            register(
                    UUID.class,
                    (rs, col, type) -> {
                        try {
                            return rs.getObject(col, UUID.class);
                        } catch (LinkageError | SQLException ignored) {
                            String s = rs.getString(col);
                            return (s == null || s.isEmpty()) ? null : UUID.fromString(s);
                        }
                    });

            // ---- Enum(按名称查找;数值列上回退到序号) ----
            register(
                    Enum.class,
                    (rs, col, type) -> {
                        String name = rs.getString(col);
                        if (name == null || name.isEmpty()) {
                            return null;
                        }
                        if (!type.isEnum()) {
                            return name;
                        }
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Class<? extends Enum> enumType = (Class<? extends Enum>) type;
                        try {
                            return Enum.valueOf(enumType, name);
                        } catch (IllegalArgumentException e) {
                            // 也许列中存储的是序号。
                            try {
                                int ordinal = Integer.parseInt(name);
                                Object[] constants = enumType.getEnumConstants();
                                return ordinal >= 0 && ordinal < constants.length
                                        ? constants[ordinal]
                                        : null;
                            } catch (NumberFormatException ignored) {
                                return null;
                            }
                        }
                    });
        }

        private Registry() {}

        public static void register(Class<?> type, TypeHandler handler) {
            HANDLERS.put(type, handler);
        }

        public static void unregister(Class<?> type) {
            HANDLERS.remove(type);
        }

        public static TypeHandler forType(Class<?> type) {
            if (type == null) {
                return null;
            }
            TypeHandler h = HANDLERS.get(type);
            if (h == null && type.isEnum()) {
                // 枚举子类会落到这里——它们没有直接注册。
                return HANDLERS.get(Enum.class);
            }
            return h;
        }

        private static void registerPair(
                Class<?> primitive, Class<?> wrapper, TypeHandler handler) {
            HANDLERS.put(primitive, handler);
            HANDLERS.put(wrapper, handler);
        }
    }
}
