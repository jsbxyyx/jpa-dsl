package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class BetweenSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final Comparable lower;
    private final Comparable upper;

    @SuppressWarnings("rawtypes")
    public BetweenSpecification(String field, Comparable lower, Comparable upper) {
        this.field = field;
        this.lower = lower;
        this.upper = upper;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.between(resolvePath(root, field), lower, upper);
    }
}
