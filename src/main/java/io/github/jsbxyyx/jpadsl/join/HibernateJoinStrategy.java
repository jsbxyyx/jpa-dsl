package io.github.jsbxyyx.jpadsl.join;

import io.github.jsbxyyx.jpadsl.core.JoinCondition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.JpaEntityJoin;
import org.hibernate.query.criteria.JpaFrom;
import org.hibernate.query.sqm.tree.SqmJoinType;

/**
 * {@link JoinStrategy} implementation that leverages Hibernate 6's
 * {@code JpaFrom.join(Class, SqmJoinType)} to produce a true SQL {@code JOIN ... ON} clause.
 *
 * <p>This strategy is only instantiated when Hibernate 6+ is on the classpath
 * (detected by {@link JoinStrategyResolver}).  It preserves full LEFT / RIGHT JOIN semantics:
 * rows from the driving table that have no matching rows in the target table are still included
 * in the result set (with {@code null} values for joined columns).
 *
 * <p>The ON condition is supplied via {@link JoinCondition} and is embedded directly in the
 * join using {@link JpaEntityJoin#on(Predicate)}, so no additional WHERE restriction is
 * returned to the caller.
 */
public class HibernateJoinStrategy implements JoinStrategy {

    @Override
    @SuppressWarnings("unchecked")
    public <T, J> Predicate buildJoin(Root<T> root,
                                       CriteriaQuery<?> query,
                                       CriteriaBuilder cb,
                                       Class<J> targetEntity,
                                       JoinType joinType,
                                       JoinCondition<T, J> onCondition) {
        // At runtime, Hibernate's Root<T> is JpaRoot<T> which extends JpaFrom<T,T>.
        JpaFrom<T, T> jpaFrom = (JpaFrom<T, T>) root;
        JpaEntityJoin<J> entityJoin = jpaFrom.join(targetEntity, SqmJoinType.from(joinType));
        Predicate condition = onCondition.toPredicate(root, entityJoin, cb);
        entityJoin.on(condition);
        // The condition is already embedded in the ON clause — no extra WHERE predicate needed.
        return null;
    }
}
