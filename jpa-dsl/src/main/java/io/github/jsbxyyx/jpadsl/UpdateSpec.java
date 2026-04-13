package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;

/**
 * Immutable product object produced by {@link UpdateBuilder#build()}.
 *
 * <p>Instances of this class are created exclusively by {@link UpdateBuilder#build()}
 * and consumed by {@link JpaUpdateExecutor#update(UpdateSpec)}.
 *
 * @param <T> the root entity type
 */
public final class UpdateSpec<T> {

    private final UpdateBuilder<T> delegate;

    UpdateSpec(UpdateBuilder<T> builder) {
        this.delegate = builder;
    }

    /**
     * Executes the update via the supplied {@link EntityManager}.
     * Package-private — called only by {@link JpaUpdateExecutorImpl}.
     *
     * @param em the entity manager to execute the update with
     * @return the number of rows affected
     * @throws IllegalStateException if no SET clause has been added to the builder
     */
    int execute(EntityManager em) {
        return delegate.execute(em);
    }
}
