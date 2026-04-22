package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.clause.FrameBound;
import io.github.jsbxyyx.jdbcast.clause.FrameType;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;

import java.util.List;
import java.util.function.Consumer;

/**
 * An aggregate function call: {@code COUNT(*)}, {@code SUM(col)}, {@code MAX(col)}, etc.
 * Supports wrapping with an OVER clause to produce a {@link WindowExpr}.
 *
 * @param <V> the Java return type of the aggregate
 */
public record AggExpr<V>(String name, List<Expr<?>> args, boolean distinct) implements Expr<V> {

    public AggExpr {
        args = List.copyOf(args);
    }

    public static <V> AggExpr<V> of(String name, Expr<?>... args) {
        return new AggExpr<>(name, List.of(args), false);
    }

    public static <V> AggExpr<V> distinct(String name, Expr<?>... args) {
        return new AggExpr<>(name, List.of(args), true);
    }

    /** Wraps this aggregate with a configured OVER clause to produce a {@link WindowExpr}. */
    public WindowExpr<V> over(Consumer<WindowExpr.Builder<V>> configure) {
        WindowExpr.Builder<V> b = new WindowExpr.Builder<>(this);
        configure.accept(b);
        return b.build();
    }

    /** {@code FUNC(...) OVER ()} — aggregate over the entire result set. */
    public WindowExpr<V> over() {
        return new WindowExpr<>(this, List.of(), List.of(), null, null, null);
    }
}
