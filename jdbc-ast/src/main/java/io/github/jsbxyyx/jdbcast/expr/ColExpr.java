package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.SFunction;

/**
 * A column reference expression: {@code [tableAlias.]columnName}.
 *
 * <p>Created via {@link io.github.jsbxyyx.jdbcast.clause.TableRef#col(SFunction)}.
 * The column name is resolved at render time by the {@link io.github.jsbxyyx.jdbcast.renderer.MetaResolver}.
 *
 * @param <V> the Java type of the column
 */
public record ColExpr<V>(SFunction<?, V> getter, String tableAlias) implements Expr<V> {
}
