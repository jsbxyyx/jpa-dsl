package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

/**
 * A set operation that appends to a SELECT statement:
 * {@code UNION | UNION ALL | INTERSECT | INTERSECT ALL | EXCEPT | EXCEPT ALL (right)}.
 */
public record SetOp(SetOpType type, SelectStatement right) {

    public static SetOp union(SelectStatement right)        { return new SetOp(SetOpType.UNION,         right); }
    public static SetOp unionAll(SelectStatement right)     { return new SetOp(SetOpType.UNION_ALL,     right); }
    public static SetOp intersect(SelectStatement right)    { return new SetOp(SetOpType.INTERSECT,     right); }
    public static SetOp intersectAll(SelectStatement right) { return new SetOp(SetOpType.INTERSECT_ALL, right); }
    public static SetOp except(SelectStatement right)       { return new SetOp(SetOpType.EXCEPT,        right); }
    public static SetOp exceptAll(SelectStatement right)    { return new SetOp(SetOpType.EXCEPT_ALL,    right); }
}
