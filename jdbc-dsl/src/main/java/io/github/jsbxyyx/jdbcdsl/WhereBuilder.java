package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.ExistsPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.InSubqueryPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import io.github.jsbxyyx.jdbcdsl.predicate.RawPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.ScalarSubqueryPredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for WHERE (and HAVING) clause predicates.
 *
 * <p>All field references use {@link SFunction} method references to guarantee static compilation.
 * Each method has a {@code boolean condition} overload that skips the predicate when {@code false}.
 *
 * <p>In addition, every predicate method has a {@link SqlExpression}-based overload that accepts
 * function or aggregate expressions as the left-hand operand, enabling conditions such as
 * {@code UPPER(t.email) = ?} or {@code COUNT(*) > ?}.
 *
 * <p><strong>v2.0 type safety:</strong> value parameters are now generic ({@code V}) and must
 * match the column type exactly. Cross-column comparisons require both sides to share the same
 * {@code V}. Intentional type mismatches (e.g. a BIGINT column compared to a VARCHAR column)
 * must use the {@link #raw(String)} escape hatch.
 *
 * @param <T> the root entity type
 */
public final class WhereBuilder<T> {

    private final List<PredicateNode> predicates = new ArrayList<>();
    private final String alias;
    private final Class<T> entityClass;

    WhereBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    // ------------------------------------------------------------------ //
    //  EQ / NE
    // ------------------------------------------------------------------ //

    public <V> WhereBuilder<T> eq(SFunction<T, V> prop, V value) {
        return eq(prop, value, true);
    }

    public <V> WhereBuilder<T> eq(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.EQ, value));
        }
        return this;
    }

    /** Cross-entity equality: allows filtering on joined table properties with an explicit alias. */
    public <X, V> WhereBuilder<T> eq(SFunction<X, V> prop, String tableAlias, V value) {
        predicates.add(LeafPredicate.of(PropertyRefResolver.resolve(prop), tableAlias, LeafPredicate.Op.EQ, value));
        return this;
    }

    /** Equality predicate with an arbitrary SQL expression on the left (e.g. a function call). */
    public <V> WhereBuilder<T> eq(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.EQ, value));
        return this;
    }

    /** Column–column equality: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> eq(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.EQ, right));
        return this;
    }

    public <V> WhereBuilder<T> ne(SFunction<T, V> prop, V value) {
        return ne(prop, value, true);
    }

    public <V> WhereBuilder<T> ne(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.NE, value));
        }
        return this;
    }

    /** Inequality predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> ne(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.NE, value));
        return this;
    }

    /** Column–column inequality: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> ne(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.NE, right));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  GT / GTE / LT / LTE
    // ------------------------------------------------------------------ //

    public <V> WhereBuilder<T> gt(SFunction<T, V> prop, V value) {
        return gt(prop, value, true);
    }

    public <V> WhereBuilder<T> gt(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.GT, value));
        }
        return this;
    }

    /** Greater-than predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> gt(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.GT, value));
        return this;
    }

    /** Column–column greater-than: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> gt(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.GT, right));
        return this;
    }

    public <V> WhereBuilder<T> gte(SFunction<T, V> prop, V value) {
        return gte(prop, value, true);
    }

    public <V> WhereBuilder<T> gte(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.GTE, value));
        }
        return this;
    }

    /** Greater-than-or-equal predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> gte(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.GTE, value));
        return this;
    }

    /** Column–column greater-than-or-equal: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> gte(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.GTE, right));
        return this;
    }

    public <V> WhereBuilder<T> lt(SFunction<T, V> prop, V value) {
        return lt(prop, value, true);
    }

    public <V> WhereBuilder<T> lt(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LT, value));
        }
        return this;
    }

    /** Less-than predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> lt(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.LT, value));
        return this;
    }

    /** Column–column less-than: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> lt(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.LT, right));
        return this;
    }

    public <V> WhereBuilder<T> lte(SFunction<T, V> prop, V value) {
        return lte(prop, value, true);
    }

    public <V> WhereBuilder<T> lte(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LTE, value));
        }
        return this;
    }

    /** Less-than-or-equal predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> lte(SqlExpression<V> expression, V value) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.LTE, value));
        return this;
    }

    /** Column–column less-than-or-equal: both sides must carry the same value type {@code V}. */
    public <V> WhereBuilder<T> lte(SqlExpression<V> left, SqlExpression<V> right) {
        predicates.add(LeafPredicate.ofExpr(left, LeafPredicate.Op.LTE, right));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  LIKE
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> like(SFunction<T, String> prop, String pattern) {
        return like(prop, pattern, true);
    }

    public WhereBuilder<T> like(SFunction<T, String> prop, String pattern, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LIKE, "%" + pattern + "%"));
        }
        return this;
    }

    /** LIKE predicate with an arbitrary SQL expression on the left. */
    public WhereBuilder<T> like(SqlExpression<String> expression, String pattern) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.LIKE, "%" + pattern + "%"));
        return this;
    }

    public WhereBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern) {
        return likeIgnoreCase(prop, pattern, true);
    }

    public WhereBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LIKE_IC, "%" + pattern + "%"));
        }
        return this;
    }

    /** Case-insensitive LIKE predicate with an arbitrary SQL expression on the left. */
    public WhereBuilder<T> likeIgnoreCase(SqlExpression<String> expression, String pattern) {
        predicates.add(LeafPredicate.ofExpr(expression, LeafPredicate.Op.LIKE_IC, "%" + pattern + "%"));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IN / NOT IN
    // ------------------------------------------------------------------ //

    public <V> WhereBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values) {
        return in(prop, values, true);
    }

    public <V> WhereBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofIn(resolve(prop), alias, values, false));
        }
        return this;
    }

    /** IN predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> in(SqlExpression<V> expression, Collection<? extends V> values) {
        predicates.add(LeafPredicate.ofExprIn(expression, values, false));
        return this;
    }

    public <V> WhereBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values) {
        return notIn(prop, values, true);
    }

    public <V> WhereBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofIn(resolve(prop), alias, values, true));
        }
        return this;
    }

    /** NOT IN predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> notIn(SqlExpression<V> expression, Collection<? extends V> values) {
        predicates.add(LeafPredicate.ofExprIn(expression, values, true));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IN / NOT IN  — subquery overloads
    // ------------------------------------------------------------------ //

    /**
     * {@code col IN (SELECT ...)} predicate.
     *
     * @param prop     property on the root entity to test
     * @param subquery inner SELECT that returns the candidate values
     */
    public WhereBuilder<T> in(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        return in(prop, subquery, true);
    }

    public WhereBuilder<T> in(SFunction<T, ?> prop, SelectSpec<?, ?> subquery, boolean condition) {
        if (condition) {
            predicates.add(new InSubqueryPredicate(
                    ColumnExpression.of(resolve(prop), alias), subquery, false));
        }
        return this;
    }

    /** {@code col NOT IN (SELECT ...)} predicate. */
    public WhereBuilder<T> notIn(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        return notIn(prop, subquery, true);
    }

    public WhereBuilder<T> notIn(SFunction<T, ?> prop, SelectSpec<?, ?> subquery, boolean condition) {
        if (condition) {
            predicates.add(new InSubqueryPredicate(
                    ColumnExpression.of(resolve(prop), alias), subquery, true));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  EXISTS / NOT EXISTS
    // ------------------------------------------------------------------ //

    /**
     * {@code EXISTS (SELECT ...)} predicate.
     *
     * @param subquery inner SELECT; only its existence is tested, not its values
     */
    public WhereBuilder<T> exists(SelectSpec<?, ?> subquery) {
        return exists(subquery, true);
    }

    public WhereBuilder<T> exists(SelectSpec<?, ?> subquery, boolean condition) {
        if (condition) {
            predicates.add(new ExistsPredicate(subquery, false));
        }
        return this;
    }

    /** {@code NOT EXISTS (SELECT ...)} predicate. */
    public WhereBuilder<T> notExists(SelectSpec<?, ?> subquery) {
        return notExists(subquery, true);
    }

    public WhereBuilder<T> notExists(SelectSpec<?, ?> subquery, boolean condition) {
        if (condition) {
            predicates.add(new ExistsPredicate(subquery, true));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery comparisons  (col OP (SELECT single-value))
    // ------------------------------------------------------------------ //

    /** {@code col = (SELECT single-value)} */
    public WhereBuilder<T> eq(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.EQ, subquery));
        return this;
    }

    /** {@code col <> (SELECT single-value)} */
    public WhereBuilder<T> ne(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.NE, subquery));
        return this;
    }

    /** {@code col > (SELECT single-value)} */
    public WhereBuilder<T> gt(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.GT, subquery));
        return this;
    }

    /** {@code col >= (SELECT single-value)} */
    public WhereBuilder<T> gte(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.GTE, subquery));
        return this;
    }

    /** {@code col < (SELECT single-value)} */
    public WhereBuilder<T> lt(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.LT, subquery));
        return this;
    }

    /** {@code col <= (SELECT single-value)} */
    public WhereBuilder<T> lte(SFunction<T, ?> prop, SelectSpec<?, ?> subquery) {
        predicates.add(new ScalarSubqueryPredicate(
                ColumnExpression.of(resolve(prop), alias), ScalarSubqueryPredicate.Op.LTE, subquery));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  BETWEEN
    // ------------------------------------------------------------------ //

    public <V> WhereBuilder<T> between(SFunction<T, V> prop, V lo, V hi) {
        return between(prop, lo, hi, true);
    }

    public <V> WhereBuilder<T> between(SFunction<T, V> prop, V lo, V hi, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofBetween(resolve(prop), alias, lo, hi));
        }
        return this;
    }

    /** BETWEEN predicate with an arbitrary SQL expression on the left. */
    public <V> WhereBuilder<T> between(SqlExpression<V> expression, V lo, V hi) {
        predicates.add(LeafPredicate.ofExprBetween(expression, lo, hi));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IS NULL / IS NOT NULL
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> isNull(SFunction<T, ?> prop) {
        return isNull(prop, true);
    }

    public WhereBuilder<T> isNull(SFunction<T, ?> prop, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofNullCheck(resolve(prop), alias, true));
        }
        return this;
    }

    /** IS NULL predicate with an arbitrary SQL expression on the left. */
    public WhereBuilder<T> isNull(SqlExpression<?> expression) {
        predicates.add(LeafPredicate.ofExprNullCheck(expression, true));
        return this;
    }

    public WhereBuilder<T> isNotNull(SFunction<T, ?> prop) {
        return isNotNull(prop, true);
    }

    public WhereBuilder<T> isNotNull(SFunction<T, ?> prop, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofNullCheck(resolve(prop), alias, false));
        }
        return this;
    }

    /** IS NOT NULL predicate with an arbitrary SQL expression on the left. */
    public WhereBuilder<T> isNotNull(SqlExpression<?> expression) {
        predicates.add(LeafPredicate.ofExprNullCheck(expression, false));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Raw SQL predicate (escape hatch)
    // ------------------------------------------------------------------ //

    /**
     * Embeds a raw SQL condition verbatim in the WHERE clause.
     *
     * <p>Use this escape hatch when the typed DSL cannot express a condition — for example,
     * database-specific functions, cross-column comparisons with intentionally mismatched types
     * (e.g. a BIGINT column compared to a VARCHAR column in a legacy schema), or complex
     * expressions.
     *
     * <p><strong>Warning:</strong> never pass user-controlled data in {@code sql}.
     * Bind user input as named parameters via {@link #raw(String, Map)}.
     *
     * <p>Example: {@code .where(w -> w.raw("t.age > t.min_age"))}
     */
    public WhereBuilder<T> raw(String sql) {
        return raw(sql, true);
    }

    /** Conditional raw SQL predicate — skipped when {@code condition} is {@code false}. */
    public WhereBuilder<T> raw(String sql, boolean condition) {
        if (condition) {
            predicates.add(new RawPredicate(sql, Map.of()));
        }
        return this;
    }

    /**
     * Embeds a raw SQL condition with named parameters in the WHERE clause.
     *
     * <p>Parameter names must not start with {@code p} followed by digits (e.g. {@code p1})
     * as those are reserved for auto-generated DSL parameters.
     *
     * <p>Example: {@code .where(w -> w.raw("YEAR(t.created_at) = :yr", Map.of("yr", 2024)))}
     */
    public WhereBuilder<T> raw(String sql, Map<String, Object> params) {
        return raw(sql, params, true);
    }

    /** Conditional raw SQL predicate with parameters — skipped when {@code condition} is {@code false}. */
    public WhereBuilder<T> raw(String sql, Map<String, Object> params, boolean condition) {
        if (condition) {
            predicates.add(new RawPredicate(sql, params));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  AND / OR composition
    // ------------------------------------------------------------------ //

    /** Adds a nested AND group. */
    public WhereBuilder<T> and(Consumer<WhereBuilder<T>> nested) {
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, alias);
        nested.accept(sub);
        PredicateNode node = sub.buildNode();
        if (node != null) {
            predicates.add(node);
        }
        return this;
    }

    /** Adds a nested OR group. */
    public WhereBuilder<T> or(Consumer<WhereBuilder<T>> nested) {
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, alias);
        nested.accept(sub);
        if (!sub.predicates.isEmpty()) {
            predicates.add(new OrPredicate(new ArrayList<>(sub.predicates)));
        }
        return this;
    }

    /** Adds a pre-built predicate node. */
    public WhereBuilder<T> predicate(PredicateNode node) {
        if (node != null) {
            predicates.add(node);
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Internal
    // ------------------------------------------------------------------ //

    PredicateNode buildNode() {
        if (predicates.isEmpty()) {
            return null;
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        return new AndPredicate(new ArrayList<>(predicates));
    }

    private PropertyRef resolve(SFunction<T, ?> fn) {
        return PropertyRefResolver.resolve(fn);
    }
}
