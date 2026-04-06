package io.github.jsbxyyx.jdbcdsl;

import java.util.Map;

/**
 * The result of SQL rendering: a parameterized SQL string and its named parameters.
 */
public final class RenderedSql {

    private final String sql;
    private final Map<String, Object> params;

    public RenderedSql(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = Map.copyOf(params);
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
