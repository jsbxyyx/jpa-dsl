package io.github.jsbxyyx.jdbcast.expr;

/**
 * An expression with an alias: {@code expr AS alias}.
 *
 * <p>Typically used in the SELECT list. Created via {@link Expr#as(String)}.
 *
 * @param <V> the Java type of the wrapped expression
 */
public record AliasedExpr<V>(Expr<V> inner, String alias) implements Expr<V> {
}
