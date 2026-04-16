package io.github.jsbxyyx.jdbcdsl.dialect;

import java.util.Map;

/**
 * Oracle 11g (and earlier) dialect: uses the double-nested {@code ROWNUM} technique for
 * offset pagination, which is the only portable pagination mechanism available before
 * Oracle 12c.
 *
 * <p>The generated SQL wraps the original query in two layers:
 * <ol>
 *   <li>Inner subquery filters {@code ROWNUM <= offset + limit} (end row).</li>
 *   <li>Outer query filters {@code _rn_ > offset} (skip the leading rows).</li>
 * </ol>
 *
 * <p>Example for {@code offset=10, limit=5}:
 * <pre>{@code
 * SELECT * FROM (
 *   SELECT _q_.*, ROWNUM _rn_ FROM (
 *     SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC
 *   ) _q_ WHERE ROWNUM <= :_end
 * ) WHERE _rn_ > :_offset
 * }</pre>
 *
 * <p>For Oracle 12c+ use {@link OracleDialect} (cleaner, standard syntax).
 */
public final class Oracle11gDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        long end = offset + limit;
        params.put("_offset", offset);
        params.put("_end", end);
        return "SELECT * FROM ("
                + "SELECT _q_.*, ROWNUM _rn_ FROM (" + sql + ") _q_"
                + " WHERE ROWNUM <= :_end"
                + ") WHERE _rn_ > :_offset";
    }
}
