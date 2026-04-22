package io.github.jsbxyyx.jdbcast.expr;

import java.util.List;

/**
 * A scalar SQL function call: {@code FUNC(arg1, arg2, ...)}.
 *
 * @param <V> the Java return type of the function
 */
public record FunctionExpr<V>(String name, List<Expr<?>> args) implements Expr<V> {

    public FunctionExpr {
        args = List.copyOf(args);
    }

    public static <V> FunctionExpr<V> of(String name, Expr<?>... args) {
        return new FunctionExpr<>(name, List.of(args));
    }
}
