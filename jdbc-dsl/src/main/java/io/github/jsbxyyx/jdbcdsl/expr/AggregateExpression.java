package io.github.jsbxyyx.jdbcdsl.expr;

import java.util.List;
import java.util.function.Consumer;

/**
 * A SQL aggregate function call expression, e.g. {@code COUNT(*)}, {@code SUM(t.amount)},
 * {@code MAX(t.age)}.
 *
 * <p>Aggregate expressions are typically used in SELECT and HAVING clauses.
 * Use {@link io.github.jsbxyyx.jdbcdsl.SqlFunctions} for pre-built factories.
 *
 * @param <V> the Java type produced by the aggregate function
 */
public final class AggregateExpression<V> implements SqlExpression<V> {

    private final String functionName;
    private final List<SqlExpression<?>> args;
    private final boolean distinct;

    private AggregateExpression(String functionName, List<SqlExpression<?>> args, boolean distinct) {
        this.functionName = functionName;
        this.args = List.copyOf(args);
        this.distinct = distinct;
    }

    /** Creates an aggregate expression with the given function name and arguments. */
    public static <V> AggregateExpression<V> of(String functionName, List<SqlExpression<?>> args) {
        return new AggregateExpression<>(functionName, args, false);
    }

    /** Creates a DISTINCT aggregate expression, e.g. {@code COUNT(DISTINCT t.status)}. */
    public static <V> AggregateExpression<V> ofDistinct(String functionName, List<SqlExpression<?>> args) {
        return new AggregateExpression<>(functionName, args, true);
    }

    /** Returns the aggregate function name (e.g. {@code "COUNT"}). */
    public String getFunctionName() {
        return functionName;
    }

    /** Returns the argument expressions. */
    public List<SqlExpression<?>> getArgs() {
        return args;
    }

    /** Returns {@code true} if this is a {@code DISTINCT} aggregate. */
    public boolean isDistinct() {
        return distinct;
    }

    /**
     * Wraps this aggregate with an OVER clause to create a {@link WindowExpression}.
     *
     * <p>Example — cumulative SUM partitioned by user:
     * <pre>{@code
     * sum(TOrder::getAmount).over(w -> w
     *     .partitionBy(TOrder::getUserId)
     *     .orderBy(JOrder.asc(TOrder::getId)))
     *     .as("cumAmount")
     * }</pre>
     *
     * @param consumer configures the OVER clause (PARTITION BY, ORDER BY)
     */
    public WindowExpression<V> over(Consumer<WindowExpression.Builder<V>> consumer) {
        WindowExpression.Builder<V> builder = new WindowExpression.Builder<>(this);
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * Wraps this aggregate with an empty OVER clause: {@code FUNC(...) OVER ()}.
     * Computes the aggregate over the entire result set.
     */
    public WindowExpression<V> over() {
        return new WindowExpression<>(this, List.of(), List.of());
    }
}
