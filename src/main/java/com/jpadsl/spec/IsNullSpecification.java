package com.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class IsNullSpecification<T> extends AbstractSpecification<T> {
    private final String field;

    public IsNullSpecification(String field) {
        this.field = field;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return resolvePath(root, field).isNull();
    }
}
