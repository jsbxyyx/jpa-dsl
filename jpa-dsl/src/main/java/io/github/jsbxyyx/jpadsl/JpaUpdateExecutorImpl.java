package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link JpaUpdateExecutor}.
 *
 * <p>Instantiated by {@link JpaUpdateFragmentsContributor} and contributed as a
 * repository fragment to every Spring Data JPA repository that extends
 * {@link JpaUpdateExecutor}.
 *
 * @param <T> the root entity type
 */
public class JpaUpdateExecutorImpl<T> implements JpaUpdateExecutor<T> {

    private final EntityManager entityManager;

    public JpaUpdateExecutorImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    @Override
    public int update(UpdateSpec<T> updateSpec) {
        return updateSpec.execute(entityManager);
    }
}
