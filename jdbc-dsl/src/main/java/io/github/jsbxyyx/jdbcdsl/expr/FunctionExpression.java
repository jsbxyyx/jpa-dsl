package io.github.jsbxyyx.jdbcdsl.expr;

import java.util.List;

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
}
