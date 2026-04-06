package io.github.jsbxyyx.jdbcdsl;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Type-safe sort specification for the jdbc-dsl, mirroring Spring's {@code Sort} API.
 *
 * <p>Field references use {@link SFunction} method references instead of {@code String} property
 * names. This class does <em>not</em> accept Spring {@code Sort} as input.
 * Use {@link #toSpringSort()} to convert to Spring's {@code Sort} for output only.
 *
 * @param <T> the entity type
 */
public final class JSort<T> implements Iterable<JOrder<T>> {

    private final List<JOrder<T>> orders;

    private JSort(List<JOrder<T>> orders) {
        this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
    }

    // ------------------------------------------------------------------ //
    //  Static factories (mirror Sort.by / Sort.unsorted)
    // ------------------------------------------------------------------ //

    /** Returns an unsorted (empty) sort specification. */
    public static <T> JSort<T> unsorted() {
        return new JSort<>(List.of());
    }

    /**
     * Creates a sort for the given properties in ascending order (mirrors {@code Sort.by(String...)}).
     */
    @SafeVarargs
    public static <T> JSort<T> by(SFunction<T, ?>... props) {
        List<JOrder<T>> list = new ArrayList<>(props.length);
        for (SFunction<T, ?> prop : props) {
            list.add(JOrder.asc(prop));
        }
        return new JSort<>(list);
    }

    /**
     * Creates a sort for the given properties in the specified direction
     * (mirrors {@code Sort.by(Direction, String...)}).
     */
    @SafeVarargs
    public static <T> JSort<T> by(JOrder.Direction direction, SFunction<T, ?>... props) {
        List<JOrder<T>> list = new ArrayList<>(props.length);
        for (SFunction<T, ?> prop : props) {
            list.add(JOrder.by(direction, prop));
        }
        return new JSort<>(list);
    }

    /**
     * Creates a sort from the given list of {@link JOrder} instances
     * (mirrors {@code Sort.by(List)}).
     */
    public static <T> JSort<T> by(List<JOrder<T>> orders) {
        return new JSort<>(orders);
    }

    /**
     * Creates a sort from the given {@link JOrder} instances
     * (mirrors {@code Sort.by(Order...)}).
     */
    @SafeVarargs
    public static <T> JSort<T> by(JOrder<T>... orders) {
        return new JSort<>(Arrays.asList(orders));
    }

    // ------------------------------------------------------------------ //
    //  Spring-equivalent instance methods
    // ------------------------------------------------------------------ //

    /**
     * Combines this sort with the given sort by appending its orders
     * (mirrors {@code Sort.and(Sort)}).
     */
    public JSort<T> and(JSort<T> other) {
        List<JOrder<T>> combined = new ArrayList<>(orders);
        combined.addAll(other.orders);
        return new JSort<>(combined);
    }

    /**
     * Returns a new sort with all orders set to ascending
     * (mirrors {@code Sort.ascending()}).
     */
    public JSort<T> ascending() {
        List<JOrder<T>> updated = new ArrayList<>(orders.size());
        for (JOrder<T> o : orders) {
            updated.add(o.with(JOrder.Direction.ASC));
        }
        return new JSort<>(updated);
    }

    /**
     * Returns a new sort with all orders set to descending
     * (mirrors {@code Sort.descending()}).
     */
    public JSort<T> descending() {
        List<JOrder<T>> updated = new ArrayList<>(orders.size());
        for (JOrder<T> o : orders) {
            updated.add(o.with(JOrder.Direction.DESC));
        }
        return new JSort<>(updated);
    }

    /**
     * Returns a new sort with all orders reversed
     * (mirrors {@code Sort.reverse()}).
     */
    public JSort<T> reverse() {
        List<JOrder<T>> reversed = new ArrayList<>(orders.size());
        for (JOrder<T> o : orders) {
            reversed.add(o.reverse());
        }
        return new JSort<>(reversed);
    }

    /** Returns {@code true} if this sort has at least one order (mirrors {@code Sort.isSorted()}). */
    public boolean isSorted() {
        return !orders.isEmpty();
    }

    /** Returns {@code true} if this sort has no orders (mirrors {@code Sort.isUnsorted()}). */
    public boolean isUnsorted() {
        return orders.isEmpty();
    }

    /** Returns {@code true} if this sort has no orders (alias for {@link #isUnsorted()}). */
    public boolean isEmpty() {
        return orders.isEmpty();
    }

    /**
     * Returns the order for the given property, or {@link Optional#empty()} if not found
     * (mirrors {@code Sort.getOrderFor(String)}).
     */
    public Optional<JOrder<T>> getOrderFor(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return orders.stream()
                .filter(o -> o.getPropertyRef() != null && o.getPropertyRef().equals(ref))
                .findFirst();
    }

    /** Returns an iterator over the orders (enables enhanced for-loop and Iterable usage). */
    @Override
    public Iterator<JOrder<T>> iterator() {
        return orders.iterator();
    }

    /** Returns a sequential stream over the orders. */
    public Stream<JOrder<T>> stream() {
        return orders.stream();
    }

    /**
     * Returns a new sort containing only orders that match the given predicate
     * (mirrors {@code Sort.filter(Predicate)}).
     */
    public JSort<T> filter(Predicate<? super JOrder<T>> predicate) {
        List<JOrder<T>> filtered = new ArrayList<>();
        for (JOrder<T> o : orders) {
            if (predicate.test(o)) {
                filtered.add(o);
            }
        }
        return new JSort<>(filtered);
    }

    /** Returns the list of orders. */
    public List<JOrder<T>> getOrders() {
        return orders;
    }

    // ------------------------------------------------------------------ //
    //  Backward-compatible convenience factories (original API)
    // ------------------------------------------------------------------ //

    /** Creates a new sort with the given property ascending. */
    public static <T> JSort<T> byAsc(SFunction<T, ?> prop) {
        return new JSort<>(List.of(JOrder.asc(prop)));
    }

    /** Creates a new sort with the given property descending. */
    public static <T> JSort<T> byDesc(SFunction<T, ?> prop) {
        return new JSort<>(List.of(JOrder.desc(prop)));
    }

    /** Returns a new {@link JSort} with an additional ascending order. */
    public JSort<T> andAsc(SFunction<T, ?> prop) {
        List<JOrder<T>> newOrders = new ArrayList<>(orders);
        newOrders.add(JOrder.asc(prop));
        return new JSort<>(newOrders);
    }

    /** Returns a new {@link JSort} with an additional descending order. */
    public JSort<T> andDesc(SFunction<T, ?> prop) {
        List<JOrder<T>> newOrders = new ArrayList<>(orders);
        newOrders.add(JOrder.desc(prop));
        return new JSort<>(newOrders);
    }

    // ------------------------------------------------------------------ //
    //  Output adapter
    // ------------------------------------------------------------------ //

    /**
     * Exports this sort to a Spring Data {@link Sort} (output adapter only).
     * Does not accept Spring Sort as input.
     */
    public Sort toSpringSort() {
        if (orders.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> springOrders = new ArrayList<>(orders.size());
        for (JOrder<T> o : orders) {
            if (o.getPropertyRef() == null) {
                // Function/expression-based orders cannot be represented in Spring's Sort; skip.
                continue;
            }
            Sort.Direction dir = o.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
            Sort.Order springOrder = new Sort.Order(dir, o.getPropertyRef().propertyName())
                    .with(toSpringNullHandling(o.getNullHandling()));
            if (o.isIgnoreCase()) {
                springOrder = springOrder.ignoreCase();
            }
            springOrders.add(springOrder);
        }
        return springOrders.isEmpty() ? Sort.unsorted() : Sort.by(springOrders);
    }

    private static Sort.NullHandling toSpringNullHandling(JOrder.NullHandling nh) {
        return switch (nh) {
            case NATIVE -> Sort.NullHandling.NATIVE;
            case NULLS_FIRST -> Sort.NullHandling.NULLS_FIRST;
            case NULLS_LAST -> Sort.NullHandling.NULLS_LAST;
        };
    }

    @Override
    public String toString() {
        return "JSort" + orders;
    }
}
