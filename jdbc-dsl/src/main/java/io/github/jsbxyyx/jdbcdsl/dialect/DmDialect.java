package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * 达梦数据库（DM）方言。
 *
 * <p>Pagination: {@code LIMIT :_limit OFFSET :_offset}
 *
 * <p>达梦 DM8 支持 MySQL 风格的 LIMIT/OFFSET 分页语法。
 */
public final class DmDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        Dialect.validatePagination(offset, limit);
        String limitParam = Dialect.uniquePaginationParamName("_limit", sql, params);
        String offsetParam = Dialect.uniquePaginationParamName("_offset", sql, params);
        params.put(limitParam, limit);
        params.put(offsetParam, offset);
        return sql + " LIMIT :" + limitParam + " OFFSET :" + offsetParam;
    }
}
