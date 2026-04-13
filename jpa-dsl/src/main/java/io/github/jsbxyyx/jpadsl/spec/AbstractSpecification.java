package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

/**
 * Abstract base class for all Specification implementations.
 */
public abstract class AbstractSpecification<T> implements Specification<T> {

    @Override
    public abstract Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder);
}
