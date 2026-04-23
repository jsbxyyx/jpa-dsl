package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * 人大金仓（KingbaseES）方言。
 *
 * <p>Pagination: {@code LIMIT :_limit OFFSET :_offset}
 *
 * <p>KingbaseES 高度兼容 PostgreSQL，支持标准的 LIMIT/OFFSET 分页语法。
 */
public final class KingbaseDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }
}
