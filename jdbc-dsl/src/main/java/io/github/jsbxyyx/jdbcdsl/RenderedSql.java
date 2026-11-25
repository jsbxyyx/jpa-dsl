package io.github.jsbxyyx.jdbcdsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of SQL rendering: a parameterized SQL string and its named parameters.
 *
 * <p>Parameter values may be {@code null} (e.g. when a WHERE predicate is added with
 * {@code condition=true} and a {@code null} value).
 */
public final class RenderedSql {

    private final String sql;
    private final Map<String, Object> params;

    public RenderedSql(String sql, Map<String, Object> params) {
        this(sql, params, false);
    }

    private RenderedSql(String sql, Map<String, Object> params, boolean takeOwnership) {
        this.sql = sql;
        // Preserve the public defensive-copy contract; internal renderers may transfer ownership.
        this.params = takeOwnership
                ? Collections.unmodifiableMap(params)
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * Creates a result by taking ownership of a renderer-local parameter map.
     *
     * <p>The caller must not retain or mutate the map after this call. Public construction
     * remains defensive and continues to copy caller-owned maps.
     */
    static RenderedSql takeOwnership(String sql, Map<String, Object> params) {
        return new RenderedSql(sql, params, true);
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "RenderedSql{sql='" + sql + "', params=" + params + '}';
    }
}
