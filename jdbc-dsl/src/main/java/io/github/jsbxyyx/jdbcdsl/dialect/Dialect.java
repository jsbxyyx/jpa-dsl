package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * Strategy for appending database-specific pagination SQL.
 */
public interface Dialect {

    /**
     * Returns a new SQL string with pagination applied, and adds the pagination parameters
     * (offset, limit) to the provided params map.
     *
     * @param sql    the base SQL (already ordered if applicable)
     * @param offset zero-based row offset
     * @param limit  maximum number of rows to return
     * @param params the named-parameter map to add pagination params to
     * @return the SQL string with pagination appended
     */
    String applyPagination(String sql, long offset, int limit, Map<String, Object> params);
}
