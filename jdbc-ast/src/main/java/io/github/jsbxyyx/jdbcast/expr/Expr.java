package io.github.jsbxyyx.jdbcast.expr;

import io.github.jsbxyyx.jdbcast.clause.OrderItem;
import io.github.jsbxyyx.jdbcast.condition.BetweenCondition;
import io.github.jsbxyyx.jdbcast.condition.CompareCondition;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.condition.InCondition;
import io.github.jsbxyyx.jdbcast.condition.LikeCondition;
import io.github.jsbxyyx.jdbcast.condition.NullCondition;
import io.github.jsbxyyx.jdbcast.condition.Op;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Root of the SQL expression AST.
 *
 * <p>All expressions are immutable value objects. Default methods on this interface
 * provide a fluent API for building comparisons, aliases, and ORDER BY items directly
 * from any expression node.
 *
 * <p>Usage example:
 * <pre>{@code
 * TableRef<TUser> u = TableRef.of(TUser.class, "u");
 *
 * SelectStatement stmt = SQL.from(u)
 *     .select(u.col(TUser::getId), u.col(TUser::getUsername).as("name"))
 *     .where(u.col(TUser::getStatus).eq("ACTIVE")
 *              .and(u.col(TUser::getAge).gte(18)))
 *     .orderBy(u.col(TUser::getAge).desc())
 *     .limit(10)
 *     .build();
 * }</pre>
 *
 * @param <V> the Java type produced by this expression
 */
public sealed interface Expr<V>
        permits ColExpr, LiteralExpr, FunctionExpr, AggExpr,
                WindowExpr, CaseExpr, CastExpr, SubqueryExpr,
                AliasedExpr, StarExpr, RawExpr {

    // ------------------------------------------------------------------ //
    //  Alias
    // ------------------------------------------------------------------ //

    /** Wraps this expression in an alias: {@code expr AS alias}. */
    default AliasedExpr<V> as(String alias) {
        return new AliasedExpr<>(this, alias);
    }

    // ------------------------------------------------------------------ //
    //  Ordering
    // ------------------------------------------------------------------ //

    /** Creates an ascending ORDER BY item for this expression. */
    default OrderItem asc() { return new OrderItem(this, true,  null); }

    /** Creates a descending ORDER BY item for this expression. */
    default OrderItem desc() { return new OrderItem(this, false, null); }

    // ------------------------------------------------------------------ //
    //  Equality / inequality
    // ------------------------------------------------------------------ //

    default Condition eq(Object value)  { return new CompareCondition(this, Op.EQ,  value); }
    default Condition ne(Object value)  { return new CompareCondition(this, Op.NE,  value); }
    default Condition lt(Object value)  { return new CompareCondition(this, Op.LT,  value); }
    default Condition lte(Object value) { return new CompareCondition(this, Op.LTE, value); }
    default Condition gt(Object value)  { return new CompareCondition(this, Op.GT,  value); }
    default Condition gte(Object value) { return new CompareCondition(this, Op.GTE, value); }

    // ------------------------------------------------------------------ //
    //  NULL checks
    // ------------------------------------------------------------------ //

    default Condition isNull()    { return new NullCondition(this, true);  }
    default Condition isNotNull() { return new NullCondition(this, false); }

    // ------------------------------------------------------------------ //
    //  IN
    // ------------------------------------------------------------------ //

    default Condition in(Collection<?> values)    { return new InCondition(this, false, List.copyOf(values)); }
    default Condition notIn(Collection<?> values) { return new InCondition(this, true,  List.copyOf(values)); }

    default Condition in(Object... values)    { return new InCondition(this, false, Arrays.asList(values)); }
    default Condition notIn(Object... values) { return new InCondition(this, true,  Arrays.asList(values)); }

    // ------------------------------------------------------------------ //
    //  BETWEEN
    // ------------------------------------------------------------------ //

    default Condition between(Object low, Object high)    { return new BetweenCondition(this, low, high, false); }
    default Condition notBetween(Object low, Object high) { return new BetweenCondition(this, low, high, true);  }

    // ------------------------------------------------------------------ //
    //  LIKE
    // ------------------------------------------------------------------ //

    default Condition like(String pattern)    { return new LikeCondition(this, pattern, false); }
    default Condition notLike(String pattern) { return new LikeCondition(this, pattern, true);  }
}
