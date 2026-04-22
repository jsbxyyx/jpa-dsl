package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/** A LIKE / NOT LIKE predicate: {@code expr [NOT] LIKE pattern}. */
public record LikeCondition(Expr<?> column, String pattern, boolean negated) implements Condition {
}
