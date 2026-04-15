package io.github.jsbxyyx.jdbcdsl.predicate;

import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;

/**
 * Predicate for {@code col IN (SELECT ...)} and {@code col NOT IN (SELECT ...)}.
 *
 * <p>Example:
 * <pre>{@code
 * // WHERE t.id IN (SELECT user_id FROM t_order WHERE status = 'PAID')
 * .where(w -> w.in(TUser::getId,
 *     SelectBuilder.from(TOrder.class)
 *         .select(TOrder::getUserId)
 *         .where(o -> o.eq(TOrder::getStatus, "PAID"))
 *         .mapToEntity()))
 * }</pre>
 */
public final class InSubqueryPredicate implements PredicateNode {

    private final SqlExpression<?> lhs;
    private final SelectSpec<?, ?> subquery;
    private final boolean negated;

    public InSubqueryPredicate(SqlExpression<?> lhs, SelectSpec<?, ?> subquery, boolean negated) {
        this.lhs = lhs;
        this.subquery = subquery;
        this.negated = negated;
    }

    public SqlExpression<?> getLhs() { return lhs; }
    public SelectSpec<?, ?> getSubquery() { return subquery; }
    public boolean isNegated() { return negated; }
}
