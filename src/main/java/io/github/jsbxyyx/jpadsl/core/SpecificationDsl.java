package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.spec.*;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

/**
 * Static factory methods for creating Specification instances using type-safe
 * JPA Static Metamodel attribute references.
 */
public class SpecificationDsl {

    private SpecificationDsl() {}

    public static <T, V> Specification<T> equal(SingularAttribute<? super T, V> attr, V value) {
        return new EqualSpecification<>(attr, value);
    }

    public static <T, V> Specification<T> notEqual(SingularAttribute<? super T, V> attr, V value) {
        return new NotEqualSpecification<>(attr, value);
    }

    public static <T> Specification<T> like(SingularAttribute<? super T, String> attr, String value) {
        return new LikeSpecification<>(attr, value);
    }

    public static <T, V> Specification<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        return new InSpecification<>(attr, values);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        return new BetweenSpecification<>(attr, lower, upper);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        return new GreaterThanSpecification<>(attr, value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        return new LessThanSpecification<>(attr, value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        return new GreaterThanOrEqualSpecification<>(attr, value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        return new LessThanOrEqualSpecification<>(attr, value);
    }

    public static <T> Specification<T> isNull(SingularAttribute<? super T, ?> attr) {
        return new IsNullSpecification<>(attr);
    }

    public static <T> Specification<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        return new IsNotNullSpecification<>(attr);
    }

    @SafeVarargs
    public static <T> Specification<T> and(Specification<T>... specs) {
        return new AndSpecification<>(specs);
    }

    public static <T> Specification<T> and(List<Specification<T>> specs) {
        return new AndSpecification<>(specs);
    }

    @SafeVarargs
    public static <T> Specification<T> or(Specification<T>... specs) {
        return new OrSpecification<>(specs);
    }

    public static <T> Specification<T> or(List<Specification<T>> specs) {
        return new OrSpecification<>(specs);
    }

    public static <T> Specification<T> not(Specification<T> spec) {
        return new NotSpecification<>(spec);
    }
}
