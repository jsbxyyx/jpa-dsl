package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.condition.Condition;

/**
 * A JOIN clause: {@code [INNER|LEFT|RIGHT|FULL OUTER|CROSS] JOIN table [AS alias] ON condition}.
 */
public record JoinClause(JoinType type, TableRef<?> table, Condition on) {

    /** CROSS JOIN has no ON condition; use {@code null} for {@code on}. */
    public static JoinClause inner(TableRef<?> table, Condition on) { return new JoinClause(JoinType.INNER, table, on); }
    public static JoinClause left(TableRef<?> table,  Condition on) { return new JoinClause(JoinType.LEFT,  table, on); }
    public static JoinClause right(TableRef<?> table, Condition on) { return new JoinClause(JoinType.RIGHT, table, on); }
    public static JoinClause full(TableRef<?> table,  Condition on) { return new JoinClause(JoinType.FULL,  table, on); }
    public static JoinClause cross(TableRef<?> table)               { return new JoinClause(JoinType.CROSS, table, null); }
}
