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
        Dialect.validatePagination(offset, limit);
        String limitParam = Dialect.uniquePaginationParamName("_limit", sql, params);
        String offsetParam = Dialect.uniquePaginationParamName("_offset", sql, params);
        params.put(limitParam, limit);
        params.put(offsetParam, offset);
        return sql + " LIMIT :" + limitParam + " OFFSET :" + offsetParam;
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta, LinkedHashMap<String, Object> colValues) {
        return renderOnConflict(spec, meta, colValues);
    }

    /**
     * Shared render logic for PostgreSQL: {@code INSERT … ON CONFLICT … DO UPDATE SET …}
     * or {@code INSERT … ON CONFLICT [(target)] DO NOTHING} when {@code spec.isDoNothing()}.
     */
    static RenderedSql renderOnConflict(UpsertSpec<?> spec, EntityMeta meta, LinkedHashMap<String, Object> colValues) {
        List<String> allCols = new ArrayList<>(colValues.keySet());
        List<String> conflictCols = spec.getConflictColumns();

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        Map<String, Object> params = new LinkedHashMap<>(colValues);
        for (String col : allCols) {
            colJoiner.add(col);
            valJoiner.add(":" + col);
        }

        String conflictTarget = conflictCols.isEmpty() ? "" : " (" + String.join(", ", conflictCols) + ")";

        String conflictAction;
        if (spec.isDoNothing()) {
            conflictAction = " DO NOTHING";
        } else {
            List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);
            StringJoiner updateJoiner = new StringJoiner(", ");
            for (String col : updateCols) {
                updateJoiner.add(col + " = EXCLUDED." + col);
            }
            conflictAction = " DO UPDATE SET " + updateJoiner;
        }

        String sql = "INSERT INTO " + meta.getTableName()
                + " (" + colJoiner + ") VALUES (" + valJoiner + ")"
                + " ON CONFLICT" + conflictTarget + conflictAction;
        return new RenderedSql(sql, params);
    }
}
