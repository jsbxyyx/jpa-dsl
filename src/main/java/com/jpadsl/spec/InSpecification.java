package com.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Collection;

public class InSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final Collection<?> values;

    public InSpecification(String field, Collection<?> values) {
        this.field = field;
        this.values = values;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return resolvePath(root, field).in(values);
    }
}
