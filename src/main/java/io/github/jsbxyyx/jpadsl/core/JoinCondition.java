package io.github.jsbxyyx.jpadsl.core;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Functional interface that defines the ON condition for a no-foreign-key join.
 *
 * <p>The {@code root} is the driving entity root, and {@code joinRoot} is the joined entity
 * (either a {@link Root} when using the JPA standard strategy, or a Hibernate 6
 * {@code JpaEntityJoin} — both implement {@link From} and support attribute access in
 * the same way).
 *
 * <p>Example:
 * <pre>{@code
 * JoinCondition<User, Order> condition = (userRoot, orderJoin, cb) ->
 *     cb.equal(userRoot.get(User_.id), orderJoin.get(Order_.userId));
 * }</pre>
 *
 * @param <T> the driving (main) entity type
 * @param <J> the joined entity type
 */
@FunctionalInterface
public interface JoinCondition<T, J> {

    /**
     * Builds the predicate that serves as the JOIN ON condition.
     *
     * @param root      the root of the driving entity
     * @param joinRoot  the root/join of the target entity (supports attribute access via {@code get()})
     * @param cb        the criteria builder
     * @return the predicate for the ON clause
     */
    Predicate toPredicate(Root<T> root, From<?, J> joinRoot, CriteriaBuilder cb);
}
