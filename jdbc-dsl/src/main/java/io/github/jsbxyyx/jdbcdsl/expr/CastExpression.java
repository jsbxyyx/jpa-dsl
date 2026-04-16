package io.github.jsbxyyx.jdbcdsl.expr;

/**
 * A SQL {@code CAST(expr AS targetType)} expression.
 *
 * <p>Unlike regular function calls ({@code FUNC(arg1, arg2, ...)}), CAST uses the keyword
 * {@code AS} between its two operands rather than a comma, so it needs its own expression type
 * and renderer branch.
 *
 * <p>Example:
 * <pre>{@code
 * cast(TUser::getAge, "VARCHAR(10)")   // → CAST(t.age AS VARCHAR(10))
 * cast(TUser::getScore, "DECIMAL(10,2)")  // → CAST(t.score AS DECIMAL(10,2))
 * }</pre>
 *
 * @param <V> the Java type produced by the cast
 */
public final class CastExpression<V> implements SqlExpression<V> {

    private final SqlExpression<?> inner;
    private final String targetType;

    public CastExpression(SqlExpression<?> inner, String targetType) {
        this.inner = inner;
        this.targetType = targetType;
    }

    /** The expression being cast. */
    public SqlExpression<?> getInner() {
        return inner;
    }

    /**
     * The SQL target type string, e.g. {@code "VARCHAR(100)"}, {@code "SIGNED"},
     * {@code "DECIMAL(10,2)"}.  This is embedded verbatim — never put user-controlled
     * data here.
     */
    public String getTargetType() {
        return targetType;
    }
}
