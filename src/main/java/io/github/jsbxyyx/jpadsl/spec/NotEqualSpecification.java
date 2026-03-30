package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

public class NotEqualSpecification<T, V> extends AbstractSpecification<T> {
    private final SingularAttribute<? super T, V> attr;
    private final V value;

    public NotEqualSpecification(SingularAttribute<? super T, V> attr, V value) {
        this.attr = attr;
        this.value = value;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return value == null ? null : cb.notEqual(root.get(attr), value);
    }
}
