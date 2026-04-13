package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Specification for a LIKE predicate. Value is automatically wrapped with {@code %}.
 */
public class LikeSpecification<T> extends AbstractSpecification<T> {
    private final SingularAttribute<? super T, String> attr;
    private final String value;

    public LikeSpecification(SingularAttribute<? super T, String> attr, String value) {
        this.attr = attr;
        this.value = value;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return value == null ? null : cb.like(root.get(attr), "%" + value + "%");
    }
}
