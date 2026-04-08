package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.AliasedExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
import io.github.jsbxyyx.jdbcdsl.expr.LiteralExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import io.github.jsbxyyx.jdbcdsl.OnBuilder.OnEqPredicate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Renders the SELECT query for a {@link SelectSpec}.
     */
    public static <T, R> RenderedSql renderSelect(SelectSpec<T, R> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        EntityMeta rootMeta = EntityMetaReader.read(spec.getEntityClass());
        String alias = spec.getAlias();

        StringBuilder sb = new StringBuilder();

        // SELECT clause
        sb.append("SELECT ");
        List<SqlExpression<?>> exprs = spec.getSelectedExpressions();
        if (exprs.isEmpty()) {
            // No explicit select(): expand all entity columns in entity declaration order,
            // which mirrors database column ordinal order for generated entities.
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
        sb.append(" FROM ").append(rootMeta.getTableName()).append(" ").append(alias);

        // JOIN clauses
        for (JoinSpec join : spec.getJoins()) {
            EntityMeta joinMeta = EntityMetaReader.read(join.getJoinEntityClass());
            sb.append(" ").append(joinTypeStr(join.getJoinType()))
              .append(" ").append(joinMeta.getTableName()).append(" ").append(join.getAlias());
            if (!join.getOnConditions().isEmpty()) {
                sb.append(" ON ");
                StringJoiner onJoiner = new StringJoiner(" AND ");
                for (PredicateNode cond : join.getOnConditions()) {
                    onJoiner.add(renderOnCondition(cond, spec, params, paramIdx));
                }
                sb.append(onJoiner);
            }
        }

        // WHERE clause
        PredicateNode where = spec.getWhere();
        if (where != null) {
            sb.append(" WHERE ");
            sb.append(renderPredicate(where, spec, params, paramIdx));
        }

        // GROUP BY clause
        List<SqlExpression<?>> groupBy = spec.getGroupByExpressions();
        if (!groupBy.isEmpty()) {
            sb.append(" GROUP BY ");
            StringJoiner groupJoiner = new StringJoiner(", ");
            for (SqlExpression<?> expr : groupBy) {
                groupJoiner.add(renderExpression(expr, spec, params, paramIdx));
            }
            sb.append(groupJoiner);
        }

        // HAVING clause
        PredicateNode having = spec.getHaving();
        if (having != null) {
            sb.append(" HAVING ");
            sb.append(renderPredicate(having, spec, params, paramIdx));
        }

        // ORDER BY clause
        JSort<T> sort = spec.getSort();
        if (!sort.isEmpty()) {
            sb.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (JOrder<T> order : sort.getOrders()) {
                String colExpr;
                if (order.isIgnoreCase() && order.getExpression() instanceof ColumnExpression<?> colExprNode) {
                    // Backward-compat: ignoreCase wraps a column in LOWER(...)
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

        return new RenderedSql(sb.toString(), params);
    }

    /**
     * Renders a COUNT(*) query for the given spec (no ORDER BY, no pagination).
     */
    public static <T, R> RenderedSql renderCount(SelectSpec<T, R> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        AtomicInteger paramIdx = new AtomicInteger(0);

        EntityMeta rootMeta = EntityMetaReader.read(spec.getEntityClass());
        String alias = spec.getAlias();

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(*) FROM ").append(rootMeta.getTableName()).append(" ").append(alias);

        // JOIN clauses
        for (JoinSpec join : spec.getJoins()) {
            EntityMeta joinMeta = EntityMetaReader.read(join.getJoinEntityClass());
            sb.append(" ").append(joinTypeStr(join.getJoinType()))
              .append(" ").append(joinMeta.getTableName()).append(" ").append(join.getAlias());
            if (!join.getOnConditions().isEmpty()) {
                sb.append(" ON ");
                StringJoiner onJoiner = new StringJoiner(" AND ");
                for (PredicateNode cond : join.getOnConditions()) {
                    onJoiner.add(renderOnCondition(cond, spec, params, paramIdx));
                }
                sb.append(onJoiner);
            }
        }

        PredicateNode where = spec.getWhere();
        if (where != null) {
            sb.append(" WHERE ");
            sb.append(renderPredicate(where, spec, params, paramIdx));
        }

        return new RenderedSql(sb.toString(), params);
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
        } else {
            throw new IllegalArgumentException("Unknown predicate node type: " + node.getClass());
        }
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
                                                    AtomicInteger paramIdx) {
        if (cond instanceof OnEqPredicate onEq) {
            return renderOnEq(onEq, spec);
        }
        return renderPredicate(cond, spec, params, paramIdx);
    }

    private static <T, R> String renderOnEq(OnEqPredicate onEq, SelectSpec<T, R> spec) {
        EntityMeta leftMeta = EntityMetaReader.read(onEq.getLeftRef().ownerClass());
        String leftCol = leftMeta.getColumnName(onEq.getLeftRef().propertyName());
        if (leftCol == null) leftCol = onEq.getLeftRef().propertyName();

        EntityMeta rightMeta = EntityMetaReader.read(onEq.getRightRef().ownerClass());
        String rightCol = rightMeta.getColumnName(onEq.getRightRef().propertyName());
        if (rightCol == null) rightCol = onEq.getRightRef().propertyName();

        return onEq.getLeftAlias() + "." + leftCol + " = " + onEq.getRightAlias() + "." + rightCol;
    }

    private static <T, R> String renderOnEqAsWhere(OnEqPredicate onEq, SelectSpec<T, R> spec) {
        return renderOnEq(onEq, spec);
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
            String param = nextParam(paramIdx, params, entry.getValue());
            setJoiner.add(colName + " = :" + param);
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

    private static String nextParam(AtomicInteger idx, Map<String, Object> params, Object value) {
        String name = "p" + idx.incrementAndGet();
        params.put(name, value);
        return name;
    }

    private static <T, R> String resolveAliasForRef(SelectSpec<T, R> spec, PropertyRef pr) {
        if (pr.ownerClass().equals(spec.getEntityClass())) {
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
        };
    }
}
