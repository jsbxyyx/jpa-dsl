package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link JpaDeleteExecutor}.
 *
 * <p>Instantiated by {@link JpaDeleteFragmentsContributor} and contributed as a
 * repository fragment to every Spring Data JPA repository that extends
 * {@link JpaDeleteExecutor}.
 *
 * @param <T> the root entity type
 */
public class JpaDeleteExecutorImpl<T> implements JpaDeleteExecutor<T> {

    private final EntityManager entityManager;

    public JpaDeleteExecutorImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    @Override
    public int delete(DeleteSpec<T> deleteSpec) {
        return deleteSpec.execute(entityManager);
    }
}
