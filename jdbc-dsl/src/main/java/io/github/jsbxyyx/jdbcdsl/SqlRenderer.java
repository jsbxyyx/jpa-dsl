package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.AliasedExpression;
import io.github.jsbxyyx.jdbcdsl.expr.CaseExpression;
import io.github.jsbxyyx.jdbcdsl.expr.CastExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.expr.LiteralExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ScalarSubqueryExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.expr.WindowExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.ExistsPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.InSubqueryPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import io.github.jsbxyyx.jdbcdsl.predicate.RawPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.ScalarSubqueryPredicate;
import io.github.jsbxyyx.jdbcdsl.OnBuilder.OnEqPredicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders a {@link SelectSpec} into a parameterized {@link RenderedSql}.
 *
 * <p>SELECT clause alias strategy:
 * <ul>
 *   <li>When no {@code select(...)} expressions are specified, all entity columns are expanded
 *       as {@code alias.column AS propertyName} in entity declaration order (which mirrors
 *       database column ordinal order for generated entities).</li>
 *   <li>{@link ColumnExpression}: rendered as {@code alias.column AS propertyName}.</li>
 *   <li>{@link AliasedExpression}: rendered as {@code <inner> AS <alias>} using the explicit alias.</li>
 *   <li>Function / aggregate / literal expressions: rendered without alias (follow JDBC column label).</li>
 * </ul>
 *
 * <p>Named parameters are {@code :p1, :p2, ...}.
 */
public final class SqlRenderer {

    private SqlRenderer() {
    }

    /**
     * Renders a {@link UnionSpec} into a parameterized {@link RenderedSql}.
     *
     * <p>Each branch is rendered as a full SELECT (without ORDER BY), joined by
     * {@code UNION} or {@code UNION ALL} operators. When the spec carries an ORDER BY sort,
     * the combined result is appended with an {@code ORDER BY} clause whose column references
     * are rendered without a table-alias prefix (matching the output column aliases).
     */
    public static <R> RenderedSql renderUnion(UnionSpec<R> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        List<UnionSpec.Branch<R>> branches = spec.getBranches();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < branches.size(); i++) {
            UnionSpec.Branch<R> branch = branches.get(i);
            if (i > 0) {
                String op = switch (branch.unionType()) {
                    case UNION_ALL -> " UNION ALL ";
                    case INTERSECT -> " INTERSECT ";
                    case INTERSECT_ALL -> " INTERSECT ALL ";
                    case EXCEPT -> " EXCEPT ";
                    case EXCEPT_ALL -> " EXCEPT ALL ";
                    default -> " UNION ";
                };
                sb.append(op);
            }
            sb.append(buildSelectSqlUnchecked(branch.spec(), params, paramIdx, false));
        }

        // Append ORDER BY on the combined union result when a sort is specified.
        JSort<?> sort = spec.getSort();
        if (!sort.isEmpty()) {
            sb.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (JOrder<?> order : sort.getOrders()) {
                String colExpr = renderOrderExpressionUnqualified(order.getExpression(), params, paramIdx);
                String nullHandling = switch (order.getNullHandling()) {
                    case NULLS_FIRST -> " NULLS FIRST";
                    case NULLS_LAST -> " NULLS LAST";
                    case NATIVE -> "";
                };
                orderJoiner.add(colExpr + " " + order.getDirection().name() + nullHandling);
            }
            sb.append(orderJoiner);
        }

