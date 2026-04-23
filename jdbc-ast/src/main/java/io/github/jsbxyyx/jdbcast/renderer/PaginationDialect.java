package io.github.jsbxyyx.jdbcast.renderer;

/**
 * SPI for rendering database-specific LIMIT / OFFSET (pagination) clauses.
 *
 * <p>Implement this interface to support any database not covered by the built-in dialects,
 * then pass the implementation to {@link AnsiSqlRenderer}:
 *
 * <pre>{@code
 * // Built-in: MySQL / PostgreSQL / H2 / SQLite / MariaDB
 * new AnsiSqlRenderer(meta, LimitOffsetDialect.INSTANCE);
 *
 * // Built-in: SQL Server / Oracle 12c+ / DB2
 * new AnsiSqlRenderer(meta, OffsetFetchDialect.INSTANCE);
 *
 * // Custom dialect
 * new AnsiSqlRenderer(meta, (sb, limit, offset, ctx) -> {
 *     if (offset != null) sb.append(" START AT ").append(ctx.addParam(offset + 1));
 *     if (limit  != null) sb.append(" FETCH FIRST ").append(ctx.addParam(limit)).append(" ROWS ONLY");
 * });
 * }</pre>
 *
 * <p>The method is only invoked when at least one of {@code limit} or {@code offset} is non-null.
 *
 * @see io.github.jsbxyyx.jdbcast.renderer.dialect.LimitOffsetDialect
 * @see io.github.jsbxyyx.jdbcast.renderer.dialect.OffsetFetchDialect
 */
@FunctionalInterface
public interface PaginationDialect {

    /**
     * Appends the pagination clause to {@code sb}.
     *
     * @param sb     the SQL buffer being built — append directly
     * @param limit  maximum number of rows, or {@code null} if no limit
     * @param offset number of rows to skip, or {@code null} if no offset
     * @param ctx    render context for registering named parameters (via {@link RenderContext#addParam})
     */
    void renderPage(StringBuilder sb, Long limit, Long offset, RenderContext ctx);
}
