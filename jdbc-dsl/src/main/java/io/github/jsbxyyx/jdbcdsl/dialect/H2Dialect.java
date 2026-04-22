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
 * H2 dialect.
 *
 * <p>Pagination: MySQL-compatible {@code LIMIT :_limit OFFSET :_offset}
 *
 * <p>UPSERT: Standard SQL MERGE with a VALUES row constructor as the USING source:
 * <pre>{@code
 * MERGE INTO t_user t
 * USING (VALUES(:u_username, :u_email, :u_age, :u_status)) AS s(username, email, age, status)
 * ON (t.username = s.username)
 * WHEN MATCHED THEN UPDATE SET t.email = s.email, ...
 * WHEN NOT MATCHED THEN INSERT (username, ...) VALUES (s.username, ...)
 * }</pre>
 *
 * <p>H2's {@code ON CONFLICT … DO UPDATE SET} syntax requires {@code MODE=PostgreSQL}.
 * H2's {@code SELECT :param AS col} syntax in a MERGE USING clause is mis-parsed as a
 * CAST expression. Using {@code VALUES(...)} as the source row avoids both issues and
 * is valid SQL-standard MERGE syntax supported natively by H2 2.x.
 */
public final class H2Dialect implements Dialect {

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
        List<String> conflictCols = spec.getConflictColumns();
        List<String> updateCols = Dialect.resolveUpdateColumns(spec, allCols);
        Map<String, Object> params = new LinkedHashMap<>();

        // USING (VALUES(:u_col1, :u_col2, …)) AS s(col1, col2, …)
        // Using a VALUES row constructor avoids the CAST-parsing ambiguity in H2
        // that occurs with SELECT ? AS colname in MERGE USING clauses.
        StringJoiner valJoiner = new StringJoiner(", ");
        StringJoiner aliasJoiner = new StringJoiner(", ");
        for (String col : allCols) {
            String paramName = "u_" + col;
            valJoiner.add(":" + paramName);
            aliasJoiner.add(col);
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
                + " USING (VALUES(" + valJoiner + ")) AS s(" + aliasJoiner + ")"
                + " ON (" + onJoiner + ")"
                + matchedClause
                + " WHEN NOT MATCHED THEN INSERT (" + insColJoiner + ") VALUES (" + insValJoiner + ")";
        return new RenderedSql(sql, params);
    }
}
