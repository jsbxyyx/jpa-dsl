package io.github.jsbxyyx.jdbcast.renderer;

import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;

/**
 * Converts SQL AST statement nodes into a rendered SQL string and bound parameters.
 *
 * <p>The primary implementation is {@link AnsiSqlRenderer}.
 * Dialect-specific subclasses override individual methods to handle differences
 * (e.g., pagination syntax, locking hints).
 */
public interface SqlRenderer {

    RenderedSql render(SelectStatement stmt);
    RenderedSql render(InsertStatement stmt);
    RenderedSql render(UpdateStatement stmt);
    RenderedSql render(DeleteStatement stmt);
}
