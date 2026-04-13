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
        this.sql = sql;
        // Use LinkedHashMap copy to preserve insertion order and allow null values
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
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
