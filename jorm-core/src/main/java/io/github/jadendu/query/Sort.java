// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.query;

import java.util.Collections;
import java.util.List;

import org.apiguardian.api.API;

/**
 * Order-by definition for paginated queries.
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Sort {

    private final List<Order> orders;

    public Sort(List<Order> orders) {
        this.orders =
                orders == null ? Collections.emptyList() : Collections.unmodifiableList(orders);
    }

    /** Empty sort — emit no {@code ORDER BY} clause. */
    @API(status = API.Status.STABLE)
    public static Sort unsorted() {
        return new Sort(Collections.emptyList());
    }

    /** Ascending sort by {@code column}. */
    @API(status = API.Status.STABLE)
    public static Sort asc(String column) {
        return new Sort(Collections.singletonList(new Order(column, Direction.ASC)));
    }

    /** Descending sort by {@code column}. */
    @API(status = API.Status.STABLE)
    public static Sort desc(String column) {
        return new Sort(Collections.singletonList(new Order(column, Direction.DESC)));
    }

    /** Append a second clause to this sort. */
    @API(status = API.Status.STABLE)
    public Sort andAsc(String column) {
        return and(new Order(column, Direction.ASC));
    }

    @API(status = API.Status.STABLE)
    public Sort andDesc(String column) {
        return and(new Order(column, Direction.DESC));
    }

    private Sort and(Order o) {
        java.util.List<Order> list = new java.util.ArrayList<>(orders);
        list.add(o);
        return new Sort(list);
    }

    public List<Order> orders() {
        return orders;
    }

    /** Optimised to fit chainable {@code orderBy("name DESC, age ASC")}. */
    @API(status = API.Status.INTERNAL)
    public String toSql() {
        if (orders.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Order o : orders) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(o.column).append(' ').append(o.direction.name());
        }
        return sb.toString();
    }

    public enum Direction {
        ASC,
        DESC
    }

    public static final class Order {
        private final String column;
        private final Direction direction;

        public Order(String column, Direction direction) {
            this.column = column;
            this.direction = direction;
        }

        public String column() {
            return column;
        }

        public Direction direction() {
            return direction;
        }
    }
}
