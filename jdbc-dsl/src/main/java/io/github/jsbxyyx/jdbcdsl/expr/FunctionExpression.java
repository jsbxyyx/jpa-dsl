package io.github.jsbxyyx.jdbcdsl.expr;

import java.util.List;
import java.util.function.Consumer;

/**
 * A SQL scalar function call expression, e.g. {@code UPPER(t.username)},
 * {@code COALESCE(t.email, 'N/A')}, {@code DATE(t.created_at)}.
 *
 * <p>Arguments are themselves {@link SqlExpression} instances, so function calls can be nested:
 * {@code UPPER(TRIM(t.username))}.
 *
 * @param <V> the Java type produced by the function
 */
public final class FunctionExpression<V> implements SqlExpression<V> {

    private final String functionName;
    private final List<SqlExpression<?>> args;

    public FunctionExpression(String functionName, List<SqlExpression<?>> args) {
        this.functionName = functionName;
        this.args = List.copyOf(args);
    }

    /** Returns the SQL function name (e.g. {@code "UPPER"}). */
    public String getFunctionName() {
        return functionName;
    }

    /** Returns the argument expressions. */
    public List<SqlExpression<?>> getArgs() {
        return args;
    }

    /**
     * Wraps this function with an OVER clause to create a {@link WindowExpression}.
     *
     * <p>Used primarily with no-arg window ranking functions:
     * <pre>{@code
     * rowNumber().over(w -> w.partitionBy(TUser::getStatus).orderBy(JOrder.asc(TUser::getId))).as("rn")
     * lag(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("prevAge")
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
     * Wraps this function with an empty OVER clause: {@code FUNC(...) OVER ()}.
     */
    public WindowExpression<V> over() {
        return new WindowExpression<>(this, List.of(), List.of(), null, null, null);
    }
}
