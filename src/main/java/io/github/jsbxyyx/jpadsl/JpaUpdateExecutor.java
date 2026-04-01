package io.github.jsbxyyx.jpadsl;

import org.springframework.transaction.annotation.Transactional;

/**
 * Repository mix-in interface that adds type-safe batch UPDATE capability to
 * any Spring Data JPA repository.
 *
 * <p>Extend this interface alongside {@code JpaRepository} and
 * {@code JpaSpecificationExecutor} to gain an {@link #executeUpdate} method
 * without ever touching {@code EntityManager} directly:
 *
 * <pre>{@code
 * @Repository
 * public interface UserRepository extends JpaRepository<User, Long>,
 *         JpaSpecificationExecutor<User>,
 *         JpaUpdateExecutor<User> {
 * }
 * }</pre>
 *
 * <p>Usage in a service:
 * <pre>{@code
 * UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
 *     .set(User_.status, "INACTIVE")
 *     .eq(User_.status, "ACTIVE")
 *     .lt(User_.age, 18)
 *     .build();
 * int affected = userRepository.executeUpdate(update);
 * }</pre>
 *
 * @param <T> the root entity type
 * @see UpdateBuilder
 * @see JpaUpdateExecutorImpl
 */
public interface JpaUpdateExecutor<T> {

    /**
     * Executes a batch update built by {@link UpdateBuilder}.
     *
     * @param updateBuilder the update definition (SET clauses + WHERE conditions)
     * @return the number of rows affected
     * @throws IllegalStateException if no SET clause was added to the builder
     */
    @Transactional
    int executeUpdate(UpdateBuilder<T> updateBuilder);
}
