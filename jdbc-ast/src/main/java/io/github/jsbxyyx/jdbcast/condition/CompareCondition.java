package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.expr.Expr;

/**
 * A binary comparison predicate: {@code left op right}.
 *
 * <p>{@code right} can be:
 * <ul>
 *   <li>A plain Java value — rendered as a named parameter.</li>
 *   <li>An {@link Expr} — rendered as an expression (e.g. column, subquery).</li>
 * </ul>
 */
public record CompareCondition(Expr<?> left, Op op, Object right) implements Condition {
}
