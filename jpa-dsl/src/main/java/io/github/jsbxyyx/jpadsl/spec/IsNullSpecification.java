package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

public class IsNullSpecification<T> extends AbstractSpecification<T> {
    private final SingularAttribute<? super T, ?> attr;

    public IsNullSpecification(SingularAttribute<? super T, ?> attr) {
        this.attr = attr;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.isNull(root.get(attr));
    }
}
