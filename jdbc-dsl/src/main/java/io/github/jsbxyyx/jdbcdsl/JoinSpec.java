package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.List;

/**
 * Describes a single JOIN clause in a select statement.
 */
public final class JoinSpec {

    private final Class<?> joinEntityClass;
    private final String alias;
    private final JoinType joinType;
    private final List<PredicateNode> onConditions;

    public JoinSpec(Class<?> joinEntityClass, String alias, JoinType joinType, List<PredicateNode> onConditions) {
        this.joinEntityClass = joinEntityClass;
        this.alias = alias;
        this.joinType = joinType;
        this.onConditions = List.copyOf(onConditions);
    }

    public Class<?> getJoinEntityClass() { return joinEntityClass; }
    public String getAlias() { return alias; }
    public JoinType getJoinType() { return joinType; }
    public List<PredicateNode> getOnConditions() { return onConditions; }
}
