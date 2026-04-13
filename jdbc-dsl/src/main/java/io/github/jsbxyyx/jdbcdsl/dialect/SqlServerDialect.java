package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * Microsoft SQL Server dialect: uses SQL:2008 {@code OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY} syntax.
 */
public final class SqlServerDialect implements Dialect {
    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_offset", offset);
        params.put("_limit", limit);
        return sql + " OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY";
    }
}
