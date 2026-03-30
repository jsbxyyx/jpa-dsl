package com.jpadsl.core;

import com.jpadsl.spec.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Static factory methods for creating Specification instances.
 * Provides a convenient DSL-style API for building query specifications.
 */
public class SpecificationDsl {

    private SpecificationDsl() {}

    public static <T> Specification<T> equal(String field, Object value) {
        return new EqualSpecification<>(field, value);
    }

    public static <T> Specification<T> notEqual(String field, Object value) {
        return new NotEqualSpecification<>(field, value);
    }

    public static <T> Specification<T> like(String field, String value) {
        return new LikeSpecification<>(field, value);
    }

    public static <T> Specification<T> in(String field, Collection<?> values) {
        return new InSpecification<>(field, values);
    }

    @SafeVarargs
    public static <T, V> Specification<T> in(String field, V... values) {
        return new InSpecification<>(field, Arrays.asList(values));
    }

    public static <T> Specification<T> between(String field, Comparable<?> lower, Comparable<?> upper) {
        return new BetweenSpecification<>(field, lower, upper);
    }

    public static <T> Specification<T> greaterThan(String field, Comparable<?> value) {
        return new GreaterThanSpecification<>(field, value);
    }

    public static <T> Specification<T> lessThan(String field, Comparable<?> value) {
        return new LessThanSpecification<>(field, value);
    }

    public static <T> Specification<T> greaterThanOrEqual(String field, Comparable<?> value) {
        return new GreaterThanOrEqualSpecification<>(field, value);
    }

    public static <T> Specification<T> lessThanOrEqual(String field, Comparable<?> value) {
        return new LessThanOrEqualSpecification<>(field, value);
    }

    public static <T> Specification<T> isNull(String field) {
        return new IsNullSpecification<>(field);
    }

    public static <T> Specification<T> isNotNull(String field) {
        return new IsNotNullSpecification<>(field);
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
