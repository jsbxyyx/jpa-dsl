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
 * Microsoft SQL Server dialect.
 *
 * <p>Pagination: SQL:2008 {@code OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY} syntax.
 *
 * <p>UPSERT: {@code MERGE INTO t AS t USING (SELECT :u_col, …) AS s(col, …) ON … WHEN … ;}
 * The MERGE statement is terminated with a semicolon as required by SQL Server.
 */
public final class SqlServerDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_offset", offset);
        params.put("_limit", limit);
        return sql + " OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY";
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta,
                                    LinkedHashMap<String, Object> colValues) {
        List<String> allCols = new ArrayList<>(colValues.keySet());
        List<String> conflictCols = spec.getConflictColumns();
        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);
        Map<String, Object> params = new LinkedHashMap<>();

        // USING (SELECT :u_col1, :u_col2, …) AS s (col1, col2, …)
        StringJoiner usingValJoiner = new StringJoiner(", ");
        StringJoiner usingAliasJoiner = new StringJoiner(", ");
        for (String col : allCols) {
            String paramName = "u_" + col;
            usingValJoiner.add(":" + paramName);
            usingAliasJoiner.add(col);
            params.put(paramName, colValues.get(col));
        }

        // ON t.k1 = s.k1 AND t.k2 = s.k2
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

        String sql = "MERGE INTO " + meta.getTableName() + " AS t"
                + " USING (SELECT " + usingValJoiner + ") AS s (" + usingAliasJoiner + ")"
                + " ON " + onJoiner
                + " WHEN MATCHED THEN UPDATE SET " + updateJoiner
                + " WHEN NOT MATCHED THEN INSERT (" + insColJoiner + ") VALUES (" + insValJoiner + ");";
        return new RenderedSql(sql, params);
    }
}
