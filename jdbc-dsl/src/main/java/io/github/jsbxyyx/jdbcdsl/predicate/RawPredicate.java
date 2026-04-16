package io.github.jsbxyyx.jdbcdsl.predicate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A raw SQL predicate fragment embedded verbatim into the WHERE clause.
 *
 * <p>This is the "escape hatch" for conditions that cannot be expressed through the
 * type-safe DSL API — for example, database-specific functions, complex CASE expressions,
 * or cross-column comparisons.
 *
 * <p>Named parameters in {@code sql} (e.g. {@code :year}) are merged directly into the
 * query's parameter map. Parameter names must not match the auto-generated DSL names
 * ({@code p1}, {@code p2}, …) to avoid collisions.
 *
 * <p><strong>Warning:</strong> never pass user-controlled data in {@code sql}.
 * Bind user input through {@code params} as named parameters.
 *
 * <p>Example:
 * <pre>{@code
 * // Simple raw condition (no params)
 * .where(w -> w.raw("t.age > t.min_age"))
 *
 * // Raw condition with named parameters
 * .where(w -> w.raw("YEAR(t.created_at) = :yr", Map.of("yr", 2024)))
 * }</pre>
 */
public final class RawPredicate implements PredicateNode {

    private final String sql;
    private final Map<String, Object> params;

    public RawPredicate(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /** Returns the raw SQL fragment to embed verbatim in the WHERE clause. */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the named parameters to merge into the outer query's parameter map.
     * Empty when the raw condition needs no bound values.
     */
    public Map<String, Object> getParams() {
        return params;
    }
}
