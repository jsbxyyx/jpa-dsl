package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class LikeSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final String value;

    public LikeSpecification(String field, String value) {
        this.field = field;
        this.value = "%" + value + "%";
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.like(resolvePath(root, field), value);
    }
}
