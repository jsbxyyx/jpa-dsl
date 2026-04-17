package io.github.jsbxyyx.jdbcdsl.expr;

import io.github.jsbxyyx.jdbcdsl.SelectSpec;

/**
 * A SELECT-clause scalar subquery expression: {@code (SELECT single-value ...)}.
 *
 * <p>Use this to embed a correlated or uncorrelated subquery that returns exactly one
 * row and one column directly in the SELECT list, projecting it into a DTO property.
 *
 * <p>Create instances via {@link io.github.jsbxyyx.jdbcdsl.SqlFunctions#subquery(SelectSpec)}:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * // SELECT t.id, t.username, (SELECT COUNT(*) FROM t_order o WHERE o.user_id = t.id) AS orderCount
 * SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
 *         .select(countStar())
 *         .mapToEntity();
 *
 * SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
 *         .select(col(TUser::getUsername), subquery(countSpec).as("orderCount"))
 *         .mapTo(UserOrderCountDto.class);
 * }</pre>
 *
 * @param <V> the Java type produced by the subquery (matches the DTO property type)
 */
public final class ScalarSubqueryExpression<V> implements SqlExpression<V> {

    private final SelectSpec<?, ?> subquery;

    public ScalarSubqueryExpression(SelectSpec<?, ?> subquery) {
        this.subquery = subquery;
    }

    /** Returns the inner {@link SelectSpec} to render as a parenthesised subquery. */
    public SelectSpec<?, ?> getSubquery() {
        return subquery;
    }
}
