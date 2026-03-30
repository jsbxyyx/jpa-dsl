package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Static factory methods that mirror the {@link SpecificationBuilder} API for
 * concise, import-static usage.
 *
 * <p>Example:
 * <pre>{@code
 * import static io.github.jsbxyyx.jpadsl.SpecificationDsl.*;
 *
 * Specification<User> spec = and(
 *     equal("status", "ACTIVE"),
 *     like("name", "%John%"),
 *     or(
 *         lessThan("age", 18),
 *         greaterThan("age", 65)
 *     )
 * );
 * }</pre>
 */
public final class SpecificationDsl {

    private SpecificationDsl() {
    }

    public static <T> Specification<T> equal(String field, Object value) {
        return (root, query, cb) -> value == null ? null : cb.equal(resolvePath(root, field), value);
    }

    public static <T> Specification<T> notEqual(String field, Object value) {
        return (root, query, cb) -> value == null ? null : cb.notEqual(resolvePath(root, field), value);
    }

    public static <T> Specification<T> isNull(String field) {
        return (root, query, cb) -> cb.isNull(resolvePath(root, field));
    }

    public static <T> Specification<T> isNotNull(String field) {
        return (root, query, cb) -> cb.isNotNull(resolvePath(root, field));
    }

    public static <T> Specification<T> like(String field, String pattern) {
        return (root, query, cb) ->
                pattern == null ? null : cb.like(resolvePath(root, field).as(String.class), pattern);
    }

    public static <T> Specification<T> notLike(String field, String pattern) {
        return (root, query, cb) ->
                pattern == null ? null : cb.notLike(resolvePath(root, field).as(String.class), pattern);
    }

    public static <T> Specification<T> likeIgnoreCase(String field, String pattern) {
        return (root, query, cb) ->
                pattern == null ? null
                        : cb.like(cb.lower(resolvePath(root, field).as(String.class)), pattern.toLowerCase());
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> greaterThan(String field, Y value) {
        return (root, query, cb) ->
                value == null ? null : cb.greaterThan(resolveComparablePath(root, field), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> greaterThanOrEqual(String field, Y value) {
        return (root, query, cb) ->
                value == null ? null
                        : cb.greaterThanOrEqualTo(resolveComparablePath(root, field), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> lessThan(String field, Y value) {
        return (root, query, cb) ->
                value == null ? null : cb.lessThan(resolveComparablePath(root, field), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> lessThanOrEqual(String field, Y value) {
        return (root, query, cb) ->
                value == null ? null
                        : cb.lessThanOrEqualTo(resolveComparablePath(root, field), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> between(String field, Y lower, Y upper) {
        return (root, query, cb) ->
                (lower == null || upper == null) ? null
                        : cb.between(resolveComparablePath(root, field), lower, upper);
    }

    public static <T> Specification<T> in(String field, Collection<?> values) {
        return (root, query, cb) ->
                (values == null || values.isEmpty()) ? null : resolvePath(root, field).in(values);
    }

    public static <T> Specification<T> in(String field, Object... values) {
        return (root, query, cb) ->
                (values == null || values.length == 0) ? null
                        : resolvePath(root, field).in(Arrays.asList(values));
    }

    public static <T> Specification<T> notIn(String field, Collection<?> values) {
        return (root, query, cb) ->
                (values == null || values.isEmpty()) ? null : resolvePath(root, field).in(values).not();
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

    @SuppressWarnings("unchecked")
    private static <T, X> Path<X> resolvePath(
            jakarta.persistence.criteria.Root<T> root, String field) {
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            return (Path<X>) root.get(parts[0]).get(parts[1]);
        }
        return root.get(field);
    }

    @SuppressWarnings("unchecked")
    private static <T, Y extends Comparable<? super Y>> Path<Y> resolveComparablePath(
            jakarta.persistence.criteria.Root<T> root, String field) {
        return (Path<Y>) (Path<?>) resolvePath(root, field);
    }
}
