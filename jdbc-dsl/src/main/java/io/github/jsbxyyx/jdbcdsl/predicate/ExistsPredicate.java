package io.github.jsbxyyx.jdbcdsl.predicate;

import io.github.jsbxyyx.jdbcdsl.SelectSpec;

/**
 * Predicate for {@code EXISTS (SELECT ...)} and {@code NOT EXISTS (SELECT ...)}.
 *
 * <p>Example:
 * <pre>{@code
 * // WHERE EXISTS (SELECT 1 FROM t_order o WHERE o.user_id = t.id AND o.status = 'PAID')
 * .where(w -> w.exists(
 *     SelectBuilder.from(TOrder.class, "o")
 *         .select(SqlFunctions.literal("1"))
 *         .where(o -> o.eq(TOrder::getStatus, "PAID"))
 *         .mapToEntity()))
 * }</pre>
 */
public final class ExistsPredicate implements PredicateNode {

    private final SelectSpec<?, ?> subquery;
    private final boolean negated;

    public ExistsPredicate(SelectSpec<?, ?> subquery, boolean negated) {
        this.subquery = subquery;
        this.negated = negated;
    }

    public SelectSpec<?, ?> getSubquery() { return subquery; }
    public boolean isNegated() { return negated; }
}
