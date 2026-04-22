package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/**
 * A BETWEEN / NOT BETWEEN predicate: {@code expr [NOT] BETWEEN low AND high}.
 *
 * <p>{@code low} and {@code high} can be plain Java values or {@link Expr} instances.
 */
public record BetweenCondition(Expr<?> column, Object low, Object high, boolean negated) implements Condition {
}
