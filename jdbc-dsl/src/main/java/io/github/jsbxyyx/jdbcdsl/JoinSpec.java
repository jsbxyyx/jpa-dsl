package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.List;

/**
 * Describes a single JOIN clause in a select statement.
 *
 * <p>Supports two variants:
 * <ul>
 *   <li><b>Entity join</b>: {@code JOIN t_table alias ON ...} — built via the
 *       standard constructor; {@link #getSubqueryJoin()} returns {@code null}.</li>
 *   <li><b>Subquery join</b>: {@code JOIN (SELECT ...) alias ON ...} — built via
 *       {@link #ofSubquery}; {@link #getSubqueryJoin()} returns the inner
 *       {@link SelectSpec}. The {@code joinEntityClass} is still required so that
 *       ON-condition property references can be resolved to column names.</li>
 * </ul>
 */
public final class JoinSpec {

    private final Class<?> joinEntityClass;
    private final String alias;
    private final JoinType joinType;
    private final List<PredicateNode> onConditions;
    /**
     * Non-null for subquery joins ({@code JOIN (SELECT ...) alias}).
     * The rendered SQL replaces the table name with the parenthesised subquery.
     */
    private final SelectSpec<?, ?> subqueryJoin;

    /** Constructor for entity-based joins. */
    public JoinSpec(Class<?> joinEntityClass, String alias, JoinType joinType, List<PredicateNode> onConditions) {
        this.joinEntityClass = joinEntityClass;
        this.alias = alias;
        this.joinType = joinType;
        this.onConditions = List.copyOf(onConditions);
        this.subqueryJoin = null;
    }

    /** Factory for subquery-based joins: {@code JOIN (SELECT ...) alias ON ...}. */
    public static JoinSpec ofSubquery(SelectSpec<?, ?> subquery,
                                      Class<?> entityClass,
                                      String alias,
                                      JoinType joinType,
                                      List<PredicateNode> onConditions) {
        JoinSpec js = new JoinSpec(entityClass, alias, joinType, onConditions);
        // Use the private field directly — work around immutability via a companion constructor.
        return new JoinSpec(entityClass, alias, joinType, onConditions, subquery);
    }

    private JoinSpec(Class<?> joinEntityClass, String alias, JoinType joinType,
                     List<PredicateNode> onConditions, SelectSpec<?, ?> subqueryJoin) {
        this.joinEntityClass = joinEntityClass;
        this.alias = alias;
        this.joinType = joinType;
        this.onConditions = List.copyOf(onConditions);
        this.subqueryJoin = subqueryJoin;
    }

    public Class<?> getJoinEntityClass() { return joinEntityClass; }
    public String getAlias() { return alias; }
    public JoinType getJoinType() { return joinType; }
    public List<PredicateNode> getOnConditions() { return onConditions; }

    /**
     * Returns the inner subquery for a subquery join, or {@code null} for plain entity joins.
     */
    public SelectSpec<?, ?> getSubqueryJoin() { return subqueryJoin; }
}
