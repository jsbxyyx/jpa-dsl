package io.github.jsbxyyx.jdbcdsl.predicate;

import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;

/**
 * Predicate for scalar subquery comparisons:
 * {@code col = (SELECT ...)}, {@code col > (SELECT ...)}, etc.
 *
 * <p>The subquery must return exactly one row and one column (scalar value).
 *
 * <p>Example:
 * <pre>{@code
 * // WHERE t.age > (SELECT AVG(age) FROM t_user)
 * import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
 * ...
 * .where(w -> w.gtScalar(col(TUser::getAge), scalar(
 *     SelectBuilder.from(TUser.class)
 *         .select(SqlFunctions.avg(TUser::getAge).as("avgAge"))
 *         .mapToEntity())))
 * }</pre>
 */
public final class ScalarSubqueryPredicate implements PredicateNode {

    public enum Op { EQ, NE, GT, GTE, LT, LTE }

    private final SqlExpression<?> lhs;
    private final Op op;
    private final SelectSpec<?, ?> subquery;

    public ScalarSubqueryPredicate(SqlExpression<?> lhs, Op op, SelectSpec<?, ?> subquery) {
        this.lhs = lhs;
        this.op = op;
        this.subquery = subquery;
    }

    public SqlExpression<?> getLhs() { return lhs; }
    public Op getOp() { return op; }
    public SelectSpec<?, ?> getSubquery() { return subquery; }
}
