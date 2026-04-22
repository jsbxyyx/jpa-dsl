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
 * MySQL dialect.
 *
 * <p>Pagination: {@code LIMIT :_limit OFFSET :_offset}
 *
 * <p>UPSERT: {@code INSERT INTO t (cols) VALUES (:cols) ON DUPLICATE KEY UPDATE col = VALUES(col), …}
 * MySQL infers the conflict from the table's unique indexes; conflict columns are not emitted in SQL.
 */
public final class MySqlDialect implements Dialect {

    @Override
    public String applyPagination(String sql, long offset, int limit, Map<String, Object> params) {
        params.put("_limit", limit);
        params.put("_offset", offset);
        return sql + " LIMIT :_limit OFFSET :_offset";
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta,
                                    LinkedHashMap<String, Object> colValues) {
        List<String> allCols = new ArrayList<>(colValues.keySet());

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        Map<String, Object> params = new LinkedHashMap<>(colValues);
        for (String col : allCols) {
            colJoiner.add(col);
            valJoiner.add(":" + col);
        }

        // DO NOTHING: MySQL uses INSERT IGNORE to silently skip duplicate rows.
        if (spec.isDoNothing()) {
            String sql = "INSERT IGNORE INTO " + meta.getTableName()
                    + " (" + colJoiner + ") VALUES (" + valJoiner + ")";
            return new RenderedSql(sql, params);
        }

        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);
        StringJoiner updateJoiner = new StringJoiner(", ");
        for (String col : updateCols) {
            updateJoiner.add(col + " = VALUES(" + col + ")");
        }

        String sql = "INSERT INTO " + meta.getTableName()
                + " (" + colJoiner + ") VALUES (" + valJoiner + ")"
                + " ON DUPLICATE KEY UPDATE " + updateJoiner;
        return new RenderedSql(sql, params);
    }
}
