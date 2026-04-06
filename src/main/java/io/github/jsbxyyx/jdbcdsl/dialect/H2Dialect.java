package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * H2 dialect: uses MySQL-compatible {@code LIMIT :_limit OFFSET :_offset} syntax.
 */
public final class H2Dialect implements Dialect {
    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }
}
