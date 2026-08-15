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
 * Read a database column into the requested Java type.
 *
 * <p>The default registry covers every type the framework needs out of the box: primitive boxes and
 * wrappers, {@link String}, {@link BigDecimal}, {@link BigInteger}, byte arrays, {@code
 * java.util.Date}, the {@code java.time} family, {@link UUID}, and {@link Enum}s. Users can plug in
 * additional handlers with {@link #register(Class, TypeHandler)}.
 *
 * <pre>{@code
 * TypeHandler.register(Money.class, (rs, col, type) -> Money.of(rs.getBigDecimal(col)));
 * }</pre>
 *
 * <p>Functions passed to {@code register} are called from a hot path; keep them allocation-free and
 * never throw inside unless genuinely invalid.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
@FunctionalInterface
public interface TypeHandler {

    /**
     * Read column {@code col} from {@code rs} and return a value compatible with {@code type}.
     * Implementations are responsible for honoring SQL NULL — see {@link
     * #nullAware(java.sql.ResultSet, Object, boolean)} for the cheapest alternative to writing the
     * same {@code wasNull()} toggle every time.
     */
    Object handle(ResultSet rs, String col, Class<?> type) throws SQLException;

    /**
     * Register (or override) a handler for {@code type}. Runtime calls are safe under the hooded
     * {@link ConcurrentHashMap}; user-supplied handlers are fed first to a {@code
     * TypeHandler.forType(type)} lookup.
     */
    @API(status = API.Status.STABLE)
    static void register(Class<?> type, TypeHandler handler) {
        Registry.register(type, handler);
    }

    /** Remove a previously-registered handler. */
    @API(status = API.Status.EXPERIMENTAL)
    static void unregister(Class<?> type) {
        Registry.unregister(type);
    }

    /**
     * Look up a handler for {@code type}; returns {@code null} when no handler is registered.
     * Callers (notably {@link ResultSetMapper}) fall back to {@link ResultSet#getObject(String)} in
     * that case.
     */
    static TypeHandler forType(Class<?> type) {
        return Registry.forType(type);
    }

    // -----------------------------------------------------------------
    // Helpers for composing wasNull-tolerant handlers without boilerplate.
    // -----------------------------------------------------------------

    /**
     * Memoise the "when SQL NULL, return X" idiom: the supplied {@code raw} is read first, and when
     * {@link ResultSet#wasNull()} returns true, the supplied {@code nullValue} is returned instead.
     */
    static Object nullAware(ResultSet rs, Object raw, Object nullValue) throws SQLException {
        return rs.wasNull() ? nullValue : raw;
    }

    /**
     * Variant of {@link #nullAware} that converts NULL to typed default (0 / false / null)
     * depending on whether {@code type} is the primitive or the wrapper.
     */
    static Object primitiveNull(ResultSet rs, Object raw, Class<?> type, Object primitiveDefault)
            throws SQLException {
        return rs.wasNull() ? (type.isPrimitive() ? primitiveDefault : null) : raw;
    }

    /**
     * Built-in registry backed by a {@link ConcurrentHashMap}. Two registrations are issued for
     * each numeric pair: {@code int.class} and {@code Integer.class} share the same handler
     * implementation but the null-boxing distinguishes primitive vs. wrapper.
     */
    final class Registry {

        private static final Map<Class<?>, TypeHandler> HANDLERS = new ConcurrentHashMap<>();

        static {
            // ---- Numeric pairs ----
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

            // ---- String family ----
            register(String.class, (rs, col, type) -> rs.getString(col));

            // ---- Binary ----
            register(byte[].class, (rs, col, type) -> rs.getBytes(col));
            register(InputStream.class, (rs, col, type) -> rs.getBinaryStream(col));
            register(Blob.class, (rs, col, type) -> rs.getBlob(col));
            register(Clob.class, (rs, col, type) -> rs.getClob(col));
            register(Reader.class, (rs, col, type) -> rs.getCharacterStream(col));

            // ---- Dates (java.util.* / java.sql.*) ----
            register(java.util.Date.class, (rs, col, type) -> rs.getTimestamp(col));
            register(Timestamp.class, (rs, col, type) -> rs.getTimestamp(col));
            register(java.sql.Date.class, (rs, col, type) -> rs.getDate(col));
            register(Time.class, (rs, col, type) -> rs.getTime(col));

            // ---- java.time ----
            // Try JDBC 4.2 first (getObject(col, Class)); fall back to Timestamp-based convert.

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

            // ---- Enum (lookup-by-name; falls back to ordinal on numeric columns) ----
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
                            // Maybe the column stored ordinals.
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
                // Enum subclasses land here — they were not directly registered.
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
