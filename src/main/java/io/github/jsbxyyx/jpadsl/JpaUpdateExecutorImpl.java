package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link JpaUpdateExecutor}.
 *
 * <p>Spring Data JPA discovers this bean as the fragment implementation for
 * any repository that extends {@link JpaUpdateExecutor}.  The {@code EntityManager}
 * is injected by the JPA container; user code never has to manage it directly.
 *
 * @param <T> the root entity type
 */
@Repository
public class JpaUpdateExecutorImpl<T> implements JpaUpdateExecutor<T> {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    @Override
    public int executeUpdate(UpdateBuilder<T> updateBuilder) {
        return updateBuilder.execute(entityManager);
    }
}
