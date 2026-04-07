package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

/**
 * Immutable specification of a JDBC DELETE statement.
 *
 * @param <T> the root entity type
 */
public final class DeleteSpec<T> {

    private final Class<T> entityClass;
    private final PredicateNode where;

    DeleteSpec(Class<T> entityClass, PredicateNode where) {
        this.entityClass = entityClass;
        this.where = where;
    }

    public Class<T> getEntityClass() { return entityClass; }
    public PredicateNode getWhere() { return where; }
}
