package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;

/**
 * A single column order directive, mirroring Spring's {@code Sort.Order} API.
 * Uses {@link SFunction} method references instead of {@code String} property names.
 *
 * <p>Supports ordering by arbitrary {@link SqlExpression} values (functions, aggregates, etc.)
 * in addition to plain column references.
 *
 * @param <T> the entity type that owns the property (use {@code ?} for function-based orders)
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
    /** The SQL expression to sort by. Always non-null; for column orders it is a {@link ColumnExpression}. */
    private final SqlExpression<?> expression;
    private final Direction direction;
    private final boolean ignoreCase;
    private final NullHandling nullHandling;

    private JOrder(SFunction<T, ?> fn, PropertyRef propertyRef, SqlExpression<?> expression,
                   Direction direction, boolean ignoreCase, NullHandling nullHandling) {
        this.fn = fn;
        this.propertyRef = propertyRef;
        this.expression = expression;
        this.direction = direction;
        this.ignoreCase = ignoreCase;
        this.nullHandling = nullHandling;
    }

    // ------------------------------------------------------------------ //
    //  Static factories – SFunction (original API)
    // ------------------------------------------------------------------ //

    /** Creates an ascending order for the given property. */
    public static <T> JOrder<T> asc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, ColumnExpression.of(ref), Direction.ASC, false, NullHandling.NATIVE);
    }

    /** Creates a descending order for the given property. */
    public static <T> JOrder<T> desc(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, ColumnExpression.of(ref), Direction.DESC, false, NullHandling.NATIVE);
    }

    /** Creates an ascending order for the given property. */
    public static <T> JOrder<T> by(SFunction<T, ?> prop) {
        return asc(prop);
    }

    /** Creates an order for the given property in the specified direction. */
    public static <T> JOrder<T> by(Direction direction, SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, ColumnExpression.of(ref), direction, false, NullHandling.NATIVE);
    }

    // ------------------------------------------------------------------ //
    //  Static factories – SqlExpression (new API for functions / aggregates)
    // ------------------------------------------------------------------ //

    /** Creates an ascending order for the given SQL expression (e.g. {@code LOWER(t.name)}). */
    public static <T> JOrder<T> asc(SqlExpression<?> expression) {
        return new JOrder<>(null, null, expression, Direction.ASC, false, NullHandling.NATIVE);
    }

    /** Creates a descending order for the given SQL expression. */
    public static <T> JOrder<T> desc(SqlExpression<?> expression) {
        return new JOrder<>(null, null, expression, Direction.DESC, false, NullHandling.NATIVE);
    }

    /** Creates an order for the given SQL expression in the specified direction. */
    public static <T> JOrder<T> by(Direction direction, SqlExpression<?> expression) {
        return new JOrder<>(null, null, expression, direction, false, NullHandling.NATIVE);
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    /**
     * Returns the SQL expression used for ordering.
     * For property-based orders this is a {@link ColumnExpression}; for function-based orders it
     * may be a {@link io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression} or
     * {@link io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression}.
     */
    public SqlExpression<?> getExpression() {
        return expression;
    }

    /**
     * Returns the resolved property reference, or {@code null} when this order was created from
     * a {@link SqlExpression} that is not a plain column reference.
     */
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
        return new JOrder<>(fn, propertyRef, expression, direction.reverse(), ignoreCase, nullHandling);
    }

    /** Returns a new order with the given direction. */
    public JOrder<T> with(Direction direction) {
        return new JOrder<>(fn, propertyRef, expression, direction, ignoreCase, nullHandling);
    }

    /** Returns a new order pointing to a different property (same direction and settings). */
    public JOrder<T> withProperty(SFunction<T, ?> prop) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        return new JOrder<>(prop, ref, ColumnExpression.of(ref), direction, ignoreCase, nullHandling);
    }

    /** Returns a new order with case-insensitive comparison. */
    public JOrder<T> ignoreCase() {
        return new JOrder<>(fn, propertyRef, expression, direction, true, nullHandling);
    }

    /** Returns a new order with the given null handling strategy. */
    public JOrder<T> with(NullHandling nullHandling) {
        return new JOrder<>(fn, propertyRef, expression, direction, ignoreCase, nullHandling);
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
        String exprStr = propertyRef != null ? propertyRef.propertyName() : expression.toString();
        return exprStr + " " + direction
                + (ignoreCase ? " IGNORE CASE" : "")
                + (nullHandling != NullHandling.NATIVE ? " " + nullHandling : "");
    }
}
