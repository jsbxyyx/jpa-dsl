package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.condition.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * A CASE expression.
 *
 * <ul>
 *   <li>Searched CASE: {@code CASE WHEN cond1 THEN e1 … ELSE eN END} — {@code operand} is null,
 *       each {@link WhenThen#when} is a {@link Condition}.</li>
 *   <li>Simple CASE: {@code CASE operand WHEN v1 THEN e1 … ELSE eN END} — {@code operand} is non-null,
 *       each {@link WhenThen#when} is an {@code Expr<?>} value.</li>
 * </ul>
 *
 * @param <V> the result type
 */
public record CaseExpr<V>(
        Expr<?> operand,         // null → searched CASE
        List<WhenThen<V>> whens,
        Expr<V> elseExpr         // null → no ELSE clause
) implements Expr<V> {

    public CaseExpr {
        whens = List.copyOf(whens);
    }

    /**
     * A single WHEN … THEN … branch.
     *
     * @param when  a {@link Condition} for searched CASE, or an {@code Expr<?>} for simple CASE
     * @param then  the result expression for this branch
     */
    public record WhenThen<V>(Object when, Expr<V> then) {}

    // ------------------------------------------------------------------ //
    //  Fluent builder for searched CASE
    // ------------------------------------------------------------------ //

    public static <V> SearchedBuilder<V> when(Condition cond, Expr<V> then) {
        return new SearchedBuilder<V>().when(cond, then);
    }

    public static final class SearchedBuilder<V> {
        private final List<WhenThen<V>> whens = new ArrayList<>();
        private Expr<V> elseExpr = null;

        private SearchedBuilder() {}

        public SearchedBuilder<V> when(Condition cond, Expr<V> then) {
            whens.add(new WhenThen<>(cond, then));
            return this;
        }

        public SearchedBuilder<V> otherwise(Expr<V> elseExpr) {
            this.elseExpr = elseExpr;
            return this;
        }

        public CaseExpr<V> build() {
            return new CaseExpr<>(null, whens, elseExpr);
        }
    }

    // ------------------------------------------------------------------ //
    //  Fluent builder for simple CASE
    // ------------------------------------------------------------------ //

    public static <V> SimpleBuilder<V> caseOf(Expr<?> operand) {
        return new SimpleBuilder<>(operand);
    }

    public static final class SimpleBuilder<V> {
        private final Expr<?> operand;
        private final List<WhenThen<V>> whens = new ArrayList<>();
        private Expr<V> elseExpr = null;

        private SimpleBuilder(Expr<?> operand) { this.operand = operand; }

        public SimpleBuilder<V> when(Expr<?> value, Expr<V> then) {
            whens.add(new WhenThen<>(value, then));
            return this;
        }

        public SimpleBuilder<V> otherwise(Expr<V> elseExpr) {
            this.elseExpr = elseExpr;
            return this;
        }

        public CaseExpr<V> build() {
            return new CaseExpr<>(operand, whens, elseExpr);
        }
    }
}
