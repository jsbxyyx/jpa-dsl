package io.github.jsbxyyx.jdbcast.condition;

import java.util.Map;

/**
 * A raw SQL predicate fragment used as an escape hatch.
 *
 * <p>Named parameters in {@code sql} (e.g., {@code :myParam}) must have matching entries
 * in {@code params}.
 */
public record RawCondition(String sql, Map<String, Object> params) implements Condition {

    public RawCondition {
        params = Map.copyOf(params);
    }

    public static RawCondition of(String sql) {
        return new RawCondition(sql, Map.of());
    }

    public static RawCondition of(String sql, Map<String, Object> params) {
        return new RawCondition(sql, params);
    }
}
