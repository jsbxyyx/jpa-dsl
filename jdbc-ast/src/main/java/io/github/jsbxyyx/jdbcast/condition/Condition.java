package io.github.jsbxyyx.jdbcast.condition;

/**
 * Root of the SQL condition (predicate) AST.
 *
 * <p>Conditions are immutable value objects. Default methods provide a fluent API
 * for composing predicates with AND / OR / NOT.
 *
 * <p>Usage:
 * <pre>{@code
 * Condition c = u.col(TUser::getStatus).eq("ACTIVE")
 *                  .and(u.col(TUser::getAge).gte(18))
 *                  .or(u.col(TUser::getRole).eq("ADMIN"));
 * }</pre>
 */
public sealed interface Condition
        permits CompareCondition, AndCondition, OrCondition, NotCondition,
                InCondition, BetweenCondition, LikeCondition,
                NullCondition, ExistsCondition, RawCondition {

    /** Combines this condition with {@code other} using AND. */
    default Condition and(Condition other) {
        return new AndCondition(java.util.List.of(this, other));
    }

    /** Combines this condition with {@code other} using OR. */
    default Condition or(Condition other) {
        return new OrCondition(java.util.List.of(this, other));
    }

    /** Negates this condition: {@code NOT (this)}. */
    default Condition not() {
        return new NotCondition(this);
    }
}
