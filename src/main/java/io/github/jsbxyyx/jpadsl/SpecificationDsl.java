package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Static factory methods that create JPA {@link Specification} instances using
 * type-safe JPA Static Metamodel attribute references.
 *
 * <p>All field references use {@link SingularAttribute} from the JPA Static Metamodel
 * (e.g. {@code User_.status}), providing compile-time verification.
 *
 * <p>Example:
 * <pre>{@code
 * import static io.github.jsbxyyx.jpadsl.SpecificationDsl.*;
 *
 * Specification<User> spec = and(
 *     eq(User_.status, "ACTIVE"),
 *     like(User_.name, "John"),
 *     or(
 *         lt(User_.age, 18),
 *         gt(User_.age, 65)
 *     )
 * );
 * }</pre>
 */
public final class SpecificationDsl {

    private SpecificationDsl() {
    }

    public static <T, V> Specification<T> eq(SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) -> value == null ? null : cb.equal(root.get(attr), value);
    }

    public static <T, V> Specification<T> ne(SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) -> value == null ? null : cb.notEqual(root.get(attr), value);
    }

    public static <T> Specification<T> isNull(SingularAttribute<? super T, ?> attr) {
        return (root, query, cb) -> cb.isNull(root.get(attr));
    }

    public static <T> Specification<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        return (root, query, cb) -> cb.isNotNull(root.get(attr));
    }

    /**
     * Creates a LIKE predicate. The value is automatically wrapped with {@code %} on both sides.
     */
    public static <T> Specification<T> like(SingularAttribute<? super T, String> attr, String value) {
        return (root, query, cb) ->
                value == null ? null : cb.like(root.get(attr), "%" + value + "%");
    }

    /**
     * Creates a case-insensitive LIKE predicate with automatic {@code %} wrapping.
     */
    public static <T> Specification<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value) {
        return (root, query, cb) ->
                value == null ? null
                        : cb.like(cb.lower(root.get(attr)), "%" + value.toLowerCase() + "%");
    }

    public static <T, V extends Comparable<? super V>> Specification<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) ->
                value == null ? null : cb.greaterThan(root.get(attr), value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) ->
                value == null ? null : cb.greaterThanOrEqualTo(root.get(attr), value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) ->
                value == null ? null : cb.lessThan(root.get(attr), value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        return (root, query, cb) ->
                value == null ? null : cb.lessThanOrEqualTo(root.get(attr), value);
    }

    public static <T, V extends Comparable<? super V>> Specification<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        return (root, query, cb) ->
                (lower == null || upper == null) ? null
                        : cb.between(root.get(attr), lower, upper);
    }

    public static <T, V> Specification<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        return (root, query, cb) ->
                (values == null || values.isEmpty()) ? null : root.get(attr).in(values);
    }

    public static <T, V> Specification<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values) {
        return (root, query, cb) ->
                (values == null || values.isEmpty()) ? null : root.get(attr).in(values).not();
    }

    @SafeVarargs
    public static <T> Specification<T> and(Specification<T>... specs) {
        return Specification.allOf(List.of(specs));
    }

    @SafeVarargs
    public static <T> Specification<T> or(Specification<T>... specs) {
        return Specification.anyOf(List.of(specs));
    }

    public static <T> Specification<T> not(Specification<T> spec) {
        return Specification.not(spec);
    }
}
