package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import io.github.jsbxyyx.jdbcdsl.OnBuilder.OnEqPredicate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders a {@link SelectSpec} into a parameterized {@link RenderedSql}.
 *
 * <p>Column aliases are {@code c0, c1, ...} corresponding to the selected property order.
 * Named parameters are {@code :p1, :p2, ...}.
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
        List<PropertyRef> props = spec.getSelectedProperties();
        if (props.isEmpty()) {
            sb.append(alias).append(".*");
        } else {
            StringJoiner cols = new StringJoiner(", ");
            for (int i = 0; i < props.size(); i++) {
                PropertyRef pr = props.get(i);
                EntityMeta meta = EntityMetaReader.read(pr.ownerClass());
                String colName = meta.getColumnName(pr.propertyName());
                if (colName == null) colName = pr.propertyName();
                String tableAlias = resolveAlias(spec, pr);
                cols.add(tableAlias + "." + colName + " AS c" + i);
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

        // ORDER BY clause
        JSort<T> sort = spec.getSort();
        if (!sort.isEmpty()) {
            sb.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (JOrder<T> order : sort.getOrders()) {
                PropertyRef pr = order.getPropertyRef();
                EntityMeta meta = EntityMetaReader.read(pr.ownerClass());
                String colName = meta.getColumnName(pr.propertyName());
                if (colName == null) colName = pr.propertyName();
                String tableAlias = resolveAliasForRef(spec, pr);
                String colExpr = order.isIgnoreCase()
                        ? "LOWER(" + tableAlias + "." + colName + ")"
                        : tableAlias + "." + colName;
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
        PropertyRef pr = leaf.getPropertyRef();
        EntityMeta meta = EntityMetaReader.read(pr.ownerClass());
        String colName = meta.getColumnName(pr.propertyName());
        if (colName == null) colName = pr.propertyName();
        String tableAlias = leaf.getTableAlias() != null ? leaf.getTableAlias() : resolveAliasForRef(spec, pr);

        return switch (leaf.getOp()) {
            case EQ -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " = :" + p;
            }
            case NE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " <> :" + p;
            }
            case GT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " > :" + p;
            }
            case GTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " >= :" + p;
            }
            case LT -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " < :" + p;
            }
            case LTE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " <= :" + p;
            }
            case LIKE -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " LIKE :" + p;
            }
            case IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " IN (:" + p + ")";
            }
            case NOT_IN -> {
                String p = nextParam(paramIdx, params, leaf.getValue());
                yield tableAlias + "." + colName + " NOT IN (:" + p + ")";
            }
            case BETWEEN -> {
                String p1 = nextParam(paramIdx, params, leaf.getValue());
                String p2 = nextParam(paramIdx, params, leaf.getValue2());
                yield tableAlias + "." + colName + " BETWEEN :" + p1 + " AND :" + p2;
            }
            case IS_NULL -> tableAlias + "." + colName + " IS NULL";
            case IS_NOT_NULL -> tableAlias + "." + colName + " IS NOT NULL";
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
    //  Helpers
    // ------------------------------------------------------------------ //

    private static String nextParam(AtomicInteger idx, Map<String, Object> params, Object value) {
        String name = "p" + idx.incrementAndGet();
        params.put(name, value);
        return name;
    }

    private static <T, R> String resolveAlias(SelectSpec<T, R> spec, PropertyRef pr) {
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

    private static <T, R> String resolveAliasForRef(SelectSpec<T, R> spec, PropertyRef pr) {
        return resolveAlias(spec, pr);
    }

    private static String joinTypeStr(JoinType type) {
        return switch (type) {
            case INNER -> "INNER JOIN";
            case LEFT -> "LEFT JOIN";
            case RIGHT -> "RIGHT JOIN";
        };
    }
}
