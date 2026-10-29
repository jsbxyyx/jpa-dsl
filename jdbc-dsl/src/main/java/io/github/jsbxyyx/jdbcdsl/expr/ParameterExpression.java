package io.github.jsbxyyx.jdbcdsl.expr;

/**
 * A value expression rendered as a named JDBC parameter, never interpolated into SQL text.
 * A {@code null} value is allowed and is bound as SQL {@code NULL}.
 *
 * @param <V> the Java type of the bound value
 */
public final class ParameterExpression<V> implements SqlExpression<V> {

    private final V value;

    private ParameterExpression(V value) {
        this.value = value;
    }

    public static <V> ParameterExpression<V> of(V value) {
        return new ParameterExpression<>(value);
    }

    public V getValue() {
        return value;
    }
}
