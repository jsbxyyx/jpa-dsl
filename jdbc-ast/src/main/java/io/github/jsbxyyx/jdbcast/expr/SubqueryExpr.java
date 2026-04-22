package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

/**
 * A scalar subquery expression: {@code (SELECT ...)}.
 * Used in SELECT lists, WHERE comparisons, etc.
 *
 * @param <V> the nominal Java return type of the subquery
 */
public record SubqueryExpr<V>(SelectStatement subquery) implements Expr<V> {

    public static <V> SubqueryExpr<V> of(SelectStatement subquery) {
        return new SubqueryExpr<>(subquery);
    }
}
