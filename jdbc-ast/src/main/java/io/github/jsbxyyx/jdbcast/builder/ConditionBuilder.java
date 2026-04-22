package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.condition.AndCondition;
import io.github.jsbxyyx.jdbcast.condition.BetweenCondition;
import io.github.jsbxyyx.jdbcast.condition.CompareCondition;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.condition.ExistsCondition;
import io.github.jsbxyyx.jdbcast.condition.InCondition;
import io.github.jsbxyyx.jdbcast.condition.LikeCondition;
import io.github.jsbxyyx.jdbcast.condition.NotCondition;
import io.github.jsbxyyx.jdbcast.condition.NullCondition;
import io.github.jsbxyyx.jdbcast.condition.Op;
import io.github.jsbxyyx.jdbcast.condition.OrCondition;
import io.github.jsbxyyx.jdbcast.condition.RawCondition;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for WHERE / HAVING conditions, designed to be used as a lambda argument.
 *
 * <p>All predicate methods accumulate conditions that are combined with AND by default.
 * Each method has a {@code boolean when} overload that silently skips the predicate when
 * {@code false} — the idiomatic pattern for dynamic queries:
 *
 * <pre>{@code
 * TableRef<TUser> u = TableRef.of(TUser.class, "u");
 *
 * // Dynamic WHERE — conditions added only when the value is non-null
 * SelectStatement stmt = SQL.from(u)
 *     .select(u.star())
 *     .where(w -> w
 *         .eq (u.col(TUser::getStatus), status,    status  != null)
 *         .gte(u.col(TUser::getAge),    minAge,    minAge  != null)
 *         .like(u.col(TUser::getUsername), keyword, keyword != null)
 *     )
 *     .build();
 *
 * // Nested OR group
 * .where(w -> w
 *     .eq(u.col(TUser::getStatus), "ACTIVE")
 *     .or(sub -> sub
 *         .eq(u.col(TUser::getRole), "ADMIN")
 *         .eq(u.col(TUser::getRole), "MANAGER"))
 * )
 * // → WHERE (u.status = :p1) AND ((u.role = :p2) OR (u.role = :p3))
 * }</pre>
 *
 * <p>Obtain an instance via {@link SelectBuilder#where(Consumer)},
 * {@link UpdateBuilder#where(Consumer)}, or {@link DeleteBuilder#where(Consumer)}.
 */
public final class ConditionBuilder {

    private final List<Condition> predicates = new ArrayList<>();

    ConditionBuilder() {}

    // ================================================================== //
    //  EQ / NE
    // ================================================================== //

    public <V> ConditionBuilder eq(Expr<V> col, V value) {
        return eq(col, value, true);
    }

    public <V> ConditionBuilder eq(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.EQ, value));
        return this;
    }

    /** Column–column equality: {@code left = right} (both sides are expressions). */
    public <V> ConditionBuilder eqCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.EQ, right));
        return this;
    }

    public <V> ConditionBuilder ne(Expr<V> col, V value) {
        return ne(col, value, true);
    }

    public <V> ConditionBuilder ne(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.NE, value));
        return this;
    }

    public <V> ConditionBuilder neCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.NE, right));
        return this;
    }

    // ================================================================== //
    //  GT / GTE / LT / LTE
    // ================================================================== //

    public <V> ConditionBuilder gt(Expr<V> col, V value) {
        return gt(col, value, true);
    }

    public <V> ConditionBuilder gt(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.GT, value));
        return this;
    }

    public <V> ConditionBuilder gtCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.GT, right));
        return this;
    }

    public <V> ConditionBuilder gte(Expr<V> col, V value) {
        return gte(col, value, true);
    }

    public <V> ConditionBuilder gte(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.GTE, value));
        return this;
    }

    public <V> ConditionBuilder gteCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.GTE, right));
        return this;
    }

    public <V> ConditionBuilder lt(Expr<V> col, V value) {
        return lt(col, value, true);
    }

    public <V> ConditionBuilder lt(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.LT, value));
        return this;
    }

    public <V> ConditionBuilder ltCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.LT, right));
        return this;
    }

    public <V> ConditionBuilder lte(Expr<V> col, V value) {
        return lte(col, value, true);
    }

    public <V> ConditionBuilder lte(Expr<V> col, V value, boolean when) {
        if (when) predicates.add(new CompareCondition(col, Op.LTE, value));
        return this;
    }

    public <V> ConditionBuilder lteCol(Expr<V> left, Expr<V> right) {
        predicates.add(new CompareCondition(left, Op.LTE, right));
        return this;
    }

    // ================================================================== //
    //  LIKE / NOT LIKE
    // ================================================================== //

    public ConditionBuilder like(Expr<String> col, String pattern) {
        return like(col, pattern, true);
    }

    public ConditionBuilder like(Expr<String> col, String pattern, boolean when) {
        if (when) predicates.add(new LikeCondition(col, pattern, false));
        return this;
    }

    public ConditionBuilder notLike(Expr<String> col, String pattern) {
        return notLike(col, pattern, true);
    }

    public ConditionBuilder notLike(Expr<String> col, String pattern, boolean when) {
        if (when) predicates.add(new LikeCondition(col, pattern, true));
        return this;
    }

    // ================================================================== //
    //  IS NULL / IS NOT NULL
    // ================================================================== //

    public ConditionBuilder isNull(Expr<?> col) {
        return isNull(col, true);
    }

    public ConditionBuilder isNull(Expr<?> col, boolean when) {
        if (when) predicates.add(new NullCondition(col, true));
        return this;
    }

    public ConditionBuilder isNotNull(Expr<?> col) {
        return isNotNull(col, true);
    }

    public ConditionBuilder isNotNull(Expr<?> col, boolean when) {
        if (when) predicates.add(new NullCondition(col, false));
        return this;
    }

    // ================================================================== //
    //  IN / NOT IN
    // ================================================================== //

    public <V> ConditionBuilder in(Expr<V> col, Collection<? extends V> values) {
        return in(col, values, true);
    }

    public <V> ConditionBuilder in(Expr<V> col, Collection<? extends V> values, boolean when) {
        if (when) predicates.add(new InCondition(col, false, List.copyOf(values)));
        return this;
    }

    @SuppressWarnings("unchecked")
    public <V> ConditionBuilder in(Expr<V> col, V... values) {
        predicates.add(new InCondition(col, false, Arrays.asList(values)));
        return this;
    }

    public <V> ConditionBuilder notIn(Expr<V> col, Collection<? extends V> values) {
        return notIn(col, values, true);
    }

    public <V> ConditionBuilder notIn(Expr<V> col, Collection<? extends V> values, boolean when) {
        if (when) predicates.add(new InCondition(col, true, List.copyOf(values)));
        return this;
    }

    @SuppressWarnings("unchecked")
    public <V> ConditionBuilder notIn(Expr<V> col, V... values) {
        predicates.add(new InCondition(col, true, Arrays.asList(values)));
        return this;
    }

    // ================================================================== //
    //  BETWEEN / NOT BETWEEN
    // ================================================================== //

    public <V> ConditionBuilder between(Expr<V> col, V lo, V hi) {
        return between(col, lo, hi, true);
    }

    public <V> ConditionBuilder between(Expr<V> col, V lo, V hi, boolean when) {
        if (when) predicates.add(new BetweenCondition(col, lo, hi, false));
        return this;
    }

    public <V> ConditionBuilder notBetween(Expr<V> col, V lo, V hi) {
        return notBetween(col, lo, hi, true);
    }

    public <V> ConditionBuilder notBetween(Expr<V> col, V lo, V hi, boolean when) {
        if (when) predicates.add(new BetweenCondition(col, lo, hi, true));
        return this;
    }

    // ================================================================== //
    //  EXISTS / NOT EXISTS
    // ================================================================== //

    public ConditionBuilder exists(SelectStatement subquery) {
        predicates.add(ExistsCondition.exists(subquery));
        return this;
    }

    public ConditionBuilder notExists(SelectStatement subquery) {
        predicates.add(ExistsCondition.notExists(subquery));
        return this;
    }

    // ================================================================== //
    //  Raw SQL escape hatch
    // ================================================================== //

    public ConditionBuilder raw(String sql) {
        return raw(sql, true);
    }

    public ConditionBuilder raw(String sql, boolean when) {
        if (when) predicates.add(RawCondition.of(sql));
        return this;
    }

    public ConditionBuilder raw(String sql, Map<String, Object> params) {
        return raw(sql, params, true);
    }

    public ConditionBuilder raw(String sql, Map<String, Object> params, boolean when) {
        if (when) predicates.add(RawCondition.of(sql, params));
        return this;
    }

    // ================================================================== //
    //  Nested AND / OR / NOT groups
    // ================================================================== //

    /**
     * Adds a nested AND group. Predicates inside are combined with AND.
     *
     * <pre>{@code
     * .where(w -> w
     *     .eq(u.col(TUser::getStatus), "ACTIVE")
     *     .and(sub -> sub
     *         .gte(u.col(TUser::getAge), 18)
     *         .lte(u.col(TUser::getAge), 65))
     * )
     * // → WHERE (u.status = :p1) AND ((u.age >= :p2) AND (u.age <= :p3))
     * }</pre>
     */
    public ConditionBuilder and(Consumer<ConditionBuilder> nested) {
        ConditionBuilder sub = new ConditionBuilder();
        nested.accept(sub);
        Condition c = sub.build();
        if (c != null) predicates.add(c);
        return this;
    }

    /**
     * Adds a nested OR group. Predicates inside are combined with OR.
     *
     * <pre>{@code
     * .where(w -> w
     *     .eq(u.col(TUser::getStatus), "ACTIVE")
     *     .or(sub -> sub
     *         .eq(u.col(TUser::getRole), "ADMIN")
     *         .eq(u.col(TUser::getRole), "SUPER_ADMIN"))
     * )
     * // → WHERE (u.status = :p1) AND ((u.role = :p2) OR (u.role = :p3))
     * }</pre>
     */
    public ConditionBuilder or(Consumer<ConditionBuilder> nested) {
        ConditionBuilder sub = new ConditionBuilder();
        nested.accept(sub);
        if (!sub.predicates.isEmpty()) {
            predicates.add(sub.predicates.size() == 1
                    ? sub.predicates.get(0)
                    : new OrCondition(new ArrayList<>(sub.predicates)));
        }
        return this;
    }

    /**
     * Adds a NOT group. Predicates inside are combined with AND then negated.
     *
     * <pre>{@code
     * .where(w -> w
     *     .not(sub -> sub
     *         .eq(u.col(TUser::getStatus), "DELETED")
     *         .isNull(u.col(TUser::getEmail)))
     * )
     * // → WHERE NOT ((u.status = :p1) AND (u.email IS NULL))
     * }</pre>
     */
    public ConditionBuilder not(Consumer<ConditionBuilder> nested) {
        ConditionBuilder sub = new ConditionBuilder();
        nested.accept(sub);
        Condition inner = sub.build();
        if (inner != null) predicates.add(new NotCondition(inner));
        return this;
    }

    /**
     * Adds a pre-built {@link Condition} directly (for conditions created from
     * {@link Expr} fluent methods, e.g. {@code u.col(TUser::getAge).between(18, 65)}).
     */
    public ConditionBuilder condition(Condition condition) {
        if (condition != null) predicates.add(condition);
        return this;
    }

    public ConditionBuilder condition(Condition condition, boolean when) {
        if (when && condition != null) predicates.add(condition);
        return this;
    }

    // ================================================================== //
    //  Build
    // ================================================================== //

    /**
     * Combines all accumulated predicates with AND and returns the resulting {@link Condition}.
     * Returns {@code null} if no predicates were added (indicates no WHERE clause).
     */
    public Condition build() {
        if (predicates.isEmpty()) return null;
        if (predicates.size() == 1) return predicates.get(0);
        return new AndCondition(new ArrayList<>(predicates));
    }
}
