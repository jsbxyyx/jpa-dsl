package io.github.jsbxyyx.jdbcdsl.expr;

/**
 * A SQL expression wrapped with an explicit column alias ({@code <expression> AS <alias>}).
 *
 * <p>Use {@link SqlExpression#as(String)} to create instances:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
 *     .select(upper(User::getEmail).as("emailUpper"))
 *     .mapTo(UserDto.class);
 * }</pre>
 *
 * <p>When rendered in a SELECT clause, this produces {@code UPPER(t.email) AS emailUpper},
 * which can then be mapped to a JavaBean property {@code setEmailUpper(String)}.
 *
 * @param <V> the Java type produced by the inner expression
 */
public final class AliasedExpression<V> implements SqlExpression<V> {

    private final SqlExpression<V> inner;
    private final String alias;

    AliasedExpression(SqlExpression<V> inner, String alias) {
        this.inner = inner;
        this.alias = alias;
    }

    /** Returns the wrapped expression. */
    public SqlExpression<V> getInner() {
        return inner;
    }

    /** Returns the column alias. */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns a new {@link AliasedExpression} wrapping the same inner expression with the given alias,
     * replacing any previous alias.
     */
    @Override
    public AliasedExpression<V> as(String newAlias) {
        return new AliasedExpression<>(inner, newAlias);
    }
}
