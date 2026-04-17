package io.github.jsbxyyx.jdbcdsl.expr;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.PropertyRefResolver;
import io.github.jsbxyyx.jdbcdsl.SFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A SQL window function expression: {@code FUNC(...) OVER ([PARTITION BY ...] [ORDER BY ...])}.
 *
 * <p>Window functions compute a value across a set of rows related to the current row
 * without collapsing them into groups (unlike aggregate functions with GROUP BY).
 *
 * <p>Create instances via {@link AggregateExpression#over(Consumer)} or
 * {@link FunctionExpression#over(Consumer)} from a window-capable expression:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * // ROW_NUMBER() OVER (PARTITION BY t.status ORDER BY t.age DESC)
 * rowNumber().over(w -> w.partitionBy(TUser::getStatus).orderBy(JOrder.desc(TUser::getAge))).as("rn")
 *
 * // SUM(t.amount) OVER (PARTITION BY t.user_id ORDER BY t.id ASC)
 * sum(TOrder::getAmount).over(w -> w.partitionBy(TOrder::getUserId).orderBy(JOrder.asc(TOrder::getId))).as("cumAmt")
 *
 * // LAG(t.age, 1) OVER (ORDER BY t.id ASC)
 * lag(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("prevAge")
 * }</pre>
 *
 * @param <V> the Java type produced by the window function
 */
public final class WindowExpression<V> implements SqlExpression<V> {

    private final SqlExpression<V> function;
    private final List<SqlExpression<?>> partitionBy;
    private final List<JOrder<?>> orderBy;

    WindowExpression(SqlExpression<V> function,
                     List<SqlExpression<?>> partitionBy,
                     List<JOrder<?>> orderBy) {
        this.function = function;
        this.partitionBy = Collections.unmodifiableList(new ArrayList<>(partitionBy));
        this.orderBy = Collections.unmodifiableList(new ArrayList<>(orderBy));
    }

    /** Returns the base function or aggregate expression (the part before OVER). */
    public SqlExpression<V> getFunction() { return function; }

    /** Returns the PARTITION BY expressions (may be empty). */
    public List<SqlExpression<?>> getPartitionBy() { return partitionBy; }

    /** Returns the ORDER BY directives within the OVER clause (may be empty). */
    public List<JOrder<?>> getOrderBy() { return orderBy; }

    // ------------------------------------------------------------------ //
    //  Builder
    // ------------------------------------------------------------------ //

    /**
     * Fluent builder for the OVER clause of a window function.
     *
     * @param <V> the return type of the window function
     */
    public static final class Builder<V> {

        private final SqlExpression<V> function;
        private final List<SqlExpression<?>> partitionBy = new ArrayList<>();
        private final List<JOrder<?>> orderBy = new ArrayList<>();

        Builder(SqlExpression<V> function) {
            this.function = function;
        }

        /**
         * Adds PARTITION BY columns using method references.
         *
         * <p>Multiple calls accumulate (they do not replace previous partitions).
         */
        @SafeVarargs
        public final <T> Builder<V> partitionBy(SFunction<T, ?>... props) {
            for (SFunction<T, ?> p : props) {
                partitionBy.add(ColumnExpression.of(PropertyRefResolver.resolve(p)));
            }
            return this;
        }

        /**
         * Adds PARTITION BY expressions (column refs with explicit alias, functions, etc.).
         */
        public Builder<V> partitionBy(SqlExpression<?>... exprs) {
            partitionBy.addAll(Arrays.asList(exprs));
            return this;
        }

        /**
         * Adds ORDER BY directives for the OVER clause.
         * Uses the same {@link JOrder} as the outer query's ORDER BY.
         */
        @SafeVarargs
        public final <T> Builder<V> orderBy(JOrder<T>... orders) {
            orderBy.addAll(Arrays.asList(orders));
            return this;
        }

        /** Builds the {@link WindowExpression}. */
        public WindowExpression<V> build() {
            return new WindowExpression<>(function, partitionBy, orderBy);
        }
    }
}
