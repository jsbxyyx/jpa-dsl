package io.github.jsbxyyx.jdbcast.expr;

import java.util.Map;

/**
 * A raw SQL fragment used as an escape hatch for expressions the DSL does not yet support.
 *
 * <p>Named parameters in the SQL string (e.g., {@code :myParam}) must have matching entries
 * in the {@code params} map.
 *
 * @param <V> the (nominal) Java return type
 */
public record RawExpr<V>(String sql, Map<String, Object> params) implements Expr<V> {

    public RawExpr {
        params = Map.copyOf(params);
    }

    public static <V> RawExpr<V> of(String sql) {
        return new RawExpr<>(sql, Map.of());
    }

    public static <V> RawExpr<V> of(String sql, Map<String, Object> params) {
        return new RawExpr<>(sql, params);
    }
}
