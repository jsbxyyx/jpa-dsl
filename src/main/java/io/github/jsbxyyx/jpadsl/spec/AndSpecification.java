package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

public class AndSpecification<T> extends AbstractSpecification<T> {
    private final List<Specification<T>> specifications;

    @SafeVarargs
    public AndSpecification(Specification<T>... specifications) {
        this.specifications = Arrays.asList(specifications);
    }

    public AndSpecification(List<Specification<T>> specifications) {
        this.specifications = specifications;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate[] predicates = specifications.stream()
                .map(spec -> spec.toPredicate(root, query, cb))
                .toArray(Predicate[]::new);
        return cb.and(predicates);
    }
}
