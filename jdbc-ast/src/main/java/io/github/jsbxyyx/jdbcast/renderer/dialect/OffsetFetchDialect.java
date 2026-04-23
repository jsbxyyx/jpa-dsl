package io.github.jsbxyyx.jdbcast.renderer.dialect;

import io.github.jsbxyyx.jdbcast.renderer.PaginationDialect;
import io.github.jsbxyyx.jdbcast.renderer.RenderContext;

/**
 * Pagination dialect using SQL:2008 {@code OFFSET … ROWS FETCH NEXT … ROWS ONLY} syntax.
 *
 * <p>Supported databases: <strong>SQL Server 2012+, Oracle 12c+, DB2, H2 (compatibility mode)</strong>.
 *
 * <pre>{@code
 * // Generated SQL (both limit and offset):
 * // SELECT ... FROM ... ORDER BY ... OFFSET :p1 ROWS FETCH NEXT :p2 ROWS ONLY
 *
 * // Generated SQL (limit only, offset defaults to 0):
 * // SELECT ... FROM ... ORDER BY ... OFFSET 0 ROWS FETCH NEXT :p1 ROWS ONLY
 *
 * // Generated SQL (offset only, no row cap):
 * // SELECT ... FROM ... ORDER BY ... OFFSET :p1 ROWS
 * }</pre>
 *
 * <p><strong>Note:</strong> SQL Server requires an {@code ORDER BY} clause when using
 * {@code OFFSET / FETCH}. Ensure your query includes {@code .orderBy(...)} when using
 * this dialect with SQL Server.
 */
public final class OffsetFetchDialect implements PaginationDialect {

    /** Shared singleton — stateless and thread-safe. */
    public static final OffsetFetchDialect INSTANCE = new OffsetFetchDialect();

    private OffsetFetchDialect() {}

    @Override
    public void renderPage(StringBuilder sb, Long limit, Long offset, RenderContext ctx) {
        if (limit != null && offset == null) {
            // FETCH NEXT only — OFFSET 0 ROWS is required by SQL Server / Oracle before FETCH
            sb.append(" OFFSET 0 ROWS FETCH NEXT ").append(ctx.addParam(limit)).append(" ROWS ONLY");
        } else if (limit == null && offset != null) {
            // OFFSET only — skip rows without a row cap
            sb.append(" OFFSET ").append(ctx.addParam(offset)).append(" ROWS");
        } else if (limit != null) {
            // Both limit and offset
            sb.append(" OFFSET ").append(ctx.addParam(offset))
              .append(" ROWS FETCH NEXT ").append(ctx.addParam(limit)).append(" ROWS ONLY");
        }
    }
}
