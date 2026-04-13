package io.github.jsbxyyx.jpadsl.join;

import io.github.jsbxyyx.jpadsl.core.JoinCondition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * {@link JoinStrategy} fallback implementation that uses the standard JPA Criteria API.
 *
 * <p>Because standard JPA does not support {@code JOIN ... ON} between unrelated entities,
 * this strategy adds the target entity as a second {@code FROM} clause
 * ({@code CriteriaQuery.from(Class)}) which produces a SQL CROSS JOIN.  The ON condition
 * is then added to the WHERE clause, effectively producing an INNER JOIN.
 *
 * <p><strong>Note:</strong> This strategy does <em>not</em> preserve LEFT / RIGHT JOIN
 * semantics — rows from the driving table without a matching row in the target table will
 * be filtered out by the WHERE condition.  Use {@link HibernateJoinStrategy} when true
 * LEFT / RIGHT JOIN semantics are required.
 */
public class StandardJoinStrategy implements JoinStrategy {

    @Override
    public <T, J> Predicate buildJoin(Root<T> root,
                                       CriteriaQuery<?> query,
                                       CriteriaBuilder cb,
                                       Class<J> targetEntity,
                                       JoinType joinType,
                                       JoinCondition<T, J> onCondition) {
        // Standard JPA does not support JOIN ... ON between unrelated entities.
        // query.from() produces a cross join; the ON condition acts as the join filter in WHERE.
        // NOTE: joinType is intentionally ignored here — the cross-join + WHERE combination
        // always behaves like an INNER join regardless of the requested join type.
        // For true LEFT / RIGHT semantics, use HibernateJoinStrategy (Hibernate 6+).
        Root<J> joinRoot = query.from(targetEntity);
        return onCondition.toPredicate(root, joinRoot, cb);
    }
}
