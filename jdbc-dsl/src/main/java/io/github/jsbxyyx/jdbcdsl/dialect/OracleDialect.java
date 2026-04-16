package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * Oracle 12c+ dialect: uses the SQL:2008 {@code OFFSET ... ROWS FETCH NEXT ... ROWS ONLY} syntax
 * introduced in Oracle Database 12c Release 1 (12.1).
 *
 * <p>Example output:
 * <pre>{@code
 * SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC
 *   OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY
 * }</pre>
 *
 * <p>For Oracle 11g and earlier use {@link Oracle11gDialect}.
 */
public final class OracleDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_offset", offset);
        params.put("_limit", limit);
        return sql + " OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY";
    }
}
