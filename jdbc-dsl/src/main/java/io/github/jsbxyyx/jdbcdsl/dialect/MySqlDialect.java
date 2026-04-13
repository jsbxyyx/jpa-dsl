package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * MySQL dialect: appends {@code LIMIT :_limit OFFSET :_offset}.
 */
public final class MySqlDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }
}
