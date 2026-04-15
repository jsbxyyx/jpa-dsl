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

    SelectSpec(Class<T> entityClass,
               String alias,
               boolean distinct,
               List<SqlExpression<?>> selectedExpressions,
               PredicateNode where,
               List<JoinSpec> joins,
               JSort<T> sort,
               Class<R> dtoClass,
               List<SqlExpression<?>> groupByExpressions,
               PredicateNode having) {
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
}
