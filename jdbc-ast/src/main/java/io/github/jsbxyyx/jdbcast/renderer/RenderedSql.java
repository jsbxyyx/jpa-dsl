package io.github.jsbxyyx.jdbcast.renderer;

import java.util.Map;

/**
 * The result of rendering an AST statement: a named-parameter SQL string and its bound values.
 *
 * @param sql    the SQL string with {@code :paramName} placeholders
 * @param params the named parameter values
 */
public record RenderedSql(String sql, Map<String, Object> params) {
}
