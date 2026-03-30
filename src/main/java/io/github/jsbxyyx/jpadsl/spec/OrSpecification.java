package io.github.jsbxyyx.jpadsl.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OrSpecification<T> extends AbstractSpecification<T> {
    private final List<Specification<T>> specs;

    @SafeVarargs
    public OrSpecification(Specification<T>... specs) {
        this.specs = Arrays.asList(specs);
    }

    public OrSpecification(List<Specification<T>> specs) {
        this.specs = specs;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = specs.stream()
                .map(spec -> spec.toPredicate(root, query, cb))
                .filter(p -> p != null)
                .collect(Collectors.toList());
        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
