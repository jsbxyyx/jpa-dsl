package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.List;
import java.util.Map;

/**
 * Immutable specification of a JDBC UPDATE statement.
 *
 * @param <T> the root entity type
 */
public final class UpdateSpec<T> {

    private final Class<T> entityClass;
    /**
     * Ordered list of (propertyName, value) pairs representing SET assignments.
     */
    private final List<Map.Entry<String, Object>> assignments;
    private final PredicateNode where;

    UpdateSpec(Class<T> entityClass,
               List<Map.Entry<String, Object>> assignments,
               PredicateNode where) {
        this.entityClass = entityClass;
        this.assignments = List.copyOf(assignments);
        this.where = where;
    }

    public Class<T> getEntityClass() { return entityClass; }
    public List<Map.Entry<String, Object>> getAssignments() { return assignments; }
    public PredicateNode getWhere() { return where; }
}
