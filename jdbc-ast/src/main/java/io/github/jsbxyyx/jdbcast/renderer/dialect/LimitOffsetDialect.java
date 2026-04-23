package io.github.jsbxyyx.jdbcast.renderer.dialect;

import io.github.jsbxyyx.jdbcast.renderer.PaginationDialect;
import io.github.jsbxyyx.jdbcast.renderer.RenderContext;

/**
 * Pagination dialect using standard {@code LIMIT :n OFFSET :m} syntax.
 *
 * <p>Supported databases: <strong>MySQL, MariaDB, PostgreSQL, H2, SQLite</strong>.
 *
 * <pre>{@code
 * // Generated SQL:
 * // SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT :p1 OFFSET :p2
 * }</pre>
 *
 * <p>This is the default dialect used by {@link io.github.jsbxyyx.jdbcast.renderer.AnsiSqlRenderer}
 * when no explicit dialect is provided.
 */
public final class LimitOffsetDialect implements PaginationDialect {

    /** Shared singleton — stateless and thread-safe. */
    public static final LimitOffsetDialect INSTANCE = new LimitOffsetDialect();

    private LimitOffsetDialect() {}

    @Override
    public void renderPage(StringBuilder sb, Long limit, Long offset, RenderContext ctx) {
        if (limit  != null) sb.append(" LIMIT " ).append(ctx.addParam(limit));
        if (offset != null) sb.append(" OFFSET ").append(ctx.addParam(offset));
    }
}
