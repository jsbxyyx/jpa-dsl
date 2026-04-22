package io.github.jsbxyyx.jdbcast.condition;

import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

/** An EXISTS / NOT EXISTS predicate: {@code [NOT] EXISTS (subquery)}. */
public record ExistsCondition(SelectStatement subquery, boolean negated) implements Condition {

    public static ExistsCondition exists(SelectStatement subquery)    { return new ExistsCondition(subquery, false); }
    public static ExistsCondition notExists(SelectStatement subquery) { return new ExistsCondition(subquery, true);  }
}
