package io.github.jsbxyyx.jdbcdsl.expr;

import java.util.List;

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
}
