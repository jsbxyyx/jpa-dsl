package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.ExistsCondition;
import io.github.jsbxyyx.jdbcast.condition.RawCondition;
import io.github.jsbxyyx.jdbcast.expr.AggExpr;
import io.github.jsbxyyx.jdbcast.expr.CastExpr;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.expr.FunctionExpr;
import io.github.jsbxyyx.jdbcast.expr.LiteralExpr;
import io.github.jsbxyyx.jdbcast.expr.RawExpr;
import io.github.jsbxyyx.jdbcast.expr.StarExpr;
import io.github.jsbxyyx.jdbcast.expr.SubqueryExpr;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

import java.util.Map;

/**
 * Static entry point for the JDBC-AST SQL DSL.
 *
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcast.builder.SQL.*;
 *
 * TableRef<TUser>  u = TableRef.of(TUser.class,  "u");
 * TableRef<TOrder> o = TableRef.of(TOrder.class, "o");
 *
 * SelectStatement q = from(u)
 *     .join(o).on(u.col(TUser::getId).eq(o.col(TOrder::getUserId)))
 *     .select(u.col(TUser::getUsername), sum(o.col(TOrder::getAmount)).as("total"))
 *     .where(u.col(TUser::getStatus).eq("ACTIVE"))
 *     .groupBy(u.col(TUser::getId), u.col(TUser::getUsername))
 *     .having(sum(o.col(TOrder::getAmount)).gt(100))
 *     .orderBy(u.col(TUser::getUsername).asc())
 *     .build();
 * }</pre>
 */
public final class SQL {

    private SQL() {}

    // ------------------------------------------------------------------ //
    //  Statement entry points
    // ------------------------------------------------------------------ //

    public static SelectBuilder from(TableRef<?> table) {
        return new SelectBuilder(table);
    }

    public static <T> InsertBuilder<T> insertInto(Class<T> entity) {
        return new InsertBuilder<>(entity);
    }

    public static <T> UpdateBuilder<T> update(Class<T> entity) {
        return new UpdateBuilder<>(entity);
    }

    public static <T> DeleteBuilder<T> deleteFrom(Class<T> entity) {
        return new DeleteBuilder<>(entity);
    }

    // ------------------------------------------------------------------ //
    //  Expression factories
    // ------------------------------------------------------------------ //

    /** {@code SELECT *} wildcard. */
    public static StarExpr star() { return StarExpr.ALL; }

    /** A literal value bound as a named parameter. */
    public static <V> LiteralExpr<V> val(V value) { return new LiteralExpr<>(value); }

    /** A raw SQL fragment (escape hatch). */
    public static <V> RawExpr<V> raw(String sql) { return RawExpr.of(sql); }
    public static <V> RawExpr<V> raw(String sql, Map<String, Object> params) { return RawExpr.of(sql, params); }

    /** {@code CAST(expr AS sqlType)} */
    public static <V> CastExpr<V> cast(Expr<?> expr, String sqlType) { return new CastExpr<>(expr, sqlType); }

    /** A scalar subquery expression: {@code (SELECT ...)}. */
    public static <V> SubqueryExpr<V> subquery(SelectStatement q) { return new SubqueryExpr<>(q); }

    // ------------------------------------------------------------------ //
    //  Aggregate functions
    // ------------------------------------------------------------------ //

    public static <V> AggExpr<V> count(Expr<V> expr) { return AggExpr.of("COUNT", expr); }
    public static AggExpr<Long>  countStar()          { return AggExpr.of("COUNT", StarExpr.ALL); }
    public static <V> AggExpr<V> sum(Expr<V> expr)    { return AggExpr.of("SUM",   expr); }
    public static <V> AggExpr<V> avg(Expr<V> expr)    { return AggExpr.of("AVG",   expr); }
    public static <V> AggExpr<V> min(Expr<V> expr)    { return AggExpr.of("MIN",   expr); }
    public static <V> AggExpr<V> max(Expr<V> expr)    { return AggExpr.of("MAX",   expr); }

    /** {@code COUNT(DISTINCT expr)} */
    public static <V> AggExpr<V> countDistinct(Expr<V> expr) { return AggExpr.distinct("COUNT", expr); }

    // ------------------------------------------------------------------ //
    //  Scalar / window functions
    // ------------------------------------------------------------------ //

    public static AggExpr<Long> rowNumber()          { return AggExpr.of("ROW_NUMBER"); }
    public static AggExpr<Long> rank()               { return AggExpr.of("RANK"); }
    public static AggExpr<Long> denseRank()          { return AggExpr.of("DENSE_RANK"); }
    public static <V> AggExpr<V> lag(Expr<V> expr, int offset)  { return AggExpr.of("LAG",  expr, val(offset)); }
    public static <V> AggExpr<V> lead(Expr<V> expr, int offset) { return AggExpr.of("LEAD", expr, val(offset)); }
    public static <V> AggExpr<V> firstValue(Expr<V> expr)       { return AggExpr.of("FIRST_VALUE", expr); }
    public static <V> AggExpr<V> lastValue(Expr<V> expr)        { return AggExpr.of("LAST_VALUE",  expr); }

    public static <V> FunctionExpr<V> fn(String name, Expr<?>... args) {
        return FunctionExpr.of(name, args);
    }

    // ------------------------------------------------------------------ //
    //  Condition factories
    // ------------------------------------------------------------------ //

    public static ExistsCondition exists(SelectStatement q)    { return ExistsCondition.exists(q);    }
    public static ExistsCondition notExists(SelectStatement q) { return ExistsCondition.notExists(q); }
    public static RawCondition    rawCond(String sql)          { return RawCondition.of(sql);          }
    public static RawCondition    rawCond(String sql, Map<String, Object> params) {
        return RawCondition.of(sql, params);
    }
}
