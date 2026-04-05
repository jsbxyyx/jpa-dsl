package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;

/**
 * Immutable product object produced by {@link DeleteBuilder#build()}.
 *
 * <p>Instances of this class are created exclusively by {@link DeleteBuilder#build()}
 * and consumed by {@link JpaDeleteExecutor#delete(DeleteSpec)}.
 *
 * @param <T> the root entity type
 */
public final class DeleteSpec<T> {

    private final DeleteBuilder<T> delegate;

    DeleteSpec(DeleteBuilder<T> builder) {
        this.delegate = builder;
    }

    /**
     * Executes the delete via the supplied {@link EntityManager}.
     * Package-private — called only by {@link JpaDeleteExecutorImpl}.
     *
     * @param em the entity manager to execute the delete with
     * @return the number of rows affected
     * @throws IllegalStateException if no WHERE clause has been added to the builder
     */
    int execute(EntityManager em) {
        return delegate.execute(em);
    }
}
