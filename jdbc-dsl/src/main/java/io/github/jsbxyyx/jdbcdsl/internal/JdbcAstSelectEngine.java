package io.github.jsbxyyx.jdbcdsl.internal;

import io.github.jsbxyyx.jdbcast.PropertyRef;
import io.github.jsbxyyx.jdbcast.PropertyRefResolver;
import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.builder.SQL;
import io.github.jsbxyyx.jdbcast.builder.SelectBuilder;
import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.clause.FrameBound;
import io.github.jsbxyyx.jdbcast.clause.FrameType;
import io.github.jsbxyyx.jdbcast.clause.JoinClause;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.AndCondition;
import io.github.jsbxyyx.jdbcast.condition.BetweenCondition;
import io.github.jsbxyyx.jdbcast.condition.CompareCondition;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.condition.InCondition;
import io.github.jsbxyyx.jdbcast.condition.LikeCondition;
import io.github.jsbxyyx.jdbcast.condition.NotCondition;
import io.github.jsbxyyx.jdbcast.condition.NullCondition;
import io.github.jsbxyyx.jdbcast.condition.Op;
import io.github.jsbxyyx.jdbcast.condition.OrCondition;
import io.github.jsbxyyx.jdbcast.condition.RawCondition;
import io.github.jsbxyyx.jdbcast.expr.AggExpr;
import io.github.jsbxyyx.jdbcast.expr.ArithmeticExpr;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.expr.FunctionExpr;
import io.github.jsbxyyx.jdbcast.expr.LiteralExpr;
import io.github.jsbxyyx.jdbcast.expr.RawExpr;
import io.github.jsbxyyx.jdbcast.expr.WindowExpr;
import io.github.jsbxyyx.jdbcast.renderer.AnsiSqlRenderer;
import io.github.jsbxyyx.jdbcast.renderer.MetaResolver;
import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpsertStatement;
import io.github.jsbxyyx.jdbcdsl.CteDef;
import io.github.jsbxyyx.jdbcdsl.DeleteSpec;
import io.github.jsbxyyx.jdbcdsl.EntityMeta;
import io.github.jsbxyyx.jdbcdsl.EntityMetaReader;
import io.github.jsbxyyx.jdbcdsl.InsertSpec;
import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.JoinSpec;
import io.github.jsbxyyx.jdbcdsl.JoinType;
import io.github.jsbxyyx.jdbcdsl.OnBuilder;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.UnionSpec;
import io.github.jsbxyyx.jdbcdsl.UpdateSpec;
import io.github.jsbxyyx.jdbcdsl.UpsertSpec;
import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.PostgresDialect;
import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.AliasedExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ArithmeticExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transitional adapter from jdbc-dsl SELECT specs to the jdbc-ast SQL engine.
 *
 * <p>Only the feature set that has a semantics-preserving translation is accepted here.
 * Callers retain the existing renderer as a compatibility path for SELECT features not yet
 * migrated; the long-term goal is to remove that path after the remaining AST adapters land.
 */
public final class JdbcAstSelectEngine {

    private static final MetaResolver META = new MetaResolver() {
        @Override
        public String tableName(Class<?> entityClass) {
            return EntityMetaReader.read(entityClass).getTableName();
        }

        @Override
        public String columnName(SFunction<?, ?> getter) {
            PropertyRef ref = PropertyRefResolver.resolve(getter);
            return columnName(ref.ownerClass(), ref.propertyName());
        }

        @Override
        public String columnName(Class<?> entityClass, String propertyName) {
            String column = EntityMetaReader.read(entityClass).getColumnName(propertyName);
            return column == null ? propertyName : column;
        }
    };

    private static final AnsiSqlRenderer RENDERER = new AnsiSqlRenderer(META);

    private JdbcAstSelectEngine() {}

    /** Renders SELECT exclusively through jdbc-ast. Unsupported syntax fails explicitly. */
    public static <T, R> RenderedSql renderSelect(SelectSpec<T, R> spec) {
        return renderSelect(spec, true);
    }

    private static <T, R> RenderedSql renderSelect(SelectSpec<T, R> spec, boolean aliasProjection) {
        RenderedSql rendered = tryRender(spec, aliasProjection);
        if (rendered == null) {
            throw new UnsupportedOperationException("SELECT spec contains syntax not yet supported by jdbc-ast");
        }
        return rendered;
    }

