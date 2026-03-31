package io.github.jsbxyyx.jpadsl.join;

import io.github.jsbxyyx.jpadsl.core.JoinCondition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Strategy interface for building a no-foreign-key entity join inside a JPA Criteria query.
 *
 * <p>Two implementations are provided:
 * <ul>
 *   <li>{@link HibernateJoinStrategy} — uses Hibernate 6's
 *       {@code JpaRoot.join(Class, JoinType)} to produce a true SQL {@code JOIN ... ON},
 *       preserving LEFT JOIN semantics (left-table rows with no match are included).</li>
 *   <li>{@link StandardJoinStrategy} — falls back to {@code CriteriaQuery.from(Class)},
 *       producing a cross-join plus a WHERE condition (effectively an INNER join).</li>
 * </ul>
 *
 * <p>Use {@link JoinStrategyResolver#resolve()} to obtain the appropriate strategy at runtime.
 *
 * @see JoinStrategyResolver
 */
public interface JoinStrategy {

    /**
     * Builds a join between the driving root and a target entity, applying the given ON condition.
     *
     * @param root        the driving entity root
     * @param query       the current criteria query (used by the standard strategy to add a FROM clause)
     * @param cb          the criteria builder
     * @param targetEntity the class of the entity to join
     * @param joinType    INNER, LEFT, or RIGHT
     * @param onCondition the predicate factory for the ON / WHERE condition
     * @param <T>         driving entity type
     * @param <J>         joined entity type
     * @return an additional {@link Predicate} to add to the WHERE clause, or {@code null} when
     *         the condition has already been embedded in the JOIN's ON clause
     */
    <T, J> Predicate buildJoin(Root<T> root,
                                CriteriaQuery<?> query,
                                CriteriaBuilder cb,
                                Class<J> targetEntity,
                                JoinType joinType,
                                JoinCondition<T, J> onCondition);
}
