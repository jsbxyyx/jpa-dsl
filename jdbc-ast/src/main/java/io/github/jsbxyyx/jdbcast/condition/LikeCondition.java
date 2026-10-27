package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/** A LIKE / NOT LIKE predicate, optionally case-insensitive. */
public record LikeCondition(Expr<?> column, String pattern, boolean negated, boolean caseInsensitive)
        implements Condition {
    public LikeCondition(Expr<?> column, String pattern, boolean negated) {
        this(column, pattern, negated, false);
    }
}
