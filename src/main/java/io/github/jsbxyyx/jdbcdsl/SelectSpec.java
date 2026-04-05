package io.github.jsbxyyx.jdbcdsl;

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
    private final List<PropertyRef> selectedProperties;
    private final PredicateNode where;
    private final List<JoinSpec> joins;
    private final JSort<T> sort;
    private final Class<R> dtoClass;

    SelectSpec(Class<T> entityClass,
               String alias,
               List<PropertyRef> selectedProperties,
               PredicateNode where,
               List<JoinSpec> joins,
               JSort<T> sort,
               Class<R> dtoClass) {
        this.entityClass = entityClass;
        this.alias = alias;
        this.selectedProperties = List.copyOf(selectedProperties);
        this.where = where;
        this.joins = List.copyOf(joins);
        this.sort = sort != null ? sort : JSort.unsorted();
        this.dtoClass = dtoClass;
    }

    public Class<T> getEntityClass() { return entityClass; }
    public String getAlias() { return alias; }
    public List<PropertyRef> getSelectedProperties() { return selectedProperties; }
    public PredicateNode getWhere() { return where; }
    public List<JoinSpec> getJoins() { return joins; }
    public JSort<T> getSort() { return sort; }
    public Class<R> getDtoClass() { return dtoClass; }
}
