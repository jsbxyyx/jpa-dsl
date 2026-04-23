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
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }
}
