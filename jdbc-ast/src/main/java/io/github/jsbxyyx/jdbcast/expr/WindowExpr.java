package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.clause.FrameBound;
import io.github.jsbxyyx.jdbcast.clause.FrameType;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;

import java.util.ArrayList;
import java.util.List;

/**
 * A window function expression: {@code FUNC(...) OVER ([PARTITION BY ...] [ORDER BY ...] [frame])}.
 *
 * @param <V> the Java return type
 */
public record WindowExpr<V>(
        Expr<?> function,
        List<Expr<?>> partitionBy,
        List<OrderItem> orderBy,
        FrameType frameType,   // null = no explicit frame clause
        FrameBound frameStart, // null = no explicit frame clause
        FrameBound frameEnd    // null = no explicit frame clause
) implements Expr<V> {

    public WindowExpr {
        partitionBy = List.copyOf(partitionBy);
        orderBy     = List.copyOf(orderBy);
    }

    // ------------------------------------------------------------------ //
    //  Builder
    // ------------------------------------------------------------------ //

    public static final class Builder<V> {
        private final Expr<V> function;
        private final List<Expr<?>> partitionBy = new ArrayList<>();
        private final List<OrderItem> orderBy   = new ArrayList<>();
        private FrameType  frameType  = null;
        private FrameBound frameStart = null;
        private FrameBound frameEnd   = null;

        Builder(Expr<V> function) { this.function = function; }

        @SafeVarargs
        public final <T> Builder<V> partitionBy(SFunction<T, ?>... getters) {
            for (SFunction<T, ?> g : getters) {
                partitionBy.add(new ColExpr<>(g, null));
            }
            return this;
        }

        public Builder<V> partitionBy(Expr<?>... exprs) {
            partitionBy.addAll(List.of(exprs));
            return this;
        }

        @SafeVarargs
        public final Builder<V> orderBy(OrderItem... items) {
            orderBy.addAll(List.of(items));
            return this;
        }

        public Builder<V> rowsBetween(FrameBound start, FrameBound end) {
            frameType = FrameType.ROWS; frameStart = start; frameEnd = end; return this;
        }

        public Builder<V> rangeBetween(FrameBound start, FrameBound end) {
            frameType = FrameType.RANGE; frameStart = start; frameEnd = end; return this;
        }

        public Builder<V> groupsBetween(FrameBound start, FrameBound end) {
            frameType = FrameType.GROUPS; frameStart = start; frameEnd = end; return this;
        }

        public WindowExpr<V> build() {
            return new WindowExpr<>(function, partitionBy, orderBy, frameType, frameStart, frameEnd);
        }
    }
}
