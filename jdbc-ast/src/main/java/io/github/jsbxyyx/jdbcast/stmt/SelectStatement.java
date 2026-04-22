package io.github.jsbxyyx.jdbcast.stmt;

import io.github.jsbxyyx.jdbcast.LockMode;
import io.github.jsbxyyx.jdbcast.clause.CteDef;
import io.github.jsbxyyx.jdbcast.clause.JoinClause;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;
import io.github.jsbxyyx.jdbcast.clause.SetOp;
import io.github.jsbxyyx.jdbcast.clause.SetOpType;
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.expr.Expr;

import java.util.List;

/**
 * An immutable SELECT statement AST node.
 *
 * <p>Instances are created by {@link io.github.jsbxyyx.jdbcast.builder.SelectBuilder}
 * via {@link io.github.jsbxyyx.jdbcast.builder.SQL#from(TableRef)}.
 *
 * <p>Set-operation methods ({@link #union}, {@link #unionAll}, {@link #intersect},
 * {@link #intersectAll}, {@link #except}, {@link #exceptAll}) return a new
 * {@code SelectStatement} with the appropriate {@link SetOp} appended.
 */
public final class SelectStatement {

    private final List<CteDef>   with;
    private final boolean        distinct;
    private final List<Expr<?>>  select;
    private final TableRef<?>    from;
    private final List<JoinClause> joins;
    private final Condition      where;
    private final List<Expr<?>>  groupBy;
    private final Condition      having;
    private final List<OrderItem> orderBy;
    private final Long           limit;
    private final Long           offset;
    private final LockMode       lockMode;
    private final SetOp          setOp;

    public SelectStatement(List<CteDef> with,
                    boolean distinct,
                    List<Expr<?>> select,
                    TableRef<?> from,
                    List<JoinClause> joins,
                    Condition where,
                    List<Expr<?>> groupBy,
                    Condition having,
                    List<OrderItem> orderBy,
                    Long limit,
                    Long offset,
                    LockMode lockMode,
                    SetOp setOp) {
        this.with     = List.copyOf(with);
        this.distinct = distinct;
        this.select   = List.copyOf(select);
        this.from     = from;
        this.joins    = List.copyOf(joins);
        this.where    = where;
        this.groupBy  = List.copyOf(groupBy);
        this.having   = having;
        this.orderBy  = List.copyOf(orderBy);
        this.limit    = limit;
        this.offset   = offset;
        this.lockMode = lockMode;
        this.setOp    = setOp;
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    public List<CteDef>    with()     { return with; }
    public boolean         distinct() { return distinct; }
    public List<Expr<?>>   select()   { return select; }
    public TableRef<?>     from()     { return from; }
    public List<JoinClause> joins()   { return joins; }
    public Condition       where()    { return where; }
    public List<Expr<?>>   groupBy()  { return groupBy; }
    public Condition       having()   { return having; }
    public List<OrderItem> orderBy()  { return orderBy; }
    public Long            limit()    { return limit; }
    public Long            offset()   { return offset; }
    public LockMode        lockMode() { return lockMode; }
    public SetOp           setOp()    { return setOp; }

    // ------------------------------------------------------------------ //
    //  Set-operation combinators
    // ------------------------------------------------------------------ //

    public SelectStatement union(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.UNION, other));
    }

    public SelectStatement unionAll(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.UNION_ALL, other));
    }

    public SelectStatement intersect(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.INTERSECT, other));
    }

    public SelectStatement intersectAll(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.INTERSECT_ALL, other));
    }

    public SelectStatement except(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.EXCEPT, other));
    }

    public SelectStatement exceptAll(SelectStatement other) {
        return withSetOp(new SetOp(SetOpType.EXCEPT_ALL, other));
    }

    private SelectStatement withSetOp(SetOp op) {
        return new SelectStatement(with, distinct, select, from, joins,
                where, groupBy, having, orderBy, limit, offset, lockMode, op);
    }
}
