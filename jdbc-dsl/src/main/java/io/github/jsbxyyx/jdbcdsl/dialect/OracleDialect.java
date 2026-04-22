package io.github.jsbxyyx.jdbcdsl.dialect;

import io.github.jsbxyyx.jdbcdsl.EntityMeta;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.UpsertSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Oracle 12c+ dialect.
 *
 * <p>Pagination: SQL:2008 {@code OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY} syntax
 * introduced in Oracle Database 12c Release 1 (12.1).
 *
 * <p>Example output:
 * <pre>{@code
 * SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC
 *   OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY
 * }</pre>
 *
 * <p>For Oracle 11g and earlier use {@link Oracle11gDialect}.
 *
 * <p>UPSERT: {@code MERGE INTO t USING (SELECT :u_col col,… FROM DUAL) s ON (…) WHEN MATCHED … WHEN NOT MATCHED …}
 */
public final class OracleDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_offset", offset);
        params.put("_limit", limit);
        return sql + " OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY";
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta,
                                    LinkedHashMap<String, Object> colValues) {
        return renderMergeViaDual(spec, meta, colValues);
    }

    /**
     * Shared MERGE-via-DUAL logic reused by {@link Oracle11gDialect}.
     *
     * <p>Parameters in the USING clause use a {@code u_} prefix (e.g. {@code :u_id})
     * to avoid any name collision with other named parameters in the statement.
     */
    static RenderedSql renderMergeViaDual(UpsertSpec<?> spec, EntityMeta meta,
                                           LinkedHashMap<String, Object> colValues) {
        List<String> allCols = new ArrayList<>(colValues.keySet());
        List<String> conflictCols = spec.getConflictColumns();
        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);
        Map<String, Object> params = new LinkedHashMap<>();

        // USING (SELECT :u_col1 col1, :u_col2 col2, … FROM DUAL) s
        StringJoiner usingJoiner = new StringJoiner(", ");
        for (String col : allCols) {
            String paramName = "u_" + col;
            usingJoiner.add(":" + paramName + " " + col);
            params.put(paramName, colValues.get(col));
        }

        // ON (t.k1 = s.k1 AND t.k2 = s.k2)
        StringJoiner onJoiner = new StringJoiner(" AND ");
        for (String col : conflictCols) {
            onJoiner.add("t." + col + " = s." + col);
        }

        // WHEN MATCHED THEN UPDATE SET t.col = s.col, …
        StringJoiner updateJoiner = new StringJoiner(", ");
        for (String col : updateCols) {
            updateJoiner.add("t." + col + " = s." + col);
        }

        // WHEN NOT MATCHED THEN INSERT (cols) VALUES (s.cols)
        StringJoiner insColJoiner = new StringJoiner(", ");
        StringJoiner insValJoiner = new StringJoiner(", ");
        for (String col : allCols) {
            insColJoiner.add(col);
            insValJoiner.add("s." + col);
        }

        String matchedClause = spec.isDoNothing()
                ? ""
                : " WHEN MATCHED THEN UPDATE SET " + updateJoiner;

        String sql = "MERGE INTO " + meta.getTableName() + " t"
                + " USING (SELECT " + usingJoiner + " FROM DUAL) s"
                + " ON (" + onJoiner + ")"
                + matchedClause
                + " WHEN NOT MATCHED THEN INSERT (" + insColJoiner + ") VALUES (" + insValJoiner + ")";
        return new RenderedSql(sql, params);
    }
}
