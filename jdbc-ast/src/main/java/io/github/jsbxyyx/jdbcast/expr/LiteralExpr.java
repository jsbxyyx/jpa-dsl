package io.github.jsbxyyx.jdbcast.expr;

/**
 * A literal value expression that will be bound as a named parameter.
 *
 * @param <V> the Java type of the literal value
 */
public record LiteralExpr<V>(V value) implements Expr<V> {

    /** Convenience factory. */
    @SuppressWarnings("unchecked")
    public static <V> LiteralExpr<V> of(V value) {
        return new LiteralExpr<>(value);
    }
}
