package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

public class BetweenSpecification<T, V extends Comparable<? super V>> extends AbstractSpecification<T> {
    private final SingularAttribute<? super T, V> attr;
    private final V lower;
    private final V upper;

    public BetweenSpecification(SingularAttribute<? super T, V> attr, V lower, V upper) {
        this.attr = attr;
        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return (lower == null || upper == null) ? null : cb.between(root.get(attr), lower, upper);
    }
}
