package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * SQL:2008 standard dialect: appends {@code OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY}.
 */
public final class Sql2008Dialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        Dialect.validatePagination(offset, limit);
        String offsetParam = Dialect.uniquePaginationParamName("_offset", sql, params);
        String limitParam = Dialect.uniquePaginationParamName("_limit", sql, params);
        params.put(offsetParam, offset);
        params.put(limitParam, limit);
        return sql + " OFFSET :" + offsetParam + " ROWS FETCH NEXT :" + limitParam + " ROWS ONLY";
    }
}
