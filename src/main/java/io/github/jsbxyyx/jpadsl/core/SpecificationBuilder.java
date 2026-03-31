package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.spec.*;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing complex JPA Specifications using a chainable DSL API.
 * Uses type-safe JPA Static Metamodel attribute references.
 *
 * <p>Usage example:
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.<User>builder()
 *     .equal(User_.status, "ACTIVE")
 *     .like(User_.name, "John")
 *     .gte(User_.age, 18)
 *     .build();
 * }</pre>
 */
public class SpecificationBuilder<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    private SpecificationBuilder() {}

    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
    }

    public <V> SpecificationBuilder<T> equal(SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new EqualSpecification<>(attr, value));
        return this;
    }

    public <V> SpecificationBuilder<T> notEqual(SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new NotEqualSpecification<>(attr, value));
        return this;
    }

    public SpecificationBuilder<T> like(SingularAttribute<? super T, String> attr, String value) {
        specifications.add(new LikeSpecification<>(attr, value));
        return this;
    }

    public <V> SpecificationBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        specifications.add(new InSpecification<>(attr, values));
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        specifications.add(new BetweenSpecification<>(attr, lower, upper));
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new GreaterThanSpecification<>(attr, value));
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new LessThanSpecification<>(attr, value));
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new GreaterThanOrEqualSpecification<>(attr, value));
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        specifications.add(new LessThanOrEqualSpecification<>(attr, value));
        return this;
    }

    public SpecificationBuilder<T> isNull(SingularAttribute<? super T, ?> attr) {
        specifications.add(new IsNullSpecification<>(attr));
        return this;
    }

    public SpecificationBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        specifications.add(new IsNotNullSpecification<>(attr));
        return this;
    }

    @SafeVarargs
    public final SpecificationBuilder<T> or(Specification<T>... specs) {
        specifications.add(new OrSpecification<>(specs));
        return this;
    }

    public SpecificationBuilder<T> or(List<Specification<T>> specs) {
        specifications.add(new OrSpecification<>(specs));
        return this;
    }

    @SafeVarargs
    public final SpecificationBuilder<T> and(Specification<T>... specs) {
        specifications.add(new AndSpecification<>(specs));
        return this;
    }

    public SpecificationBuilder<T> and(List<Specification<T>> specs) {
        specifications.add(new AndSpecification<>(specs));
        return this;
    }

    public SpecificationBuilder<T> not(Specification<T> spec) {
        specifications.add(new NotSpecification<>(spec));
        return this;
    }

    public SpecificationBuilder<T> add(Specification<T> spec) {
        specifications.add(spec);
        return this;
    }

    public Specification<T> build() {
        if (specifications.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate[] predicates = specifications.stream()
                    .map(spec -> spec.toPredicate(root, query, cb))
                    .filter(p -> p != null)
                    .toArray(Predicate[]::new);
            return cb.and(predicates);
        };
    }
}
