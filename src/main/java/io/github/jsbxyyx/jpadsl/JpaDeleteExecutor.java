package io.github.jsbxyyx.jpadsl;

import org.springframework.transaction.annotation.Transactional;

/**
 * Repository mix-in interface that adds type-safe batch DELETE capability to
 * any Spring Data JPA repository.
 *
 * <p>Extend this interface alongside {@code JpaRepository} and
 * {@code JpaSpecificationExecutor} to gain a {@link #delete} method
 * without ever touching {@code EntityManager} directly:
 *
 * <pre>{@code
 * @Repository
 * public interface UserRepository extends JpaRepository<User, Long>,
 *         JpaSpecificationExecutor<User>,
 *         JpaDeleteExecutor<User> {
 * }
 * }</pre>
 *
 * <p>Usage in a service:
 * <pre>{@code
 * DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
 *     .eq(User_.status, "INACTIVE")
 *     .lt(User_.age, 18)
 *     .build();
 * int affected = userRepository.delete(spec);
 * }</pre>
 *
 * @param <T> the root entity type
 * @see DeleteBuilder
 * @see DeleteSpec
 * @see JpaDeleteExecutorImpl
 */
public interface JpaDeleteExecutor<T> {

    /**
     * Executes a batch delete described by the given {@link DeleteSpec}.
     *
     * @param deleteSpec the delete specification (WHERE conditions)
     * @return the number of rows affected
     * @throws IllegalStateException if no WHERE condition was added to the builder
     */
    @Transactional
    int delete(DeleteSpec<T> deleteSpec);

    /**
     * Executes a batch delete built by {@link DeleteBuilder}.
     *
     * @param deleteBuilder the delete definition (WHERE conditions)
     * @return the number of rows affected
     * @throws IllegalStateException if no WHERE condition was added to the builder
     * @deprecated Use {@link #delete(DeleteSpec)} instead.
     */
    @Deprecated
    @Transactional
    default int executeDelete(DeleteBuilder<T> deleteBuilder) {
        return delete(deleteBuilder.build());
    }
}
