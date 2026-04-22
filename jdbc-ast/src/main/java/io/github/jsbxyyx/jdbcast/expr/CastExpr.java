package io.github.jsbxyyx.jdbcast.expr;

/**
 * A {@code CAST(expr AS sqlType)} expression.
 *
 * @param <V> the Java target type after the cast
 */
public record CastExpr<V>(Expr<?> inner, String sqlType) implements Expr<V> {

    public static <V> CastExpr<V> of(Expr<?> inner, String sqlType) {
        return new CastExpr<>(inner, sqlType);
    }
}
