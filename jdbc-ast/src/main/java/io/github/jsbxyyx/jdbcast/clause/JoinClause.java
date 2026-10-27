package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.condition.Condition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JOIN clause: {@code [INNER|LEFT|RIGHT|FULL OUTER|CROSS] JOIN table [AS alias] ON condition}.
 */
public record JoinClause(
        JoinType type, TableRef<?> table, Condition on, String tableSqlOverride, Map<String, Object> tableParams) {

    public JoinClause(JoinType type, TableRef<?> table, Condition on) {
        this(type, table, on, null, Map.of());
    }

    public JoinClause {
        tableParams = Collections.unmodifiableMap(new LinkedHashMap<>(tableParams));
    }

    /** CROSS JOIN has no ON condition; use {@code null} for {@code on}. */
    public static JoinClause inner(TableRef<?> table, Condition on) {
        return new JoinClause(JoinType.INNER, table, on);
    }

    public static JoinClause left(TableRef<?> table, Condition on) {
        return new JoinClause(JoinType.LEFT, table, on);
    }

    public static JoinClause right(TableRef<?> table, Condition on) {
        return new JoinClause(JoinType.RIGHT, table, on);
    }

    public static JoinClause full(TableRef<?> table, Condition on) {
        return new JoinClause(JoinType.FULL, table, on);
    }

    public static JoinClause cross(TableRef<?> table) {
        return new JoinClause(JoinType.CROSS, table, null);
    }
}
