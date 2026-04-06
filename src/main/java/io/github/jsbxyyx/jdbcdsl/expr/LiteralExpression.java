package io.github.jsbxyyx.jdbcdsl.expr;

/**
 * A raw SQL literal fragment that is embedded verbatim into the generated SQL string
 * (not bound as a JDBC named parameter).
 *
 * <p>Examples: {@code *} (for {@code COUNT(*)}), {@code 0}, a quoted string {@code 'N/A'}.
 *
 * <p><strong>Warning:</strong> never pass user-controlled data as a literal — use JDBC bound
 * parameters (the {@code value} argument of WHERE builder methods) for user input.
 *
 * @param <V> the Java type conceptually associated with this literal
 */
public final class LiteralExpression<V> implements SqlExpression<V> {

    private final String sql;

    private LiteralExpression(String sql) {
        this.sql = sql;
    }

    /** Creates a literal SQL fragment. */
    public static <V> LiteralExpression<V> of(String sql) {
        return new LiteralExpression<>(sql);
    }

    /** Returns the raw SQL fragment to embed literally in the query. */
    public String getSql() {
        return sql;
    }
}