        return new RenderedSql(sb.toString(), params);
    }

    /**
     * Renders a {@link JOrder}'s expression without a table-alias prefix.
     *
     * <p>Used for ORDER BY on UNION results, where column references must be bare names
     * (or their output aliases) rather than {@code alias.column_name}.
     */
    private static String renderOrderExpressionUnqualified(SqlExpression<?> expression,
                                                            Map<String, Object> params,
                                                            AtomicInteger paramIdx) {
        if (expression instanceof ColumnExpression<?> col) {
            // Resolve to column name only (no table alias — ORDER BY column alias on UNION)
            PropertyRef pr = col.getPropertyRef();
            EntityMeta meta = EntityMetaReader.read(pr.ownerClass());
            String colName = meta.getColumnName(pr.propertyName());
            return colName != null ? colName : pr.propertyName();
        } else if (expression instanceof LiteralExpression<?> lit) {
            return lit.getSql();
        } else if (expression instanceof FunctionExpression<?> fn) {
            StringJoiner argJoiner = new StringJoiner(", ");
            for (SqlExpression<?> arg : fn.getArgs()) {
                argJoiner.add(renderOrderExpressionUnqualified(arg, params, paramIdx));
            }
            return fn.getFunctionName() + "(" + argJoiner + ")";
        } else if (expression instanceof AliasedExpression<?> aliased) {
            return renderOrderExpressionUnqualified(aliased.getInner(), params, paramIdx);
        } else {
            // Fallback for other expression types
            return expression.toString();
        }
    }

    /**
     * Renders the SELECT query for a {@link SelectSpec}.
     */
    public static <T, R> RenderedSql renderSelect(SelectSpec<T, R> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);
        return new RenderedSql(buildSelectSql(spec, params, paramIdx, true), params);
    }

    /**
     * Builds SELECT SQL sharing the given {@code params} map and {@code paramIdx} counter.
     * Used internally so that subquery parameters are folded into the outer query's
     * parameter map with globally unique names.
     *
     * @param includeOrderBy {@code false} when rendering a subquery (ORDER BY is not valid
     *                       inside a subquery in SQL-92)
     */
    static <T, R> String buildSelectSql(SelectSpec<T, R> spec,
                                         Map<String, Object> params,
                                         AtomicInteger paramIdx,
                                         boolean includeOrderBy) {
        EntityMeta rootMeta = EntityMetaReader.read(spec.getEntityClass());
        String alias = spec.getAlias();

        StringBuilder sb = new StringBuilder();

        // WITH clause (CTE preamble)
        List<CteDef> cteDefs = spec.getCteDefs();
        if (!cteDefs.isEmpty()) {
            sb.append("WITH ");
            StringJoiner cteJoiner = new StringJoiner(", ");
            for (CteDef cte : cteDefs) {
                String body = buildSelectSqlUnchecked(cte.body(), params, paramIdx, false);
                cteJoiner.add(cte.name() + " AS (" + body + ")");
            }
            sb.append(cteJoiner).append(" ");
        }

        // SELECT clause
        sb.append(spec.isDistinct() ? "SELECT DISTINCT " : "SELECT ");
        List<SqlExpression<?>> exprs = spec.getSelectedExpressions();
        if (exprs.isEmpty()) {
            EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
            StringJoiner cols = new StringJoiner(", ");
            meta.getPropertyToColumn().entrySet()
                    .forEach(e -> cols.add(alias + "." + e.getValue() + " AS " + e.getKey()));
            sb.append(cols);
        } else {
            StringJoiner cols = new StringJoiner(", ");
            for (SqlExpression<?> expr : exprs) {
                cols.add(renderSelectItem(expr, spec, params, paramIdx));
            }
            sb.append(cols);
        }

        // FROM clause
        // Priority: subqueryFrom > tableNameOverride > entity table name
        String fromTarget;
        if (spec.getSubqueryFrom() != null) {
            String innerSql = buildSelectSqlUnchecked(spec.getSubqueryFrom(), params, paramIdx, false);
            fromTarget = "(" + innerSql + ")";
        } else if (spec.getTableNameOverride() != null) {
            fromTarget = spec.getTableNameOverride();
        } else {
            fromTarget = rootMeta.getTableName();
        }
        sb.append(" FROM ").append(fromTarget).append(" ").append(alias);

        // JOIN clauses
        appendJoinClauses(sb, spec, params, paramIdx);

        // WHERE clause
        appendWhereClause(sb, spec, params, paramIdx);

        // GROUP BY clause
        appendGroupByClause(sb, spec, params, paramIdx);

        // HAVING clause
        appendHavingClause(sb, spec, params, paramIdx);

        // ORDER BY clause (omitted inside subqueries)
        if (includeOrderBy) {
            JSort<T> sort = spec.getSort();
            if (!sort.isEmpty()) {
                sb.append(" ORDER BY ");
                StringJoiner orderJoiner = new StringJoiner(", ");
                for (JOrder<T> order : sort.getOrders()) {
                    String colExpr;
                    if (order.isIgnoreCase() && order.getExpression() instanceof ColumnExpression<?> colExprNode) {
                        String rendered = renderColumnExpression(colExprNode, spec);
                        colExpr = "LOWER(" + rendered + ")";
                    } else {
                        colExpr = renderExpression(order.getExpression(), spec, params, paramIdx);
                    }
                    String nullHandling = switch (order.getNullHandling()) {
                        case NULLS_FIRST -> " NULLS FIRST";
                        case NULLS_LAST -> " NULLS LAST";
                        case NATIVE -> "";
                    };
                    orderJoiner.add(colExpr + " " + order.getDirection().name() + nullHandling);
                }
                sb.append(orderJoiner);
            }
            // Locking clause is a top-level SELECT modifier; never emitted inside subqueries.
            if (spec.getLockMode() != null) {
                String lockSql = switch (spec.getLockMode()) {
                    case UPDATE -> " FOR UPDATE";
                    case UPDATE_NOWAIT -> " FOR UPDATE NOWAIT";
                    case UPDATE_SKIP_LOCKED -> " FOR UPDATE SKIP LOCKED";
                    case SHARE -> " FOR SHARE";
                };
                sb.append(lockSql);
            }
        }

        return sb.toString();
    }

    /**
     * Renders a subquery {@link SelectSpec} inline, sharing the outer query's parameter map and
     * counter so that all named parameters are globally unique (e.g. {@code :p1, :p2, ...}).
     * ORDER BY is intentionally omitted — it is not valid inside a SQL-92 subquery.
     */
    @SuppressWarnings("unchecked")
    static String renderSubquerySql(SelectSpec<?, ?> spec,
                                     Map<String, Object> params,
                                     AtomicInteger paramIdx) {
        return buildSelectSql((SelectSpec<Object, Object>) spec, params, paramIdx, false);
    }

    @SuppressWarnings("unchecked")
    private static String buildSelectSqlUnchecked(SelectSpec<?, ?> spec,
                                                   Map<String, Object> params,
                                                   AtomicInteger paramIdx,
                                                   boolean includeOrderBy) {
        return buildSelectSql((SelectSpec<Object, Object>) spec, params, paramIdx, includeOrderBy);
    }

    /**
     * Renders a COUNT query for the given spec (no ORDER BY, no pagination).
     *
     * <ul>
     *   <li>No JOINs, no GROUP BY → {@code SELECT COUNT(*) FROM table WHERE ...}</li>
     *   <li>JOINs present (no GROUP BY) → {@code SELECT COUNT(DISTINCT alias.pk) FROM ... JOIN ... WHERE ...}
     *       to avoid counting duplicate rows produced by one-to-many joins.</li>
     *   <li>GROUP BY present → {@code SELECT COUNT(*) FROM (SELECT 1 FROM ... GROUP BY ... HAVING ...) _count_t}
     *       to count the number of groups rather than raw rows.</li>
     * </ul>
     */
    public static <T, R> RenderedSql renderCount(SelectSpec<T, R> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        EntityMeta rootMeta = EntityMetaReader.read(spec.getEntityClass());
        String alias = spec.getAlias();
        // Honour subqueryFrom > tableNameOverride > entity table name.
        String fromTarget;
        if (spec.getSubqueryFrom() != null) {
            String innerSql = buildSelectSqlUnchecked(spec.getSubqueryFrom(), params, paramIdx, false);
            fromTarget = "(" + innerSql + ")";
        } else if (spec.getTableNameOverride() != null) {
            fromTarget = spec.getTableNameOverride();
        } else {
            fromTarget = rootMeta.getTableName();
        }

        // Prepend WITH clause when the spec has CTEs.
        String ctePrefix = buildCtePrefix(spec, params, paramIdx);

        boolean hasJoins = !spec.getJoins().isEmpty();
        boolean hasGroupBy = !spec.getGroupByExpressions().isEmpty();

        if (hasGroupBy) {
            // Wrap the grouped query as a derived table and count its rows.
            StringBuilder inner = new StringBuilder("SELECT 1")
                    .append(" FROM ").append(fromTarget).append(" ").append(alias);
            appendJoinClauses(inner, spec, params, paramIdx);
            appendWhereClause(inner, spec, params, paramIdx);
            appendGroupByClause(inner, spec, params, paramIdx);
            appendHavingClause(inner, spec, params, paramIdx);
            return new RenderedSql(ctePrefix + "SELECT COUNT(*) FROM (" + inner + ") _count_t", params);
        }

        if (hasJoins) {
            // JOINs can multiply rows for one-to-many relationships.
            // COUNT(DISTINCT pk) de-duplicates without a subquery.
            String pkCol = rootMeta.getIdColumnName();
            if (pkCol != null) {
                StringBuilder sb = new StringBuilder("SELECT COUNT(DISTINCT ")
                        .append(alias).append(".").append(pkCol).append(")")
                        .append(" FROM ").append(fromTarget).append(" ").append(alias);
                appendJoinClauses(sb, spec, params, paramIdx);
                appendWhereClause(sb, spec, params, paramIdx);
                return new RenderedSql(ctePrefix + sb, params);
            }
            // No PK available: fall back to subquery.
            StringBuilder inner = new StringBuilder("SELECT 1")
                    .append(" FROM ").append(fromTarget).append(" ").append(alias);
            appendJoinClauses(inner, spec, params, paramIdx);
            appendWhereClause(inner, spec, params, paramIdx);
            return new RenderedSql(ctePrefix + "SELECT COUNT(*) FROM (" + inner + ") _count_t", params);
        }

        // Simple case: no JOINs, no GROUP BY.
        StringBuilder sb = new StringBuilder("SELECT COUNT(*)")
                .append(" FROM ").append(fromTarget).append(" ").append(alias);
        appendWhereClause(sb, spec, params, paramIdx);
        return new RenderedSql(ctePrefix + sb, params);
    }

    /** Builds the {@code WITH cte1 AS (...), cte2 AS (...) } prefix string (empty string when no CTEs). */
    private static <T, R> String buildCtePrefix(SelectSpec<T, R> spec,
                                                 Map<String, Object> params,
                                                 AtomicInteger paramIdx) {
        List<CteDef> cteDefs = spec.getCteDefs();
        if (cteDefs.isEmpty()) return "";
        StringJoiner cteJoiner = new StringJoiner(", ");
        for (CteDef cte : cteDefs) {
            String body = buildSelectSqlUnchecked(cte.body(), params, paramIdx, false);
            cteJoiner.add(cte.name() + " AS (" + body + ")");
        }
        return "WITH " + cteJoiner + " ";
    }

    // ------------------------------------------------------------------ //
    //  Expression rendering
    // ------------------------------------------------------------------ //

    /**
     * Renders a single SELECT clause item, adding an appropriate column alias:
     * <ul>
     *   <li>{@link AliasedExpression} → {@code <inner> AS <alias>}</li>
     *   <li>{@link ColumnExpression} → {@code alias.column AS propertyName}</li>
     *   <li>Function / aggregate / literal → no alias (JDBC column label is used by the mapper)</li>
     * </ul>
     */
    private static <T, R> String renderSelectItem(SqlExpression<?> expr,
                                                   SelectSpec<T, R> spec,
                                                   Map<String, Object> params,
                                                   AtomicInteger paramIdx) {
        if (expr instanceof AliasedExpression<?> aliased) {
            return renderExpression(aliased.getInner(), spec, params, paramIdx)
                    + " AS " + aliased.getAlias();
        } else if (expr instanceof ColumnExpression<?> col) {
            String sql = renderColumnExpression(col, spec);
            String propName = col.getPropertyRef().propertyName();
            return sql + " AS " + propName;
        } else {
            // Function / aggregate / literal: no alias; mapper relies on JDBC columnLabel.
            return renderExpression(expr, spec, params, paramIdx);
        }
    }

    /**
     * Recursively renders a {@link SqlExpression} into its SQL string representation.
     * {@link ColumnExpression}s are rendered as {@code alias.column_name}.
     * {@link FunctionExpression}s and {@link AggregateExpression}s are rendered as
     * {@code FUNC(arg1, arg2, ...)}.
     * {@link LiteralExpression}s are embedded verbatim.
     * {@link AliasedExpression}s are rendered by delegating to their inner expression
     * (alias is handled separately in the SELECT clause via {@link #renderSelectItem}).
     */
    static <T, R> String renderExpression(SqlExpression<?> expression,
                                          SelectSpec<T, R> spec,
                                          Map<String, Object> params,
                                          AtomicInteger paramIdx) {
        if (expression instanceof AliasedExpression<?> aliased) {
            // In non-SELECT contexts (WHERE / ORDER BY / HAVING), render the inner expression.
            return renderExpression(aliased.getInner(), spec, params, paramIdx);
        } else if (expression instanceof ColumnExpression<?> col) {
            return renderColumnExpression(col, spec);
        } else if (expression instanceof FunctionExpression<?> fn) {
            return renderFunctionExpression(fn, spec, params, paramIdx);
        } else if (expression instanceof AggregateExpression<?> agg) {
            return renderAggregateExpression(agg, spec, params, paramIdx);
        } else if (expression instanceof LiteralExpression<?> lit) {
            return lit.getSql();
        } else if (expression instanceof CastExpression<?> cast) {
            String inner = renderExpression(cast.getInner(), spec, params, paramIdx);
            return "CAST(" + inner + " AS " + cast.getTargetType() + ")";
        } else if (expression instanceof CaseExpression<?> caseExpr) {
            return renderCaseExpression(caseExpr, spec, params, paramIdx);
        } else if (expression instanceof WindowExpression<?> win) {
            return renderWindowExpression(win, spec, params, paramIdx);
        } else if (expression instanceof ScalarSubqueryExpression<?> sub) {
            return renderScalarSubqueryExpression(sub, params, paramIdx);
        } else {
            throw new IllegalArgumentException("Unknown SqlExpression type: " + expression.getClass());
        }
    }

    private static <T, R> String renderColumnExpression(ColumnExpression<?> col, SelectSpec<T, R> spec) {
        PropertyRef pr = col.getPropertyRef();
        EntityMeta meta = EntityMetaReader.read(pr.ownerClass());
        String colName = meta.getColumnName(pr.propertyName());
        if (colName == null) colName = pr.propertyName();
        String tableAlias = col.getTableAlias() != null ? col.getTableAlias() : resolveAliasForRef(spec, pr);
        return tableAlias + "." + colName;
    }

    private static <T, R> String renderFunctionExpression(FunctionExpression<?> fn,
                                                           SelectSpec<T, R> spec,
                                                           Map<String, Object> params,
                                                           AtomicInteger paramIdx) {
        StringJoiner argJoiner = new StringJoiner(", ");
        for (SqlExpression<?> arg : fn.getArgs()) {
            argJoiner.add(renderExpression(arg, spec, params, paramIdx));
        }
        return fn.getFunctionName() + "(" + argJoiner + ")";
    }

    private static <T, R> String renderAggregateExpression(AggregateExpression<?> agg,
                                                            SelectSpec<T, R> spec,
                                                            Map<String, Object> params,
                                                            AtomicInteger paramIdx) {
        StringJoiner argJoiner = new StringJoiner(", ");
        for (SqlExpression<?> arg : agg.getArgs()) {
            argJoiner.add(renderExpression(arg, spec, params, paramIdx));
        }
        String inner = agg.isDistinct() ? "DISTINCT " + argJoiner : argJoiner.toString();
        return agg.getFunctionName() + "(" + inner + ")";
    }

    private static <T, R> String renderCaseExpression(CaseExpression<?> expr,
                                                       SelectSpec<T, R> spec,
                                                       Map<String, Object> params,
                                                       AtomicInteger paramIdx) {
        StringBuilder sb = new StringBuilder("CASE");
        for (CaseExpression.WhenClause when : expr.getWhenClauses()) {
            sb.append(" WHEN ").append(renderPredicate(when.condition(), spec, params, paramIdx));
            sb.append(" THEN ").append(renderExpression(when.result(), spec, params, paramIdx));
        }
        if (expr.getElseExpr() != null) {
            sb.append(" ELSE ").append(renderExpression(expr.getElseExpr(), spec, params, paramIdx));
        }
        sb.append(" END");
        return sb.toString();
    }

    private static <T, R> String renderWindowExpression(WindowExpression<?> win,
                                                          SelectSpec<T, R> spec,
                                                          Map<String, Object> params,
                                                          AtomicInteger paramIdx) {
        String funcSql = renderExpression(win.getFunction(), spec, params, paramIdx);

        StringBuilder over = new StringBuilder();

        List<SqlExpression<?>> partitionBy = win.getPartitionBy();
        if (!partitionBy.isEmpty()) {
            StringJoiner pj = new StringJoiner(", ");
            for (SqlExpression<?> expr : partitionBy) {
                pj.add(renderExpression(expr, spec, params, paramIdx));
            }
            over.append("PARTITION BY ").append(pj);
        }

        List<JOrder<?>> orderBy = win.getOrderBy();
        if (!orderBy.isEmpty()) {
            if (over.length() > 0) over.append(" ");
            StringJoiner oj = new StringJoiner(", ");
            for (JOrder<?> order : orderBy) {
                String colExpr = renderExpression(order.getExpression(), spec, params, paramIdx);
                String nullHandling = switch (order.getNullHandling()) {
                    case NULLS_FIRST -> " NULLS FIRST";
                    case NULLS_LAST -> " NULLS LAST";
                    case NATIVE -> "";
                };
                oj.add(colExpr + " " + order.getDirection().name() + nullHandling);
            }
            over.append("ORDER BY ").append(oj);
        }

        // Frame clause: ROWS/RANGE/GROUPS BETWEEN start AND end
        if (win.getFrameType() != null && win.getFrameStart() != null) {
            if (over.length() > 0) over.append(" ");
            String frameTypeStr = win.getFrameType().name(); // ROWS, RANGE, or GROUPS
            String startSql = win.getFrameStart().toSql();
            if (win.getFrameEnd() != null) {
                over.append(frameTypeStr).append(" BETWEEN ").append(startSql)
                        .append(" AND ").append(win.getFrameEnd().toSql());
            } else {
                over.append(frameTypeStr).append(" ").append(startSql);
            }
        }

        return funcSql + " OVER (" + over + ")";
    }

    private static String renderScalarSubqueryExpression(ScalarSubqueryExpression<?> sub,
                                                          Map<String, Object> params,
                                                          AtomicInteger paramIdx) {
        String inner = renderSubquerySql(sub.getSubquery(), params, paramIdx);
        return "(" + inner + ")";
    }

    // ------------------------------------------------------------------ //
    //  Predicate rendering
    // ------------------------------------------------------------------ //

    private static <T, R> String renderPredicate(PredicateNode node,
                                                   SelectSpec<T, R> spec,
                                                   Map<String, Object> params,
                                                   AtomicInteger paramIdx) {
        if (node instanceof LeafPredicate leaf) {
            return renderLeaf(leaf, spec, params, paramIdx);
        } else if (node instanceof AndPredicate and) {
            return renderAnd(and, spec, params, paramIdx);
        } else if (node instanceof OrPredicate or) {
            return renderOr(or, spec, params, paramIdx);
        } else if (node instanceof NotPredicate not) {
            return "NOT (" + renderPredicate(not.getChild(), spec, params, paramIdx) + ")";
        } else if (node instanceof OnEqPredicate onEq) {
            return renderOnEqAsWhere(onEq, spec);
        } else if (node instanceof InSubqueryPredicate inSub) {
            return renderInSubquery(inSub, spec, params, paramIdx);
        } else if (node instanceof ExistsPredicate exists) {
            return renderExists(exists, params, paramIdx);
        } else if (node instanceof ScalarSubqueryPredicate scalar) {
            return renderScalarSubquery(scalar, spec, params, paramIdx);
        } else if (node instanceof RawPredicate raw) {
            return renderRawPredicate(raw, params);
        } else {
            throw new IllegalArgumentException("Unknown predicate node type: " + node.getClass());
        }
    }

    private static <T, R> String renderInSubquery(InSubqueryPredicate node,
                                                    SelectSpec<T, R> spec,
                                                    Map<String, Object> params,
                                                    AtomicInteger paramIdx) {
        String lhs = renderExpression(node.getLhs(), spec, params, paramIdx);
        String inner = renderSubquerySql(node.getSubquery(), params, paramIdx);
        String op = node.isNegated() ? " NOT IN (" : " IN (";
        return lhs + op + inner + ")";
    }

    private static String renderExists(ExistsPredicate node,
                                        Map<String, Object> params,
                                        AtomicInteger paramIdx) {
        String inner = renderSubquerySql(node.getSubquery(), params, paramIdx);
        String prefix = node.isNegated() ? "NOT EXISTS (" : "EXISTS (";
        return prefix + inner + ")";
    }

    private static <T, R> String renderScalarSubquery(ScalarSubqueryPredicate node,
                                                        SelectSpec<T, R> spec,
                                                        Map<String, Object> params,
                                                        AtomicInteger paramIdx) {
        String lhs = renderExpression(node.getLhs(), spec, params, paramIdx);
        String op = switch (node.getOp()) {
            case EQ -> " = ";
            case NE -> " <> ";
            case GT -> " > ";
            case GTE -> " >= ";
            case LT -> " < ";
            case LTE -> " <= ";
        };
        String inner = renderSubquerySql(node.getSubquery(), params, paramIdx);
        return lhs + op + "(" + inner + ")";
    }

    private static String renderRawPredicate(RawPredicate node, Map<String, Object> params) {
        // Merge any user-supplied parameters into the shared parameter map.
        params.putAll(node.getParams());
        return node.getSql();
    }

    private static <T, R> String renderLeaf(LeafPredicate leaf,
                                             SelectSpec<T, R> spec,
                                             Map<String, Object> params,
                                             AtomicInteger paramIdx) {
        String lhs = renderExpression(leaf.getExpression(), spec, params, paramIdx);

        return switch (leaf.getOp()) {
            case EQ -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " = :" + p;
            }
            case NE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " <> :" + p;
            }
            case GT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " > :" + p;
            }
            case GTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " >= :" + p;
            }
            case LT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " < :" + p;
            }
            case LTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " <= :" + p;
            }
            case LIKE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " LIKE :" + p;
            }
            case LIKE_IC -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield "LOWER(" + lhs + ") LIKE LOWER(:" + p + ")";
            }
            case IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " IN (:" + p + ")";
            }
            case NOT_IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " NOT IN (:" + p + ")";
            }
            case BETWEEN -> {
                String p1 = nextParam(paramIdx, params, leaf.getValue());
                String p2 = nextParam(paramIdx, params, leaf.getValue2());
                yield lhs + " BETWEEN :" + p1 + " AND :" + p2;
            }
            case IS_NULL -> lhs + " IS NULL";
            case IS_NOT_NULL -> lhs + " IS NOT NULL";
        };
    }

    private static <T, R> String renderAnd(AndPredicate and,
                                            SelectSpec<T, R> spec,
                                            Map<String, Object> params,
                                            AtomicInteger paramIdx) {
        StringJoiner joiner = new StringJoiner(" AND ", "(", ")");
        for (PredicateNode child : and.getChildren()) {
            joiner.add(renderPredicate(child, spec, params, paramIdx));
        }
        return joiner.toString();
    }

    private static <T, R> String renderOr(OrPredicate or,
                                           SelectSpec<T, R> spec,
                                           Map<String, Object> params,
                                           AtomicInteger paramIdx) {
        StringJoiner joiner = new StringJoiner(" OR ", "(", ")");
        for (PredicateNode child : or.getChildren()) {
            joiner.add(renderPredicate(child, spec, params, paramIdx));
        }
        return joiner.toString();
    }

    private static <T, R> String renderOnCondition(PredicateNode cond,
                                                    SelectSpec<T, R> spec,
                                                    Map<String, Object> params,
                                                    AtomicInteger paramIdx,
                                                    Set<String> subqueryAliases) {
        if (cond instanceof OnEqPredicate onEq) {
            return renderOnEq(onEq, subqueryAliases);
        }
        return renderPredicate(cond, spec, params, paramIdx);
    }

    /**
     * Resolves a column identifier for an ON-condition side.
     *
     * <p>When the alias refers to a subquery, the column is identified by its projected
     * alias (the camelCase property name), because that is what the subquery outputs.
     * For plain entity joins the DB column name is used instead.
     */
    private static String resolveOnColumn(PropertyRef ref, String alias, Set<String> subqueryAliases) {
        if (subqueryAliases.contains(alias)) {
            // Subquery projects columns as camelCase property aliases
            return ref.propertyName();
        }
        EntityMeta meta = EntityMetaReader.read(ref.ownerClass());
        String col = meta.getColumnName(ref.propertyName());
        return col != null ? col : ref.propertyName();
    }

    private static String renderOnEq(OnEqPredicate onEq, Set<String> subqueryAliases) {
        String leftCol  = resolveOnColumn(onEq.getLeftRef(),  onEq.getLeftAlias(),  subqueryAliases);
        String rightCol = resolveOnColumn(onEq.getRightRef(), onEq.getRightAlias(), subqueryAliases);
        return onEq.getLeftAlias() + "." + leftCol + " = " + onEq.getRightAlias() + "." + rightCol;
    }

    private static <T, R> String renderOnEqAsWhere(OnEqPredicate onEq, SelectSpec<T, R> spec) {
        // In WHERE context there are no subquery aliases; fall back to column names.
        return renderOnEq(onEq, Set.of());
    }

    // ------------------------------------------------------------------ //
    //  UPSERT rendering
    // ------------------------------------------------------------------ //

    /**
     * Renders a dialect-specific UPSERT (INSERT … ON CONFLICT / MERGE) statement.
     *
     * @param spec      the upsert specification (conflict and update columns)
     * @param meta      entity metadata (table name, column mappings)
     * @param colValues ordered map of {@code columnName → value} for the full row
     * @param dialect   the active dialect that determines SQL syntax
     * @return the rendered SQL and its named parameters
     * @throws UnsupportedOperationException when the dialect does not support UPSERT
     */
    public static <T> RenderedSql renderUpsert(UpsertSpec<T> spec, EntityMeta meta,
                                                java.util.LinkedHashMap<String, Object> colValues,
                                                Dialect dialect) {
        return dialect.renderUpsert(spec, meta, colValues);
    }

    // ------------------------------------------------------------------ //
    //  INSERT rendering
    // ------------------------------------------------------------------ //

    /**
     * Renders a parameterized INSERT statement from an {@link InsertSpec} and a
     * column-name-to-value map.
     *
     * <p>Parameter names equal the column names (e.g. column {@code created_at} →
     * parameter {@code :created_at}).
     *
     * <p>Example output:
     * {@code INSERT INTO t_user (username, email) VALUES (:username, :email)}
     *
     * @param spec      the insert specification (entity class and optional explicit column list)
     * @param meta      entity metadata (table name, column mappings)
     * @param colValues ordered map of {@code columnName → value} to insert
     */
    public static <T> RenderedSql renderInsert(InsertSpec<T> spec, EntityMeta meta,
                                               java.util.LinkedHashMap<String, Object> colValues) {
        List<String> cols = spec.getColumnNames().isEmpty()
                ? new ArrayList<>(colValues.keySet())
                : spec.getColumnNames();

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        Map<String, Object> params = new LinkedHashMap<>();
        for (String col : cols) {
            colJoiner.add(col);
            valJoiner.add(":" + col);
            params.put(col, colValues.get(col));
        }

        String sql = "INSERT INTO " + meta.getTableName()
                + " (" + colJoiner + ") VALUES (" + valJoiner + ")";
        return new RenderedSql(sql, params);
    }

    // ------------------------------------------------------------------ //
    //  RETURNING / OUTPUT rendering helpers
    // ------------------------------------------------------------------ //

    /**
     * Appends a {@code RETURNING col1, col2, …} clause to an INSERT statement, enabling
     * the database to return the inserted row's values.
     *
     * <p>Supported by PostgreSQL and H2. Not supported by MySQL or standard SQL Server.
     * For SQL Server use {@code OUTPUT inserted.col1, …} via raw SQL instead.
     *
     * @param spec           the insert specification
     * @param meta           entity metadata
     * @param colValues      column-to-value map (same as passed to {@link #renderInsert})
     * @param returningCols  column names to return (use the DB column name, not the property name)
     * @return the rendered INSERT … RETURNING SQL and its parameters
     */
    public static <T> RenderedSql renderInsertReturning(InsertSpec<T> spec, EntityMeta meta,
                                                         java.util.LinkedHashMap<String, Object> colValues,
                                                         List<String> returningCols) {
        RenderedSql base = renderInsert(spec, meta, colValues);
        if (returningCols == null || returningCols.isEmpty()) {
            return base;
        }
        String sql = base.getSql() + " RETURNING " + String.join(", ", returningCols);
        return new RenderedSql(sql, base.getParams());
    }

    /**
     * Appends a {@code RETURNING col1, col2, …} clause to an UPDATE statement, enabling
     * the database to return the modified row's values.
     *
     * <p>Supported by PostgreSQL and H2. Not supported by MySQL.
     *
     * @param spec          the update specification
     * @param returningCols column names to return (use the DB column name, not the property name)
     * @return the rendered UPDATE … RETURNING SQL and its parameters
     */
    public static <T> RenderedSql renderUpdateReturning(UpdateSpec<T> spec, List<String> returningCols) {
        RenderedSql base = renderUpdate(spec);
        if (returningCols == null || returningCols.isEmpty()) {
            return base;
        }
        String sql = base.getSql() + " RETURNING " + String.join(", ", returningCols);
        return new RenderedSql(sql, base.getParams());
    }

    /**
     * Appends a {@code RETURNING col1, col2, …} clause to a DELETE statement, enabling
     * the database to return the deleted row's values.
     *
     * <p>Supported by PostgreSQL and H2. Not supported by MySQL.
     *
     * @param spec          the delete specification
     * @param returningCols column names to return (use the DB column name, not the property name)
     * @return the rendered DELETE … RETURNING SQL and its parameters
     */
    public static <T> RenderedSql renderDeleteReturning(DeleteSpec<T> spec, List<String> returningCols) {
        RenderedSql base = renderDelete(spec);
        if (returningCols == null || returningCols.isEmpty()) {
            return base;
        }
        String sql = base.getSql() + " RETURNING " + String.join(", ", returningCols);
        return new RenderedSql(sql, base.getParams());
    }

    // ------------------------------------------------------------------ //
    //  UPDATE / DELETE rendering
    // ------------------------------------------------------------------ //

    /**
     * Renders a parameterized UPDATE statement from a {@link UpdateSpec}.
     *
     * <p>Example output: {@code UPDATE t_user SET status = :p1, age = :p2 WHERE id = :p3}
     */
    public static <T> RenderedSql renderUpdate(UpdateSpec<T> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        StringBuilder sb = new StringBuilder("UPDATE ").append(meta.getTableName()).append(" SET ");

        StringJoiner setJoiner = new StringJoiner(", ");
        for (Map.Entry<String, Object> entry : spec.getAssignments()) {
            String colName = meta.getColumnName(entry.getKey());
            if (colName == null) colName = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof SqlExpression<?> expr) {
                // Expression assignment: SET col = <SQL expression>
                setJoiner.add(colName + " = " + renderExpressionStandalone(expr, meta, params, paramIdx));
            } else {
                String param = nextParam(paramIdx, params, value);
                setJoiner.add(colName + " = :" + param);
            }
        }
        sb.append(setJoiner);

        if (spec.getWhere() != null) {
            sb.append(" WHERE ");
            sb.append(renderPredicateStandalone(spec.getWhere(), meta, params, paramIdx));
        }

        return new RenderedSql(sb.toString(), params);
    }

    /**
     * Renders a parameterized DELETE statement from a {@link DeleteSpec}.
     *
     * <p>Example output: {@code DELETE FROM t_user WHERE status = :p1}
     */
    public static <T> RenderedSql renderDelete(DeleteSpec<T> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        StringBuilder sb = new StringBuilder("DELETE FROM ").append(meta.getTableName());

        if (spec.getWhere() != null) {
            sb.append(" WHERE ");
            sb.append(renderPredicateStandalone(spec.getWhere(), meta, params, paramIdx));
        }

        return new RenderedSql(sb.toString(), params);
    }

    /**
     * Renders a predicate in standalone (non-SELECT) context where column references are
     * resolved directly from the entity metadata without a table alias.
     */
    private static String renderPredicateStandalone(PredicateNode node,
                                                     EntityMeta meta,
                                                     Map<String, Object> params,
                                                     AtomicInteger paramIdx) {
        if (node instanceof io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate leaf) {
            return renderLeafStandalone(leaf, meta, params, paramIdx);
        } else if (node instanceof io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate and) {
            StringJoiner joiner = new StringJoiner(" AND ", "(", ")");
            for (PredicateNode child : and.getChildren()) {
                joiner.add(renderPredicateStandalone(child, meta, params, paramIdx));
            }
            return joiner.toString();
        } else if (node instanceof io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate or) {
            StringJoiner joiner = new StringJoiner(" OR ", "(", ")");
            for (PredicateNode child : or.getChildren()) {
                joiner.add(renderPredicateStandalone(child, meta, params, paramIdx));
            }
            return joiner.toString();
        } else if (node instanceof io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate not) {
            return "NOT (" + renderPredicateStandalone(not.getChild(), meta, params, paramIdx) + ")";
        } else if (node instanceof RawPredicate raw) {
            return renderRawPredicate(raw, params);
        } else {
            throw new IllegalArgumentException("Unsupported predicate node type: " + node.getClass());
        }
    }

    private static String renderLeafStandalone(io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate leaf,
                                                EntityMeta meta,
                                                Map<String, Object> params,
                                                AtomicInteger paramIdx) {
        // Resolve LHS: if the expression is a ColumnExpression, resolve its column name from meta
        String lhs;
        io.github.jsbxyyx.jdbcdsl.expr.SqlExpression<?> expr = leaf.getExpression();
        if (expr instanceof io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression<?> col) {
            String propName = col.getPropertyRef().propertyName();
            String colName = meta.getColumnName(propName);
            if (colName == null) colName = propName;
            lhs = colName;
        } else {
            // Fallback: render as-is without table alias (unusual for update/delete)
            lhs = expr.toString();
        }

        return switch (leaf.getOp()) {
            case EQ -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " = :" + p;
            }
            case NE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " <> :" + p;
            }
            case GT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " > :" + p;
            }
            case GTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " >= :" + p;
            }
            case LT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " < :" + p;
            }
            case LTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " <= :" + p;
            }
            case LIKE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " LIKE :" + p;
            }
            case LIKE_IC -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield "LOWER(" + lhs + ") LIKE LOWER(:" + p + ")";
            }
            case IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " IN (:" + p + ")";
            }
            case NOT_IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield lhs + " NOT IN (:" + p + ")";
            }
            case BETWEEN -> {
                String p1 = nextParam(paramIdx, params, leaf.getValue());
                String p2 = nextParam(paramIdx, params, leaf.getValue2());
                yield lhs + " BETWEEN :" + p1 + " AND :" + p2;
            }
            case IS_NULL -> lhs + " IS NULL";
            case IS_NOT_NULL -> lhs + " IS NOT NULL";
        };
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    // ------------------------------------------------------------------ //
    //  Clause-appending helpers (shared by renderSelect and renderCount)
    // ------------------------------------------------------------------ //

    private static <T, R> void appendJoinClauses(StringBuilder sb,
                                                   SelectSpec<T, R> spec,
                                                   Map<String, Object> params,
                                                   AtomicInteger paramIdx) {
        // Collect aliases that refer to subqueries (derived tables), including the spec's
        // own FROM if it is a subquery.  ON-condition column references for these aliases
        // use camelCase property names (the aliases projected by the inner SELECT) rather
        // than the raw DB column names used for real-table joins.
        Set<String> subqueryAliases = new HashSet<>();
        if (spec.getSubqueryFrom() != null) {
            subqueryAliases.add(spec.getAlias());
        }
        for (JoinSpec j : spec.getJoins()) {
            if (j.getSubqueryJoin() != null) {
                subqueryAliases.add(j.getAlias());
            }
        }

        for (JoinSpec join : spec.getJoins()) {
            sb.append(" ").append(joinTypeStr(join.getJoinType())).append(" ");

            if (join.getSubqueryJoin() != null) {
                // Subquery join: JOIN (SELECT ...) alias
                String innerSql = buildSelectSqlUnchecked(join.getSubqueryJoin(), params, paramIdx, false);
                sb.append("(").append(innerSql).append(")");
            } else {
                // Entity join: JOIN t_table
                EntityMeta joinMeta = EntityMetaReader.read(join.getJoinEntityClass());
                sb.append(joinMeta.getTableName());
            }
            sb.append(" ").append(join.getAlias());

            // CROSS JOIN has no ON clause
            if (join.getJoinType() != JoinType.CROSS && !join.getOnConditions().isEmpty()) {
                sb.append(" ON ");
                StringJoiner onJoiner = new StringJoiner(" AND ");
                for (PredicateNode cond : join.getOnConditions()) {
                    onJoiner.add(renderOnCondition(cond, spec, params, paramIdx, subqueryAliases));
                }
                sb.append(onJoiner);
            }
        }
    }

    private static <T, R> void appendWhereClause(StringBuilder sb,
                                                   SelectSpec<T, R> spec,
                                                   Map<String, Object> params,
                                                   AtomicInteger paramIdx) {
        PredicateNode where = spec.getWhere();
        if (where != null) {
            sb.append(" WHERE ").append(renderPredicate(where, spec, params, paramIdx));
        }
    }

    private static <T, R> void appendGroupByClause(StringBuilder sb,
                                                     SelectSpec<T, R> spec,
                                                     Map<String, Object> params,
                                                     AtomicInteger paramIdx) {
        List<SqlExpression<?>> groupBy = spec.getGroupByExpressions();
        if (!groupBy.isEmpty()) {
            sb.append(" GROUP BY ");
            StringJoiner joiner = new StringJoiner(", ");
            for (SqlExpression<?> expr : groupBy) {
                joiner.add(renderExpression(expr, spec, params, paramIdx));
            }
            sb.append(joiner);
        }
    }

    private static <T, R> void appendHavingClause(StringBuilder sb,
                                                    SelectSpec<T, R> spec,
                                                    Map<String, Object> params,
                                                    AtomicInteger paramIdx) {
        PredicateNode having = spec.getHaving();
        if (having != null) {
            sb.append(" HAVING ").append(renderPredicate(having, spec, params, paramIdx));
        }
    }

    private static String nextParam(AtomicInteger idx, Map<String, Object> params, Object value) {
        String name = "p" + idx.incrementAndGet();
        params.put(name, value);
        return name;
    }

    private static <T, R> String resolveAliasForRef(SelectSpec<T, R> spec, PropertyRef pr) {
        if (pr.ownerClass().equals(spec.getEntityClass())) {
            // Self-join guard: if the same entity class also appears as a non-subquery join,
            // the column reference is ambiguous — there is no way to know which table alias
            // the caller intended.  Fail fast with an actionable error message so the caller
            // does not silently receive SQL that references the wrong table.
            boolean selfJoinExists = spec.getJoins().stream()
                    .anyMatch(j -> j.getSubqueryJoin() == null
                            && pr.ownerClass().equals(j.getJoinEntityClass()));
            if (selfJoinExists) {
                String prop = pr.propertyName();
                String getter = "get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
                throw new IllegalStateException(
                        "Ambiguous column reference '" + pr.ownerClass().getSimpleName() + "."
                        + prop + "': the same entity class is used as both the root table ("
                        + spec.getAlias() + ") and a joined table. "
                        + "Use col(" + pr.ownerClass().getSimpleName() + "::" + getter
                        + ", \"<alias>\") to specify the table alias explicitly.");
            }
            return spec.getAlias();
        }
        for (JoinSpec join : spec.getJoins()) {
            if (pr.ownerClass().equals(join.getJoinEntityClass())) {
                return join.getAlias();
            }
        }
        return spec.getAlias();
    }

    private static String joinTypeStr(JoinType type) {
        return switch (type) {
            case INNER -> "INNER JOIN";
            case LEFT -> "LEFT JOIN";
            case RIGHT -> "RIGHT JOIN";
            case FULL -> "FULL OUTER JOIN";
            case CROSS -> "CROSS JOIN";
        };
    }

    /**
     * Renders a {@link SqlExpression} in a standalone context (no table alias, no SelectSpec).
     *
     * <p>Used for UPDATE SET expression assignments where the RHS is a SQL expression rather
     * than a bound parameter. Column references are rendered as bare column names (no alias
     * prefix), which is correct for single-table UPDATE statements.
     *
     * <p>{@link ScalarSubqueryExpression}s are fully supported: the inner SELECT is rendered
     * inline and its parameters are merged into the shared {@code params} map.
     */
    private static String renderExpressionStandalone(SqlExpression<?> expression, EntityMeta meta,
                                                      Map<String, Object> params,
                                                      AtomicInteger paramIdx) {
        if (expression instanceof LiteralExpression<?> lit) {
            return lit.getSql();
        } else if (expression instanceof ColumnExpression<?> col) {
            String propName = col.getPropertyRef().propertyName();
            String colName = meta.getColumnName(propName);
            return colName != null ? colName : propName;
        } else if (expression instanceof FunctionExpression<?> fn) {
            StringJoiner argJoiner = new StringJoiner(", ");
            for (SqlExpression<?> arg : fn.getArgs()) {
                argJoiner.add(renderExpressionStandalone(arg, meta, params, paramIdx));
            }
            return fn.getFunctionName() + "(" + argJoiner + ")";
        } else if (expression instanceof CastExpression<?> cast) {
            return "CAST(" + renderExpressionStandalone(cast.getInner(), meta, params, paramIdx)
                    + " AS " + cast.getTargetType() + ")";
        } else if (expression instanceof AliasedExpression<?> aliased) {
            return renderExpressionStandalone(aliased.getInner(), meta, params, paramIdx);
        } else if (expression instanceof ScalarSubqueryExpression<?> sub) {
            // SET col = (SELECT ...) — render the subquery inline, sharing the param map
            return "(" + renderSubquerySql(sub.getSubquery(), params, paramIdx) + ")";
        } else {
            throw new IllegalArgumentException(
                    "Unsupported expression type in UPDATE SET: " + expression.getClass().getSimpleName());
        }
    }
}
