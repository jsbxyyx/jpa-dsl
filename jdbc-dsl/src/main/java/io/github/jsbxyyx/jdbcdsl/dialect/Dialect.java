package io.github.jsbxyyx.jdbcdsl.dialect;

import io.github.jsbxyyx.jdbcdsl.EntityMeta;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.UpsertSpec;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strategy for database-specific SQL generation.
 *
 * <p>Implementations cover pagination ({@link #applyPagination}) and
 * UPSERT / MERGE ({@link #renderUpsert}).
 */
public interface Dialect {

    /**
     * Returns a new SQL string with pagination applied, and adds the pagination parameters
     * (offset, limit) to the provided params map.
     *
     * @param sql    the base SQL (already ordered if applicable)
     * @param offset zero-based row offset
     * @param limit  maximum number of rows to return
     * @param params the named-parameter map to add pagination params to
     * @return the SQL string with pagination appended
     */
    String applyPagination(String sql, long offset, int limit, Map<String, Object> params);

    /**
     * Renders a dialect-specific UPSERT / MERGE statement.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}.
     * Concrete dialects override this method to produce syntax appropriate for their database:
     * <ul>
     *   <li>MySQL: {@code INSERT … ON DUPLICATE KEY UPDATE}</li>
     *   <li>PostgreSQL / H2: {@code INSERT … ON CONFLICT … DO UPDATE SET}</li>
     *   <li>Oracle: {@code MERGE INTO … USING (SELECT … FROM DUAL) …}</li>
     *   <li>SQL Server: {@code MERGE INTO … USING (SELECT …) AS s(…) …}</li>
     * </ul>
     *
     * @param spec      the upsert specification (conflict columns, optional update columns)
     * @param meta      entity metadata (table name, column mappings)
     * @param colValues ordered map of {@code columnName → value} for the full row
     * @return the rendered SQL and its named parameters
     * @throws UnsupportedOperationException if this dialect does not support UPSERT
     */
    default RenderedSql renderUpsert(UpsertSpec<?> spec, EntityMeta meta,
                                     LinkedHashMap<String, Object> colValues) {
        throw new UnsupportedOperationException(
                "UPSERT is not supported by dialect: " + getClass().getSimpleName());
    }

    /**
     * Resolves the effective list of columns to include in the UPDATE part of an UPSERT.
     *
     * <p>When {@code spec.getUpdateColumns()} is non-empty, those are returned as-is.
     * Otherwise all columns except the conflict-target columns are returned (the "update all"
     * default).
     *
     * @param spec    the upsert spec providing conflict and update column lists
     * @param allCols all column names that will be inserted
     * @return the columns to include in the UPDATE / SET clause
     */
    static List<String> resolveUpdateColumns(UpsertSpec<?> spec, List<String> allCols) {
        if (!spec.getUpdateColumns().isEmpty()) {
            return spec.getUpdateColumns();
        }
        Set<String> conflicts = new HashSet<>(spec.getConflictColumns());
        return allCols.stream().filter(c -> !conflicts.contains(c)).collect(Collectors.toList());
    }
}
