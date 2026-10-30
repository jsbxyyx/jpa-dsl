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
        Dialect.validatePagination(offset, limit);
        String offsetParam = Dialect.uniquePaginationParamName("_offset", sql, params);
        String limitParam = Dialect.uniquePaginationParamName("_limit", sql, params);
        params.put(offsetParam, offset);
        params.put(limitParam, limit);
        String paginatedSql = sql;
        if (!hasTopLevelOrderBy(sql)) {
            // SQL Server requires ORDER BY when OFFSET/FETCH is used. This fallback
            // makes unsorted pagination legal while leaving row order intentionally unspecified.
            paginatedSql += " ORDER BY (SELECT NULL)";
        }
        return paginatedSql + " OFFSET :" + offsetParam + " ROWS FETCH NEXT :" + limitParam + " ROWS ONLY";
    }

    /**
     * Checks for a top-level ORDER BY, ignoring nested queries, quoted text, and comments.
     */
    private static boolean hasTopLevelOrderBy(String sql) {
        int depth = 0;
        int length = sql.length();
        for (int i = 0; i < length; i++) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '[' || c == '`') {
                char closing = c == '[' ? ']' : c == '`' ? '`' : c;
                i++;
                while (i < length) {
                    char quoted = sql.charAt(i);
                    if (quoted == closing) {
                        if (i + 1 < length && sql.charAt(i + 1) == closing) {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    if (c == '\'' && quoted == '\\') {
                        i++;
                    }
                    i++;
                }
                continue;
            }
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < length && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < length && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && isWordAt(sql, i, "ORDER")) {
                int byStart = i + 5;
                while (byStart < length && Character.isWhitespace(sql.charAt(byStart))) {
                    byStart++;
                }
                if (isWordAt(sql, byStart, "BY")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWordAt(String sql, int offset, String word) {
        int end = offset + word.length();
        return offset >= 0
                && end <= sql.length()
                && sql.regionMatches(true, offset, word, 0, word.length())
                && (offset == 0 || !Character.isLetterOrDigit(sql.charAt(offset - 1)) && sql.charAt(offset - 1) != '_')
                && (end == sql.length() || !Character.isLetterOrDigit(sql.charAt(end)) && sql.charAt(end) != '_');
    }

    @Override
    public RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta, LinkedHashMap<String, Object> colValues) {
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

        String matchedClause = spec.isDoNothing() ? "" : " WHEN MATCHED THEN UPDATE SET " + updateJoiner;

        String sql = "MERGE INTO " + meta.getTableName() + " AS t"
                + " USING (SELECT " + usingValJoiner + ") AS s (" + usingAliasJoiner + ")"
                + " ON " + onJoiner
                + matchedClause
                + " WHEN NOT MATCHED THEN INSERT (" + insColJoiner + ") VALUES (" + insValJoiner + ");";
        return new RenderedSql(sql, params);
    }
}
