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
 * PostgreSQL dialect.
 *
 * <p>Pagination: {@code LIMIT :_limit OFFSET :_offset}
 *
 * <p>UPSERT: {@code INSERT INTO t (cols) VALUES (:cols) ON CONFLICT (key) DO UPDATE SET col = EXCLUDED.col, …}
 */
public final class PostgresDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta,
                                    LinkedHashMap<String, Object> colValues) {
        return renderOnConflict(spec, meta, colValues);
    }

    /**
     * Shared render logic for PostgreSQL / H2: {@code INSERT … ON CONFLICT … DO UPDATE SET …}
     */
    static RenderedSql renderOnConflict(UpsertSpec<?> spec, EntityMeta meta,
                                         LinkedHashMap<String, Object> colValues) {
        List<String> allCols = new ArrayList<>(colValues.keySet());
        List<String> conflictCols = spec.getConflictColumns();
        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        Map<String, Object> params = new LinkedHashMap<>(colValues);
        for (String col : allCols) {
            colJoiner.add(col);
            valJoiner.add(":" + col);
        }

        String conflictTarget = conflictCols.isEmpty()
                ? ""
                : " (" + String.join(", ", conflictCols) + ")";

        StringJoiner updateJoiner = new StringJoiner(", ");
        for (String col : updateCols) {
            updateJoiner.add(col + " = EXCLUDED." + col);
        }

        String sql = "INSERT INTO " + meta.getTableName()
                + " (" + colJoiner + ") VALUES (" + valJoiner + ")"
                + " ON CONFLICT" + conflictTarget + " DO UPDATE SET " + updateJoiner;
        return new RenderedSql(sql, params);
    }
}
