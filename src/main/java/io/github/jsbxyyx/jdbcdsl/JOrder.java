package io.github.jsbxyyx.jdbcdsl;

/**
 * A single column order directive, mirroring Spring's {@code Sort.Order} API.
 * Uses {@link SFunction} method references instead of {@code String} property names.
 *
 * @param <T> the entity type that owns the property
 */
public final class JOrder<T> {

    /** Sort direction. */
    public enum Direction {
        ASC, DESC;

        /** Returns the opposite direction. */
        public Direction reverse() {
            return this == ASC ? DESC : ASC;
        }
    }

    /** NULL value handling strategy for ORDER BY. */
    public enum NullHandling {
        /** Let the database decide (no explicit NULLS clause). */
        NATIVE,
        /** Sort NULLs before non-null values. */
        NULLS_FIRST,
        /** Sort NULLs after non-null values. */
        NULLS_LAST
    }

    private final SFunction<T, ?> fn;
    private final PropertyRef propertyRef;
    private final Direction direction;
    private final boolean ignoreCase;
    private final NullHandling nullHandling;

    private JOrder(SFunction<T, ?> fn, PropertyRef propertyRef,
                   Direction direction, boolean ignoreCase, NullHandling nullHandling) {
        this.fn = fn;
        this.propertyRef = propertyRef;
        this.direction = direction;
        this.ignoreCase = ignoreCase;
        this.nullHandling = nullHandling;
    }

    // ------------------------------------------------------------------ //
    //  Static factories (mirror Sort.Order.asc / .desc / .by)
    // ------------------------------------------------------------------ //

    /** Creates an ascending order for the given property. */
    public static <T> JOrder<T> asc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, Direction.ASC, false, NullHandling.NATIVE);
    }

    /** Creates a descending order for the given property. */
    public static <T> JOrder<T> desc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, Direction.DESC, false, NullHandling.NATIVE);
    }

    /** Creates an order for the given property in the specified direction. */
    public static <T> JOrder<T> by(SFunction<T, ?> prop) {
        return asc(prop);
    }

    /** Creates an order for the given property in the specified direction. */
    public static <T> JOrder<T> by(Direction direction, SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, direction, false, NullHandling.NATIVE);
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    /** Returns the resolved property reference. */
    public PropertyRef getPropertyRef() {
        return propertyRef;
    }

    /** Returns the sort direction. */
    public Direction getDirection() {
        return direction;
    }

    /** Returns {@code true} if the direction is {@link Direction#ASC}. */
    public boolean isAscending() {
        return direction == Direction.ASC;
    }

    /** Returns {@code true} if the direction is {@link Direction#DESC}. */
    public boolean isDescending() {
        return direction == Direction.DESC;
    }

    /** Returns {@code true} if ordering is case-insensitive. */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /** Returns the null handling strategy. */
    public NullHandling getNullHandling() {
        return nullHandling;
    }

    // ------------------------------------------------------------------ //
    //  Fluent mutation methods (all return new instances)
    // ------------------------------------------------------------------ //

    /** Returns a new order with the direction reversed. */
    public JOrder<T> reverse() {
        return new JOrder<>(fn, propertyRef, direction.reverse(), ignoreCase, nullHandling);
    }

    /** Returns a new order with the given direction. */
    public JOrder<T> with(Direction direction) {
        return new JOrder<>(fn, propertyRef, direction, ignoreCase, nullHandling);
    }

    /** Returns a new order pointing to a different property (same direction and settings). */
    public JOrder<T> withProperty(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, direction, ignoreCase, nullHandling);
    }

    /** Returns a new order with case-insensitive comparison. */
    public JOrder<T> ignoreCase() {
        return new JOrder<>(fn, propertyRef, direction, true, nullHandling);
    }

    /** Returns a new order with the given null handling strategy. */
    public JOrder<T> with(NullHandling nullHandling) {
        return new JOrder<>(fn, propertyRef, direction, ignoreCase, nullHandling);
    }

    /** Returns a new order with {@link NullHandling#NULLS_FIRST}. */
    public JOrder<T> nullsFirst() {
        return with(NullHandling.NULLS_FIRST);
    }

    /** Returns a new order with {@link NullHandling#NULLS_LAST}. */
    public JOrder<T> nullsLast() {
        return with(NullHandling.NULLS_LAST);
    }

    /** Returns a new order with {@link NullHandling#NATIVE}. */
    public JOrder<T> nullsNative() {
        return with(NullHandling.NATIVE);
    }

    @Override
    public String toString() {
        return propertyRef.propertyName() + " " + direction
                + (ignoreCase ? " IGNORE CASE" : "")
                + (nullHandling != NullHandling.NATIVE ? " " + nullHandling : "");
    }
}
