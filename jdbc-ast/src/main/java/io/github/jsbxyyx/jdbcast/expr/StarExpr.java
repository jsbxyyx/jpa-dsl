package io.github.jsbxyyx.jdbcast.expr;

/**
 * The wildcard {@code *} or {@code tableAlias.*} in a SELECT list.
 */
public record StarExpr(String tableAlias) implements Expr<Object> {

    /** {@code SELECT *} */
    public static final StarExpr ALL = new StarExpr(null);

    /** {@code SELECT tableAlias.*} */
    public static StarExpr of(String tableAlias) {
        return new StarExpr(tableAlias);
    }
}