    /**
     * Renders INSERT through jdbc-ast, preserving the legacy renderer as a safety fallback.
     * The supplied values are keyed by database column name, as produced by JdbcDslExecutor.
     */
    public static RenderedSql renderUpsert(
            UpsertSpec<?> spec, EntityMeta meta, LinkedHashMap<String, Object> columnValues, Dialect dialect) {
        List<String> columns = new ArrayList<>(columnValues.keySet());
        List<String> updateColumns = Dialect.resolveUpdateColumns(spec, columns);
        List<String> conflictColumns = spec.getConflictColumns();
        if (!(dialect instanceof MySqlDialect)
                && !(dialect instanceof PostgresDialect && spec.isDoNothing())
                && conflictColumns.isEmpty()) {
            throw new IllegalArgumentException("UPSERT requires conflict columns for dialect "
                    + dialect.getClass().getSimpleName());
        }
        if (!columns.containsAll(conflictColumns)) {
            throw new IllegalArgumentException("UPSERT conflict columns must be included in insert columns");
        }
        if (!columns.containsAll(updateColumns)) {
            throw new IllegalArgumentException("UPSERT update columns must be included in insert columns");
        }
        UpsertStatement statement = new UpsertStatement(
                meta.getTableName(), columns, columnValues, conflictColumns, updateColumns, spec.isDoNothing());
        return dialect.renderUpsert(statement);
    }

    public static <T> RenderedSql renderInsert(InsertSpec<T> spec, EntityMeta meta, Map<String, Object> columnValues) {
        return renderInsert(spec, meta, columnValues, List.of());
    }

    public static <T> RenderedSql renderInsert(
            InsertSpec<T> spec, EntityMeta meta, Map<String, Object> columnValues, List<String> returningColumns) {
        RenderedSql rendered = tryRenderInsert(spec, meta, columnValues, returningColumns);
        if (rendered == null) {
            throw new UnsupportedOperationException("INSERT spec cannot be represented by jdbc-ast");
        }
        return rendered;
    }

