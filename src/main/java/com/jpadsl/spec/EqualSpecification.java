package com.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class EqualSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final Object value;

    public EqualSpecification(String field, Object value) {
        this.field = field;
        this.value = value;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.equal(resolvePath(root, field), value);
    }
}
