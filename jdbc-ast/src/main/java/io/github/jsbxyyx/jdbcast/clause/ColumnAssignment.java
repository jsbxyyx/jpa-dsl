package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.expr.Expr;

/**
 * A column assignment in an INSERT or UPDATE statement: {@code column = expr}.
 *
 * @param getter  method reference identifying the column
 * @param value   the expression to assign (typically a {@link io.github.jsbxyyx.jdbcast.expr.LiteralExpr})
 */
public record ColumnAssignment(SFunction<?, ?> getter, Expr<?> value) {
}
