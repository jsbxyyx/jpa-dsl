package com.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class LessThanSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final Comparable value;

    @SuppressWarnings("rawtypes")
    public LessThanSpecification(String field, Comparable value) {
        this.field = field;
        this.value = value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.lessThan(resolvePath(root, field), value);
    }
}
