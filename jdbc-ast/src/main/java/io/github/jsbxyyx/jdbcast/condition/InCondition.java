package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

import java.util.List;

/**
 * An IN / NOT IN predicate: {@code expr [NOT] IN (v1, v2, ...)}.
 *
 * <p>{@code values} elements can be plain Java values or {@link Expr} instances.
 */
public record InCondition(Expr<?> column, boolean negated, List<?> values) implements Condition {

    public InCondition {
        values = List.copyOf(values);
    }
}
