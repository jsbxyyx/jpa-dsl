package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/**
 * A single ORDER BY item: an expression, a direction, and an optional NULLS placement.
 *
 * @param expr      the expression to sort by
 * @param asc       {@code true} for ASC, {@code false} for DESC
 * @param nullsFirst {@code true} = NULLS FIRST, {@code false} = NULLS LAST, {@code null} = database default
 */
public record OrderItem(Expr<?> expr, boolean asc, Boolean nullsFirst) {

    /** Creates an ascending order item with database-default NULLS placement. */
    public static OrderItem asc(Expr<?> expr)  { return new OrderItem(expr, true,  null); }
    /** Creates a descending order item with database-default NULLS placement. */
    public static OrderItem desc(Expr<?> expr) { return new OrderItem(expr, false, null); }

    public OrderItem withNullsFirst() { return new OrderItem(expr, asc, true);  }
    public OrderItem withNullsLast()  { return new OrderItem(expr, asc, false); }
}
