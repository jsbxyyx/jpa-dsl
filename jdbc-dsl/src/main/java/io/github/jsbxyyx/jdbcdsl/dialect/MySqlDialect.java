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
        Dialect.validatePagination(offset, limit);
        String limitParam = Dialect.uniquePaginationParamName("_limit", sql, params);
        String offsetParam = Dialect.uniquePaginationParamName("_offset", sql, params);
        params.put(limitParam, limit);
        params.put(offsetParam, offset);
        return sql + " LIMIT :" + limitParam + " OFFSET :" + offsetParam;
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta, LinkedHashMap<String, Object> colValues) {
        if (spec.isDoNothing()) {
            throw new UnsupportedOperationException(
                    "MySQL doNothing() is not supported: INSERT IGNORE can suppress non-duplicate errors");
        }
        List<String> allCols = new ArrayList<>(colValues.keySet());

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        Map<String, Object> params = new LinkedHashMap<>(colValues);
        for (String col : allCols) {
            colJoiner.add(col);
            valJoiner.add(":" + col);
        }

        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols, meta);
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
