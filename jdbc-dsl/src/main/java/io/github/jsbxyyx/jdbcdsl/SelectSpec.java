package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.List;

/**
 * Immutable specification of a SELECT query.
 *
 * @param <T> the root entity type
 * @param <R> the DTO (result) type
 */
public final class SelectSpec<T, R> {

    private final Class<T> entityClass;
    private final String alias;
    private final boolean distinct;
    private final List<SqlExpression<?>> selectedExpressions;
    private final PredicateNode where;
    private final List<JoinSpec> joins;
    private final JSort<T> sort;
    private final Class<R> dtoClass;
    private final List<SqlExpression<?>> groupByExpressions;
    private final PredicateNode having;
    /** Ordered list of Common Table Expressions prepended as a {@code WITH} clause. */
    private final List<CteDef> cteDefs;
    /**
     * When non-null, overrides the entity's {@code @Table} name in the FROM clause.
     * Used by {@link SelectBuilder#fromCte(String, Class)} to emit {@code FROM cte_name alias}
     * instead of {@code FROM t_entity_table alias}.
     */
    private final String tableNameOverride;

    /**
     * When non-null, the FROM clause renders as a derived-table subquery:
     * {@code FROM (SELECT ...) alias}.
     * Takes precedence over {@link #tableNameOverride} and the entity's table name.
     */
    private final SelectSpec<?, ?> subqueryFrom;

    /**
     * Row-level locking mode appended at the end of the SELECT SQL, or {@code null} for no locking.
     *
     * @see LockMode
     */
    private final LockMode lockMode;

    /** Full canonical constructor. */
    SelectSpec(Class<T> entityClass,
               String alias,
               boolean distinct,
               List<SqlExpression<?>> selectedExpressions,
               PredicateNode where,
               List<JoinSpec> joins,
               JSort<T> sort,
               Class<R> dtoClass,
               List<SqlExpression<?>> groupByExpressions,
               PredicateNode having,
               List<CteDef> cteDefs,
               String tableNameOverride,
               SelectSpec<?, ?> subqueryFrom,
               LockMode lockMode) {
        this.entityClass = entityClass;
        this.alias = alias;
        this.distinct = distinct;
        this.selectedExpressions = List.copyOf(selectedExpressions);
        this.where = where;
        this.joins = List.copyOf(joins);
        this.sort = sort != null ? sort : JSort.unsorted();
        this.dtoClass = dtoClass;
        this.groupByExpressions = List.copyOf(groupByExpressions);
        this.having = having;
        this.cteDefs = List.copyOf(cteDefs);
        this.tableNameOverride = tableNameOverride;
        this.subqueryFrom = subqueryFrom;
        this.lockMode = lockMode;
    }

    public Class<T> getEntityClass() { return entityClass; }
    public String getAlias() { return alias; }
    public boolean isDistinct() { return distinct; }
    public List<SqlExpression<?>> getSelectedExpressions() { return selectedExpressions; }
    public PredicateNode getWhere() { return where; }
    public List<JoinSpec> getJoins() { return joins; }
    public JSort<T> getSort() { return sort; }
    public Class<R> getDtoClass() { return dtoClass; }
    public List<SqlExpression<?>> getGroupByExpressions() { return groupByExpressions; }
    public PredicateNode getHaving() { return having; }

    /** Returns the CTE definitions attached to this spec's WITH clause (may be empty). */
    public List<CteDef> getCteDefs() { return cteDefs; }

    /**
     * Returns the FROM-clause table-name override, or {@code null} if the entity's
     * {@code @Table} name should be used.
     */
    public String getTableNameOverride() { return tableNameOverride; }

    /**
     * Returns the derived-table subquery used in the FROM clause, or {@code null} when the
     * FROM clause references a plain table or CTE name.
     */
    public SelectSpec<?, ?> getSubqueryFrom() { return subqueryFrom; }

    /**
     * Returns the row-level locking mode, or {@code null} when no locking is requested.
     *
     * @see LockMode
     */
    public LockMode getLockMode() { return lockMode; }

    /** Returns {@code true} if any row-level lock should be appended to the SELECT SQL. */
    public boolean isForUpdate() { return lockMode != null; }
}
