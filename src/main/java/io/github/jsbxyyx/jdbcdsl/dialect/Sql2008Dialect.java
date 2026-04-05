package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * SQL:2008 standard dialect: appends {@code OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY}.
 */
public final class Sql2008Dialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_offset", offset);
        params.put("_limit", limit);
        return sql + " OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY";
    }
}
