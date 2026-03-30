package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

/**
 * Abstract base class for all Specification implementations.
 * Provides utility methods for path resolution (supports nested paths like "orders.status").
 */
public abstract class AbstractSpecification<T> implements Specification<T> {

    /**
     * Resolves a potentially nested field path (e.g., "address.city") from the root.
     */
    @SuppressWarnings("unchecked")
    protected <Y> Path<Y> resolvePath(Root<T> root, String field) {
        String[] parts = field.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return (Path<Y>) path;
    }

    @Override
    public abstract jakarta.persistence.criteria.Predicate toPredicate(
            Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder);
}