    public static <T> RenderedSql tryRenderInsert(
            InsertSpec<T> spec, EntityMeta meta, Map<String, Object> columnValues, List<String> returningColumns) {
        List<String> columns =
                spec.getColumnNames().isEmpty() ? new ArrayList<>(columnValues.keySet()) : spec.getColumnNames();
        if (columns.isEmpty() || columns.stream().anyMatch(column -> !columnValues.containsKey(column))) {
            return null;
        }
        List<ColumnAssignment> assignments = new ArrayList<>();
        for (String column : columns) {
            String property = meta.getPropertyToColumn().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(column))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(column);
            assignments.add(
                    new ColumnAssignment(spec.getEntityClass(), property, LiteralExpr.of(columnValues.get(column))));
        }
        io.github.jsbxyyx.jdbcast.renderer.RenderedSql rendered = RENDERER.render(new InsertStatement(
                spec.getEntityClass(), assignments, returningColumns == null ? List.of() : returningColumns));
        return new RenderedSql(rendered.sql(), rendered.params());
    }

    public static <T> RenderedSql renderUpdate(UpdateSpec<T> spec) {
        RenderedSql rendered = tryRenderUpdate(spec);
        if (rendered == null) {
            throw new UnsupportedOperationException("UPDATE spec contains syntax not yet supported by jdbc-ast");
        }
        return rendered;
    }

    public static <T> RenderedSql renderUpdateReturning(UpdateSpec<T> spec, List<String> returningColumns) {
        RenderedSql rendered = tryRenderUpdate(spec, returningColumns);
        if (rendered == null) {
            throw new UnsupportedOperationException(
                    "UPDATE RETURNING spec contains syntax not yet supported by jdbc-ast");
        }
        return rendered;
    }

    public static <T> RenderedSql tryRenderUpdate(UpdateSpec<T> spec) {
        return tryRenderUpdate(spec, List.of());
    }

    public static <T> RenderedSql tryRenderUpdate(UpdateSpec<T> spec, List<String> returningColumns) {
        if (spec.getAssignments().isEmpty()) {
            return null;
        }
        Condition where;
        try {
            where = spec.getWhere() == null ? null : toCondition(spec.getWhere(), null, true);
        } catch (UnsupportedOperationException unsupported) {
            return null;
        }
        List<ColumnAssignment> assignments = new ArrayList<>();
        for (Map.Entry<String, Object> assignment : spec.getAssignments()) {
            Expr<?> value = assignment.getValue() instanceof SqlExpression<?> expression
                    ? toExpr(expression, null, true)
                    : LiteralExpr.of(assignment.getValue());
            assignments.add(new ColumnAssignment(spec.getEntityClass(), assignment.getKey(), value));
        }
        io.github.jsbxyyx.jdbcast.renderer.RenderedSql rendered = RENDERER.render(new UpdateStatement(
                spec.getEntityClass(),
                null,
                assignments,
                where,
                returningColumns == null ? List.of() : returningColumns));
        return new RenderedSql(rendered.sql(), rendered.params());
    }

    public static <T> RenderedSql renderDelete(DeleteSpec<T> spec) {
        RenderedSql rendered = tryRenderDelete(spec);
        if (rendered == null) {
            throw new UnsupportedOperationException("DELETE spec contains syntax not yet supported by jdbc-ast");
        }
        return rendered;
    }

    public static <T> RenderedSql renderDeleteReturning(DeleteSpec<T> spec, List<String> returningColumns) {
        RenderedSql rendered = tryRenderDelete(spec, returningColumns);
        if (rendered == null) {
            throw new UnsupportedOperationException(
                    "DELETE RETURNING spec contains syntax not yet supported by jdbc-ast");
        }
        return rendered;
    }

    public static <T> RenderedSql tryRenderDelete(DeleteSpec<T> spec) {
        return tryRenderDelete(spec, List.of());
    }

    public static <T> RenderedSql tryRenderDelete(DeleteSpec<T> spec, List<String> returningColumns) {
        Condition where;
        try {
            where = spec.getWhere() == null ? null : toCondition(spec.getWhere(), null, true);
        } catch (UnsupportedOperationException unsupported) {
            return null;
        }
        io.github.jsbxyyx.jdbcast.renderer.RenderedSql rendered = RENDERER.render(new DeleteStatement(
                spec.getEntityClass(), null, where, returningColumns == null ? List.of() : returningColumns));
        return new RenderedSql(rendered.sql(), rendered.params());
    }

    /** Counts rows produced by the AST-rendered SELECT, including grouped and distinct queries. */
    public static <T, R> RenderedSql renderCount(SelectSpec<T, R> spec) {
        RenderedSql select = renderSelect(spec);
        String sql = select.getSql();
        if (!spec.getSort().isEmpty()) {
            int orderBy = sql.lastIndexOf(" ORDER BY ");
            if (orderBy >= 0) {
                sql = sql.substring(0, orderBy);
            }
        }
        boolean simpleRootProjection = spec.getSelectedExpressions().isEmpty()
                || spec.getSelectedExpressions().stream().allMatch(JdbcAstSelectEngine::isSimpleColumnProjection);
        if (!spec.getJoins().isEmpty()
                && spec.getGroupByExpressions().isEmpty()
                && !spec.isDistinct()
                && spec.getCteDefs().isEmpty()
                && spec.getSubqueryFrom() == null
                && spec.getTableNameOverride() == null
                && simpleRootProjection) {
            EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
            String idProperty = meta.getIdPropertyName();
            if (idProperty != null) {
                String idColumn = meta.getColumnName(idProperty);
                int from = sql.indexOf(" FROM ");
                if (from >= 0) {
                    String idRef = qualified(spec.getAlias(), idColumn == null ? idProperty : idColumn);
                    return new RenderedSql(
                            "SELECT COUNT(DISTINCT " + idRef + ")" + sql.substring(from), select.getParams());
                }
            }
        }
        return new RenderedSql("SELECT COUNT(*) FROM (" + sql + ") _count_t", select.getParams());
    }

    /** Renders set-operation branches through jdbc-ast and merges their parameters without collisions. */
    public static <R> RenderedSql renderUnion(UnionSpec<R> spec) {
        StringBuilder sql = new StringBuilder();
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        List<UnionSpec.Branch<R>> branches = spec.getBranches();
        for (int i = 0; i < branches.size(); i++) {
            UnionSpec.Branch<R> branch = branches.get(i);
            if (i > 0) {
                sql.append(
                        switch (branch.unionType()) {
                            case UNION -> " UNION ";
                            case UNION_ALL -> " UNION ALL ";
                            case INTERSECT -> " INTERSECT ";
                            case INTERSECT_ALL -> " INTERSECT ALL ";
                            case EXCEPT -> " EXCEPT ";
                            case EXCEPT_ALL -> " EXCEPT ALL ";
                        });
            }
            RenderedSql rendered = renderSelect(branch.spec());
            String branchSql = rendered.getSql();
            if (!branch.spec().getSort().isEmpty()) {
                int orderBy = branchSql.lastIndexOf(" ORDER BY ");
                if (orderBy >= 0) {
                    branchSql = branchSql.substring(0, orderBy);
                }
            }
            RenderedSql renamed = renameParameters(new RenderedSql(branchSql, rendered.getParams()), "u" + i + "_");
            params.putAll(renamed.getParams());
            sql.append(renamed.getSql());
        }
        if (!spec.getSort().isEmpty()) {
            StringJoiner order = new StringJoiner(", ");
            for (JOrder<?> item : spec.getSort().getOrders()) {
                if (!(item.getExpression() instanceof ColumnExpression<?> column)) {
                    throw new UnsupportedOperationException("UNION ORDER BY currently requires column expressions");
                }
                EntityMeta sortMeta =
                        EntityMetaReader.read(column.getPropertyRef().ownerClass());
                String columnName =
                        sortMeta.getColumnName(column.getPropertyRef().propertyName());
                String bare = columnName == null ? column.getPropertyRef().propertyName() : columnName;
                order.add(bare + (item.isAscending() ? " ASC" : " DESC"));
            }
            sql.append(" ORDER BY ").append(order);
        }
        return new RenderedSql(sql.toString(), params);
    }

    public static <T, R> RenderedSql tryRender(SelectSpec<T, R> spec) {
        return tryRender(spec, true);
    }

    private static <T, R> RenderedSql tryRender(SelectSpec<T, R> spec, boolean aliasProjection) {
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        String alias = spec.getAlias();
        Map<String, String> joinedAliases = new LinkedHashMap<>();
        Map<Class<?>, Long> joinedCounts = spec.getJoins().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        JoinSpec::getJoinEntityClass, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        for (JoinSpec join : spec.getJoins()) {
            if (joinedCounts.get(join.getJoinEntityClass()) == 1L
                    && !join.getJoinEntityClass().equals(spec.getEntityClass())) {
                joinedAliases.put(
                        EntityMetaReader.read(join.getJoinEntityClass()).getTableName(), join.getAlias());
            }
        }
        TableRef<T> table = TableRef.of(spec.getEntityClass(), alias);
        SelectBuilder builder = SQL.from(table);
        if (spec.isDistinct()) {
            builder.distinct();
        }
        if (spec.getLockMode() != null) {
            builder.forUpdate(io.github.jsbxyyx.jdbcast.LockMode.valueOf(
                    spec.getLockMode().name()));
        }

        List<Expr<?>> selected = new ArrayList<>();
        if (spec.getSelectedExpressions().isEmpty()) {
            for (Map.Entry<String, String> entry : meta.getPropertyToColumn().entrySet()) {
                selected.add(RawExpr.of(
                        qualified(alias, entry.getValue()) + (aliasProjection ? " AS " + entry.getKey() : "")));
            }
        } else {
            try {
                for (SqlExpression<?> expression : spec.getSelectedExpressions()) {
                    selected.add(
                            aliasProjection
                                    ? toSelectExpr(expression, alias, joinedAliases)
                                    : toExpr(expression, alias, false, joinedAliases));
                }
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }
        builder.select(selected);

        for (JoinSpec join : spec.getJoins()) {
            TableRef<?> joinTable = TableRef.of(join.getJoinEntityClass(), join.getAlias());
            if (join.getJoinType() == JoinType.CROSS) {
                builder.crossJoin(joinTable);
            } else {
                Condition on;
                try {
                    on = join.getOnConditions().stream()
                            .map(predicate -> toCondition(predicate, alias))
                            .reduce((left, right) -> new AndCondition(List.of(left, right)))
                            .orElse(null);
                } catch (UnsupportedOperationException unsupported) {
                    return null;
                }
                if (on == null) {
                    return null;
                }
                switch (join.getJoinType()) {
                    case INNER -> builder.join(joinTable).on(on);
                    case LEFT -> builder.leftJoin(joinTable).on(on);
                    case RIGHT -> builder.rightJoin(joinTable).on(on);
                    case FULL -> builder.fullJoin(joinTable).on(on);
                    case CROSS -> builder.crossJoin(joinTable);
                }
            }
        }

        if (spec.getWhere() != null) {
            try {
                builder.where(toCondition(spec.getWhere(), alias));
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }

        if (!spec.getGroupByExpressions().isEmpty()) {
            try {
                List<Expr<?>> groupBy = new ArrayList<>();
                for (SqlExpression<?> expression : spec.getGroupByExpressions()) {
                    groupBy.add(toExpr(expression, alias, false, joinedAliases));
                }
                builder.groupBy(groupBy.toArray(Expr<?>[]::new));
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }

        if (spec.getHaving() != null) {
            try {
                builder.having(toCondition(spec.getHaving(), alias));
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }

        if (!spec.getSort().isEmpty()) {
            List<OrderItem> orderItems = new ArrayList<>();
            for (JOrder<T> order : spec.getSort()) {
                Expr<?> expr;
                try {
                    expr = toExpr(order.getExpression(), alias, false, joinedAliases);
                } catch (UnsupportedOperationException unsupported) {
                    return null;
                }
                Boolean nullsFirst =
                        switch (order.getNullHandling()) {
                            case NULLS_FIRST -> true;
                            case NULLS_LAST -> false;
                            case NATIVE -> null;
                        };
                orderItems.add(new OrderItem(expr, order.isAscending(), nullsFirst));
            }
            builder.orderBy(orderItems.toArray(OrderItem[]::new));
        }

        SelectStatement statement = builder.build();
        if (spec.getJoins().stream().anyMatch(join -> join.getSubqueryJoin() != null)) {
            List<JoinClause> joins = new ArrayList<>();
            for (int i = 0; i < spec.getJoins().size(); i++) {
                JoinClause joinClause = statement.joins().get(i);
                SelectSpec<?, ?> subquery = spec.getJoins().get(i).getSubqueryJoin();
                if (subquery == null) {
                    joins.add(joinClause);
                    continue;
                }
                boolean rawOn = spec.getJoins().get(i).getOnConditions().stream()
                        .anyMatch(condition -> condition instanceof RawPredicate);
                RenderedSql inner = renderSelect(subquery, rawOn);
                RenderedSql renamed = renameParameters(inner, "sj" + i + "_");
                joins.add(new JoinClause(
                        joinClause.type(),
                        joinClause.table(),
                        joinClause.on(),
                        "(" + renamed.getSql() + ") " + spec.getJoins().get(i).getAlias(),
                        renamed.getParams()));
            }
            statement = new SelectStatement(
                    statement.with(),
                    statement.distinct(),
                    statement.select(),
                    statement.from(),
                    joins,
                    statement.where(),
                    statement.groupBy(),
                    statement.having(),
                    statement.orderBy(),
                    statement.limit(),
                    statement.offset(),
                    statement.lockMode(),
                    statement.setOp(),
                    statement.fromSqlOverride(),
                    statement.fromParams());
        }
        if (spec.getSubqueryFrom() != null) {
            RenderedSql inner = renderSelect(spec.getSubqueryFrom(), false);
            RenderedSql renamed = renameParameters(inner, "sf_");
            LinkedHashMap<String, Object> innerParams = new LinkedHashMap<>(renamed.getParams());
            statement = new SelectStatement(
                    statement.with(),
                    statement.distinct(),
                    statement.select(),
                    statement.from(),
                    statement.joins(),
                    statement.where(),
                    statement.groupBy(),
                    statement.having(),
                    statement.orderBy(),
                    statement.limit(),
                    statement.offset(),
                    statement.lockMode(),
                    statement.setOp(),
                    "(" + renamed.getSql() + ") " + spec.getAlias(),
                    innerParams);
        }
        if (spec.getSubqueryFrom() == null && spec.getTableNameOverride() != null) {
            statement = new SelectStatement(
                    statement.with(),
                    statement.distinct(),
                    statement.select(),
                    statement.from(),
                    statement.joins(),
                    statement.where(),
                    statement.groupBy(),
                    statement.having(),
                    statement.orderBy(),
                    statement.limit(),
                    statement.offset(),
                    statement.lockMode(),
                    statement.setOp(),
                    spec.getTableNameOverride() + " " + spec.getAlias(),
                    Map.of());
        }
        io.github.jsbxyyx.jdbcast.renderer.RenderedSql rendered = RENDERER.render(statement);
        RenderedSql result = new RenderedSql(rendered.sql(), rendered.params());
        if (!spec.getCteDefs().isEmpty()) {
            StringJoiner ctes = new StringJoiner(", ");
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            for (int i = 0; i < spec.getCteDefs().size(); i++) {
                CteDef cte = spec.getCteDefs().get(i);
                RenderedSql body = renameParameters(renderSelect(cte.body(), false), "cte" + i + "_");
                ctes.add(cte.name() + " AS (" + body.getSql() + ")");
                params.putAll(body.getParams());
            }
            params.putAll(result.getParams());
            result = new RenderedSql("WITH " + ctes + " " + result.getSql(), params);
        }
        return result;
    }

    private static boolean isSimpleColumnProjection(SqlExpression<?> expression) {
        if (expression instanceof AliasedExpression<?> aliased) {
            return isSimpleColumnProjection(aliased.getInner());
        }
        return expression instanceof ColumnExpression<?>;
    }

    private static String renderPredicateLhs(SqlExpression<?> expression, String alias, boolean unqualifiedColumns) {
        if (expression instanceof AliasedExpression<?> aliased) {
            return renderPredicateLhs(aliased.getInner(), alias, unqualifiedColumns);
        }
        if (expression instanceof ColumnExpression<?> column) {
            EntityMeta meta = EntityMetaReader.read(column.getPropertyRef().ownerClass());
            String columnName = meta.getColumnName(column.getPropertyRef().propertyName());
            String columnAlias =
                    unqualifiedColumns ? null : (column.getTableAlias() == null ? alias : column.getTableAlias());
            return qualified(
                    columnAlias, columnName == null ? column.getPropertyRef().propertyName() : columnName);
        }
        throw new UnsupportedOperationException("Subquery predicate left-hand side must be a column expression");
    }

    private static RenderedSql renameParameters(RenderedSql rendered, String prefix) {
        String sql = rendered.getSql();
        LinkedHashMap<String, Object> renamedParams = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(rendered.getParams().keySet());
        names.sort((left, right) -> Integer.compare(right.length(), left.length()));
        for (String oldName : names) {
            String newName = prefix + oldName;
            Pattern parameter = Pattern.compile("(?<!:):" + Pattern.quote(oldName) + "(?![A-Za-z0-9_])");
            sql = parameter.matcher(sql).replaceAll(Matcher.quoteReplacement(":" + newName));
            renamedParams.put(newName, rendered.getParams().get(oldName));
        }
        return new RenderedSql(sql, renamedParams);
    }

    private static String qualified(String alias, String column) {
        return alias == null || alias.isBlank() ? column : alias + "." + column;
    }

    private static Expr<?> toSelectExpr(
            SqlExpression<?> expression, String defaultAlias, Map<String, String> joinedAliases) {
        Expr<?> expr = toExpr(expression, defaultAlias, false, joinedAliases);
        if (expression instanceof ColumnExpression<?> column) {
            return expr.as(column.getPropertyRef().propertyName());
        }
        return expr;
    }

    private static Expr<?> toExpr(SqlExpression<?> expression, String defaultAlias) {
        return toExpr(expression, defaultAlias, false, Map.of());
    }

    private static Expr<?> toExpr(SqlExpression<?> expression, String defaultAlias, boolean unqualifiedColumns) {
        return toExpr(expression, defaultAlias, unqualifiedColumns, Map.of());
    }

    private static Expr<?> toExpr(
            SqlExpression<?> expression,
            String defaultAlias,
            boolean unqualifiedColumns,
            Map<String, String> joinedAliases) {
        if (expression instanceof ColumnExpression<?> column) {
            EntityMeta meta = EntityMetaReader.read(column.getPropertyRef().ownerClass());
            String columnName = meta.getColumnName(column.getPropertyRef().propertyName());
            String alias = unqualifiedColumns
                    ? null
                    : (column.getTableAlias() == null
                            ? joinedAliases.getOrDefault(meta.getTableName(), defaultAlias)
                            : column.getTableAlias());
            return RawExpr.of(qualified(
                    alias, columnName == null ? column.getPropertyRef().propertyName() : columnName));
        }
        if (expression instanceof AliasedExpression<?> aliased) {
            return toExpr(aliased.getInner(), defaultAlias, unqualifiedColumns, joinedAliases)
                    .as(aliased.getAlias());
        }
        if (expression instanceof FunctionExpression<?> function) {
            List<Expr<?>> args = new ArrayList<>();
            for (SqlExpression<?> arg : function.getArgs()) {
                args.add(toExpr(arg, defaultAlias, unqualifiedColumns, joinedAliases));
            }
            return new FunctionExpr<>(function.getFunctionName(), args);
        }
        if (expression instanceof AggregateExpression<?> aggregate) {
            List<Expr<?>> args = new ArrayList<>();
            for (SqlExpression<?> arg : aggregate.getArgs()) {
                args.add(toExpr(arg, defaultAlias, unqualifiedColumns, joinedAliases));
            }
            return aggregate.isDistinct()
                    ? AggExpr.distinct(aggregate.getFunctionName(), args.toArray(Expr<?>[]::new))
                    : new AggExpr<>(aggregate.getFunctionName(), args, false);
        }
        if (expression instanceof LiteralExpression<?> literal) {
            return RawExpr.of(literal.getSql());
        }
        if (expression instanceof ScalarSubqueryExpression<?> scalar) {
            RenderedSql inner = renameParameters(
                    renderSelect(scalar.getSubquery(), false),
                    "sq" + Integer.toHexString(System.identityHashCode(scalar.getSubquery())) + "_");
            return RawExpr.of("(" + inner.getSql() + ")", inner.getParams());
        }
        if (expression instanceof ArithmeticExpression arithmetic) {
            return new ArithmeticExpr<>(
                    arithmetic.getOperator().getSql(),
                    toExpr(arithmetic.getLeft(), defaultAlias, unqualifiedColumns, joinedAliases),
                    toExpr(arithmetic.getRight(), defaultAlias, unqualifiedColumns, joinedAliases));
        }
        if (expression instanceof WindowExpression<?> window) {
            Expr<?> function = toExpr(window.getFunction(), defaultAlias, unqualifiedColumns, joinedAliases);
            List<Expr<?>> partitions = new ArrayList<>();
            for (SqlExpression<?> part : window.getPartitionBy()) {
                partitions.add(toExpr(part, defaultAlias, unqualifiedColumns, joinedAliases));
            }
            List<OrderItem> orders = window.getOrderBy().stream()
                    .map(order -> new OrderItem(
                            toExpr(order.getExpression(), defaultAlias, unqualifiedColumns, joinedAliases),
                            order.isAscending(),
                            null))
                    .toList();
            FrameType frameType = window.getFrameType() == null
                    ? null
                    : FrameType.valueOf(window.getFrameType().name());
            FrameBound frameStart = toFrameBound(window.getFrameStart());
            FrameBound frameEnd = toFrameBound(window.getFrameEnd());
            return new WindowExpr<>(function, partitions, orders, frameType, frameStart, frameEnd);
        }
        throw new UnsupportedOperationException("Expression is not migrated to jdbc-ast yet: "
                + expression.getClass().getSimpleName());
    }

    private static FrameBound toFrameBound(WindowExpression.FrameBound bound) {
        if (bound == null) {
            return null;
        }
        return switch (bound.getKind()) {
            case UNBOUNDED_PRECEDING -> FrameBound.unboundedPreceding();
            case PRECEDING -> FrameBound.preceding(bound.getOffset());
            case CURRENT_ROW -> FrameBound.currentRow();
            case FOLLOWING -> FrameBound.following(bound.getOffset());
            case UNBOUNDED_FOLLOWING -> FrameBound.unboundedFollowing();
        };
    }

    private static Condition toCondition(PredicateNode predicate, String alias) {
        return toCondition(predicate, alias, false);
    }

    private static Condition toCondition(PredicateNode predicate, String alias, boolean unqualifiedColumns) {
        if (predicate instanceof OnBuilder.OnEqPredicate onEq) {
            EntityMeta leftMeta = EntityMetaReader.read(onEq.getLeftRef().ownerClass());
            EntityMeta rightMeta = EntityMetaReader.read(onEq.getRightRef().ownerClass());
            String leftColumn = leftMeta.getColumnName(onEq.getLeftRef().propertyName());
            String rightColumn = rightMeta.getColumnName(onEq.getRightRef().propertyName());
            return new CompareCondition(
                    RawExpr.of(qualified(
                            onEq.getLeftAlias(),
                            leftColumn == null ? onEq.getLeftRef().propertyName() : leftColumn)),
                    Op.EQ,
                    RawExpr.of(qualified(
                            onEq.getRightAlias(),
                            rightColumn == null ? onEq.getRightRef().propertyName() : rightColumn)));
        }
        if (predicate instanceof RawPredicate raw) {
            return new RawCondition(raw.getSql(), raw.getParams());
        }
        if (predicate instanceof InSubqueryPredicate inSubquery) {
            RenderedSql inner = renameParameters(
                    renderSelect(inSubquery.getSubquery(), false),
                    "sq" + Integer.toHexString(System.identityHashCode(inSubquery.getSubquery())) + "_");
            String lhs = renderPredicateLhs(inSubquery.getLhs(), alias, unqualifiedColumns);
            String sql = lhs + (inSubquery.isNegated() ? " NOT IN (" : " IN (") + inner.getSql() + ")";
            return new RawCondition(sql, inner.getParams());
        }
        if (predicate instanceof ExistsPredicate exists) {
            RenderedSql inner = renameParameters(
                    renderSelect(exists.getSubquery(), false),
                    "sq" + Integer.toHexString(System.identityHashCode(exists.getSubquery())) + "_");
            return new RawCondition(
                    (exists.isNegated() ? "NOT EXISTS (" : "EXISTS (") + inner.getSql() + ")", inner.getParams());
        }
        if (predicate instanceof ScalarSubqueryPredicate scalar) {
            RenderedSql inner = renameParameters(
                    renderSelect(scalar.getSubquery(), false),
                    "sq" + Integer.toHexString(System.identityHashCode(scalar.getSubquery())) + "_");
            String operator =
                    switch (scalar.getOp()) {
                        case EQ -> "=";
                        case NE -> "<>";
                        case GT -> ">";
                        case GTE -> ">=";
                        case LT -> "<";
                        case LTE -> "<=";
                    };
            String lhs = renderPredicateLhs(scalar.getLhs(), alias, unqualifiedColumns);
            return new RawCondition(lhs + " " + operator + " (" + inner.getSql() + ")", inner.getParams());
        }
        if (predicate instanceof LeafPredicate leaf) {
            if ((leaf.getOp() == LeafPredicate.Op.IN || leaf.getOp() == LeafPredicate.Op.NOT_IN)
                    && leaf.getValue() instanceof Collection<?> values
                    && values.isEmpty()) {
                // SQL does not permit IN (), so use the equivalent constant predicate.
                // This keeps empty dynamic filters deterministic and avoids invalid SQL.
                return RawCondition.of(leaf.getOp() == LeafPredicate.Op.IN ? "1 = 0" : "1 = 1");
            }
            Expr<?> left = toExpr(leaf.getExpression(), alias, unqualifiedColumns);
            return switch (leaf.getOp()) {
                case EQ -> new CompareCondition(left, Op.EQ, leaf.getValue());
                case NE -> new CompareCondition(left, Op.NE, leaf.getValue());
                case GT -> new CompareCondition(left, Op.GT, leaf.getValue());
                case GTE -> new CompareCondition(left, Op.GTE, leaf.getValue());
                case LT -> new CompareCondition(left, Op.LT, leaf.getValue());
                case LTE -> new CompareCondition(left, Op.LTE, leaf.getValue());
                case LIKE -> new LikeCondition(left, String.valueOf(leaf.getValue()), false);
                case LIKE_IC -> new LikeCondition(left, String.valueOf(leaf.getValue()), false, true);
                case IN -> new InCondition(left, false, new ArrayList<>((Collection<?>) leaf.getValue()));
                case NOT_IN -> new InCondition(left, true, new ArrayList<>((Collection<?>) leaf.getValue()));
                case BETWEEN -> new BetweenCondition(left, leaf.getValue(), leaf.getValue2(), false);
                case IS_NULL -> new NullCondition(left, true);
                case IS_NOT_NULL -> new NullCondition(left, false);
            };
        }
        if (predicate instanceof AndPredicate and) {
            return new AndCondition(and.getChildren().stream()
                    .map(child -> toCondition(child, alias, unqualifiedColumns))
                    .toList());
        }
        if (predicate instanceof OrPredicate or) {
            return new OrCondition(or.getChildren().stream()
                    .map(child -> toCondition(child, alias, unqualifiedColumns))
                    .toList());
        }
        if (predicate instanceof NotPredicate not) {
            return new NotCondition(toCondition(not.getChild(), alias, unqualifiedColumns));
        }
        throw new UnsupportedOperationException("Predicate is not migrated to jdbc-ast yet: "
                + predicate.getClass().getSimpleName());
    }
}
