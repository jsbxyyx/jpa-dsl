package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;

public class InSpecification<T, V> extends AbstractSpecification<T> {
    private final SingularAttribute<? super T, V> attr;
    private final Collection<V> values;

    public InSpecification(SingularAttribute<? super T, V> attr, Collection<V> values) {
        this.attr = attr;
        this.values = values;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return (values == null || values.isEmpty()) ? null : root.get(attr).in(values);
    }
}
