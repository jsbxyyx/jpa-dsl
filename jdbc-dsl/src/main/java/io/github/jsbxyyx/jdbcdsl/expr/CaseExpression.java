package io.github.jsbxyyx.jdbcdsl.expr;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A searched SQL {@code CASE WHEN ... THEN ... ELSE ... END} expression.
 *
 * <p>Use {@link #builder()} to construct, or the convenience factory in
 * {@code SqlFunctions.case_()}:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * // CASE WHEN t.status = 1 THEN 'Active' WHEN t.status = 0 THEN 'Inactive' ELSE 'Unknown' END AS statusLabel
 * case_()
 *     .when(LeafPredicate.ofExpr(col(User::getStatus), LeafPredicate.Op.EQ, 1), lit("'Active'"))
 *     .when(LeafPredicate.ofExpr(col(User::getStatus), LeafPredicate.Op.EQ, 0), lit("'Inactive'"))
 *     .otherwise(lit("'Unknown'"))
 *     .as("statusLabel")
 * }</pre>
 *
 * @param <V> the Java type of the value produced by this expression
 */
public final class CaseExpression<V> implements SqlExpression<V> {

    /** A single {@code WHEN condition THEN result} clause. */
    public record WhenClause(PredicateNode condition, SqlExpression<?> result) {}

    private final List<WhenClause> whenClauses;
    private final SqlExpression<?> elseExpr;

    private CaseExpression(List<WhenClause> whenClauses, SqlExpression<?> elseExpr) {
        this.whenClauses = List.copyOf(whenClauses);
        this.elseExpr = elseExpr;
    }

    public List<WhenClause> getWhenClauses() { return whenClauses; }

    /** Returns the {@code ELSE} expression, or {@code null} if none was specified. */
    public SqlExpression<?> getElseExpr() { return elseExpr; }

    /** Returns a new builder for constructing a {@link CaseExpression}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link CaseExpression}.
     */
    public static final class Builder {

        private final List<WhenClause> whenClauses = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a {@code WHEN condition THEN result} clause.
         *
         * @param condition the WHEN predicate (can be built with {@link io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate})
         * @param result    the THEN expression
         */
        public Builder when(PredicateNode condition, SqlExpression<?> result) {
            whenClauses.add(new WhenClause(condition, result));
            return this;
        }

        /**
         * Completes the CASE expression with an {@code ELSE} clause.
         *
         * @param elseExpr the ELSE expression
         */
        public <V> CaseExpression<V> otherwise(SqlExpression<V> elseExpr) {
            if (whenClauses.isEmpty()) {
                throw new IllegalStateException("CaseExpression requires at least one WHEN clause");
            }
            return new CaseExpression<>(whenClauses, elseExpr);
        }

        /**
         * Completes the CASE expression without an {@code ELSE} clause
         * (the database will return NULL for unmatched rows).
         */
        @SuppressWarnings("unchecked")
        public <V> CaseExpression<V> build() {
            if (whenClauses.isEmpty()) {
                throw new IllegalStateException("CaseExpression requires at least one WHEN clause");
            }
            return new CaseExpression<>(whenClauses, null);
        }
    }
}
