package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/** An IS NULL / IS NOT NULL predicate: {@code expr IS [NOT] NULL}. */
public record NullCondition(Expr<?> column, boolean isNull) implements Condition {
}
