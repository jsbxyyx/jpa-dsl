package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.LockMode;
import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.clause.CteDef;
import io.github.jsbxyyx.jdbcast.clause.JoinClause;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link SelectStatement}.
 *
 * <p>Obtain an instance via {@link SQL#from(TableRef)}.
 *
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
 */
public final class SelectBuilder {

    private final TableRef<?>        from;
    private final List<CteDef>       with     = new ArrayList<>();
    private boolean                  distinct = false;
    private final List<Expr<?>>      select   = new ArrayList<>();
    private final List<JoinClause>   joins    = new ArrayList<>();
    private Condition                where    = null;
    private final List<Expr<?>>      groupBy  = new ArrayList<>();
    private Condition                having   = null;
    private final List<OrderItem>    orderBy  = new ArrayList<>();
    private Long                     limit    = null;
    private Long                     offset   = null;
    private LockMode                 lockMode = null;

    SelectBuilder(TableRef<?> from) {
        this.from = from;
    }

    // ------------------------------------------------------------------ //
    //  WITH (CTE)
    // ------------------------------------------------------------------ //

    public SelectBuilder with(CteDef... cteDefs) {
        with.addAll(Arrays.asList(cteDefs));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  DISTINCT
    // ------------------------------------------------------------------ //

    public SelectBuilder distinct() {
        this.distinct = true;
        return this;
    }

    // ------------------------------------------------------------------ //
    //  SELECT list
    // ------------------------------------------------------------------ //

    public SelectBuilder select(Expr<?>... exprs) {
        select.addAll(Arrays.asList(exprs));
        return this;
    }

    public SelectBuilder select(List<? extends Expr<?>> exprs) {
        select.addAll(exprs);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  JOINs
    // ------------------------------------------------------------------ //

    public JoinBuilder join(TableRef<?> table) {
        return new JoinBuilder(this, table, io.github.jsbxyyx.jdbcast.clause.JoinType.INNER);
    }

    public JoinBuilder leftJoin(TableRef<?> table) {
        return new JoinBuilder(this, table, io.github.jsbxyyx.jdbcast.clause.JoinType.LEFT);
    }

    public JoinBuilder rightJoin(TableRef<?> table) {
        return new JoinBuilder(this, table, io.github.jsbxyyx.jdbcast.clause.JoinType.RIGHT);
    }

    public JoinBuilder fullJoin(TableRef<?> table) {
        return new JoinBuilder(this, table, io.github.jsbxyyx.jdbcast.clause.JoinType.FULL);
    }

    public SelectBuilder crossJoin(TableRef<?> table) {
        joins.add(JoinClause.cross(table));
        return this;
    }

    // package-private: called by JoinBuilder
    void addJoin(JoinClause join) {
        joins.add(join);
    }

    // ------------------------------------------------------------------ //
    //  WHERE
    // ------------------------------------------------------------------ //

    public SelectBuilder where(Condition condition) {
        this.where = condition;
        return this;
    }

    /** Lambda-style WHERE builder — accumulated predicates are AND-combined. */
    public SelectBuilder where(Consumer<ConditionBuilder> builder) {
        ConditionBuilder cb = new ConditionBuilder();
        builder.accept(cb);
        Condition c = cb.build();
        if (c != null) this.where = c;
        return this;
    }

    /** Appends to existing WHERE with AND (convenience for dynamic query building). */
    public SelectBuilder andWhere(Condition condition) {
        this.where = (this.where == null) ? condition : this.where.and(condition);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  GROUP BY / HAVING
    // ------------------------------------------------------------------ //

    public SelectBuilder groupBy(Expr<?>... exprs) {
        groupBy.addAll(Arrays.asList(exprs));
        return this;
    }

    public SelectBuilder having(Condition condition) {
        this.having = condition;
        return this;
    }

    /** Lambda-style HAVING builder — accumulated predicates are AND-combined. */
    public SelectBuilder having(Consumer<ConditionBuilder> builder) {
        ConditionBuilder cb = new ConditionBuilder();
        builder.accept(cb);
        Condition c = cb.build();
        if (c != null) this.having = c;
        return this;
    }

    // ------------------------------------------------------------------ //
    //  ORDER BY
    // ------------------------------------------------------------------ //

    public SelectBuilder orderBy(OrderItem... items) {
        orderBy.addAll(Arrays.asList(items));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  LIMIT / OFFSET
    // ------------------------------------------------------------------ //

    public SelectBuilder limit(long limit) {
        this.limit = limit;
        return this;
    }

    public SelectBuilder offset(long offset) {
        this.offset = offset;
        return this;
    }

    public SelectBuilder page(long pageNumber, int pageSize) {
        this.limit  = (long) pageSize;
        this.offset = pageNumber * pageSize;
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Locking
    // ------------------------------------------------------------------ //

    public SelectBuilder forUpdate() {
        return forUpdate(LockMode.UPDATE);
    }

    public SelectBuilder forUpdate(LockMode mode) {
        this.lockMode = mode;
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    public SelectStatement build() {
        return new SelectStatement(with, distinct, select, from,
                joins, where, groupBy, having, orderBy, limit, offset, lockMode, null);
    }
}
