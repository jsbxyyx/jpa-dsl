package io.github.jsbxyyx.jdbcdsl.expr;

/**
 * Sealed interface representing any SQL value expression that can appear in SELECT, WHERE,
 * ORDER BY, GROUP BY, or HAVING clauses.
 *
 * <p>Permitted implementations:
 * <ul>
 *   <li>{@link ColumnExpression} – a plain {@code alias.column} reference</li>
 *   <li>{@link FunctionExpression} – a scalar SQL function call, e.g. {@code UPPER(t.email)}</li>
 *   <li>{@link LiteralExpression} – a raw SQL fragment embedded verbatim, e.g. {@code *}, {@code 'N/A'}</li>
 *   <li>{@link AggregateExpression} – an aggregate function call, e.g. {@code COUNT(*)}, {@code SUM(t.amount)}</li>
 *   <li>{@link AliasedExpression} – any expression wrapped with an explicit {@code AS <alias>}</li>
 * </ul>
 *
 * @param <V> the Java type of the value produced by this expression (used for type-safe wiring)
 */
public sealed interface SqlExpression<V>
        permits ColumnExpression, FunctionExpression, LiteralExpression, AggregateExpression, AliasedExpression {

    /**
     * Wraps this expression with an explicit SQL column alias ({@code <expr> AS <alias>}).
     *
     * <p>Use this when you need to map a function or aggregate expression to a JavaBean property:
     * <pre>{@code
     * upper(User::getEmail).as("emailUpper")  // → UPPER(t.email) AS emailUpper
     * }</pre>
     *
     * @param alias the column alias to apply in the SELECT clause
     * @return a new {@link AliasedExpression} wrapping this expression
     */
    default AliasedExpression<V> as(String alias) {
        return new AliasedExpression<>(this, alias);
    }
}
