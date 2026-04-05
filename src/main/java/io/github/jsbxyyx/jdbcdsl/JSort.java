package io.github.jsbxyyx.jdbcdsl;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type-safe sort specification for the jdbc-dsl.
 *
 * <p>Field references must be {@link SFunction} method references.
 * This class does <em>not</em> accept Spring {@code Sort} as input.
 * Use {@link #toSpringSort()} to convert to Spring's {@code Sort} for output only.
 *
 * @param <T> the entity type
 */
public final class JSort<T> {

    private final List<JOrder> orders;

    private JSort(List<JOrder> orders) {
        this.orders = Collections.unmodifiableList(orders);
    }

    /** Returns an unsorted (empty) sort specification. */
    public static <T> JSort<T> unsorted() {
        return new JSort<>(List.of());
    }

    /** Creates a new sort with the given property ascending. */
    public static <T> JSort<T> byAsc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JSort<>(List.of(new JOrder(ref, JOrder.Direction.ASC)));
    }

    /** Creates a new sort with the given property descending. */
    public static <T> JSort<T> byDesc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JSort<>(List.of(new JOrder(ref, JOrder.Direction.DESC)));
    }

    /** Returns a new {@link JSort} with an additional ascending order. */
    public JSort<T> andAsc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        List<JOrder> newOrders = new ArrayList<>(orders);
        newOrders.add(new JOrder(ref, JOrder.Direction.ASC));
        return new JSort<>(newOrders);
    }

    /** Returns a new {@link JSort} with an additional descending order. */
    public JSort<T> andDesc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        List<JOrder> newOrders = new ArrayList<>(orders);
        newOrders.add(new JOrder(ref, JOrder.Direction.DESC));
        return new JSort<>(newOrders);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public List<JOrder> getOrders() {
        return orders;
    }

    /**
     * Exports this sort to a Spring Data {@link Sort} (output adapter only).
     * Does not accept Spring Sort as input.
     */
    public Sort toSpringSort() {
        if (orders.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> springOrders = new ArrayList<>(orders.size());
        for (JOrder o : orders) {
            Sort.Direction dir = o.getDirection() == JOrder.Direction.ASC
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            springOrders.add(new Sort.Order(dir, o.getPropertyRef().propertyName()));
        }
        return Sort.by(springOrders);
    }
}
