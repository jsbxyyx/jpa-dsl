package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing JPA {@link Specification} instances.
 *
 * <p>Supports basic predicates (equal, like, between, etc.), logical composition
 * (and / or / not), and joined entity queries.
 *
 * <p>Example:
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.<User>builder()
 *     .equal("status", "ACTIVE")
 *     .like("name", "%John%")
 *     .greaterThan("age", 18)
 *     .build();
 * }</pre>
 *
 * @param <T> the root entity type
 */
public class SpecificationBuilder<T> {

    private final List<Specification<T>> specs = new ArrayList<>();

    private SpecificationBuilder() {
    }

    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
    }

    // ------------------------------------------------------------------ //
    //  Equality / Inequality
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> equal(String field, Object value) {
        if (value != null) {
            specs.add((root, query, cb) -> cb.equal(resolvePath(root, field), value));
        }
        return this;
    }

    public SpecificationBuilder<T> notEqual(String field, Object value) {
        if (value != null) {
            specs.add((root, query, cb) -> cb.notEqual(resolvePath(root, field), value));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Null checks
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> isNull(String field) {
        specs.add((root, query, cb) -> cb.isNull(resolvePath(root, field)));
        return this;
    }

    public SpecificationBuilder<T> isNotNull(String field) {
        specs.add((root, query, cb) -> cb.isNotNull(resolvePath(root, field)));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  String predicates
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> like(String field, String pattern) {
        if (pattern != null) {
            specs.add((root, query, cb) -> cb.like(resolvePath(root, field).as(String.class), pattern));
        }
        return this;
    }

    public SpecificationBuilder<T> notLike(String field, String pattern) {
        if (pattern != null) {
            specs.add((root, query, cb) -> cb.notLike(resolvePath(root, field).as(String.class), pattern));
        }
        return this;
    }

    public SpecificationBuilder<T> likeIgnoreCase(String field, String pattern) {
        if (pattern != null) {
            specs.add((root, query, cb) ->
                    cb.like(cb.lower(resolvePath(root, field).as(String.class)), pattern.toLowerCase()));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Comparison predicates
    // ------------------------------------------------------------------ //

    public <Y extends Comparable<? super Y>> SpecificationBuilder<T> greaterThan(String field, Y value) {
        if (value != null) {
            specs.add((root, query, cb) -> cb.greaterThan(resolveComparablePath(root, field), value));
        }
        return this;
    }

    public <Y extends Comparable<? super Y>> SpecificationBuilder<T> greaterThanOrEqual(String field, Y value) {
        if (value != null) {
            specs.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(resolveComparablePath(root, field), value));
        }
        return this;
    }

    public <Y extends Comparable<? super Y>> SpecificationBuilder<T> lessThan(String field, Y value) {
        if (value != null) {
            specs.add((root, query, cb) -> cb.lessThan(resolveComparablePath(root, field), value));
        }
        return this;
    }

    public <Y extends Comparable<? super Y>> SpecificationBuilder<T> lessThanOrEqual(String field, Y value) {
        if (value != null) {
            specs.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(resolveComparablePath(root, field), value));
        }
        return this;
    }

    public <Y extends Comparable<? super Y>> SpecificationBuilder<T> between(String field, Y lower, Y upper) {
        if (lower != null && upper != null) {
            specs.add((root, query, cb) ->
                    cb.between(resolveComparablePath(root, field), lower, upper));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IN predicate
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> in(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            specs.add((root, query, cb) -> resolvePath(root, field).in(values));
        }
        return this;
    }

    public SpecificationBuilder<T> in(String field, Object... values) {
        if (values != null && values.length > 0) {
            specs.add((root, query, cb) -> resolvePath(root, field).in(Arrays.asList(values)));
        }
        return this;
    }

    public SpecificationBuilder<T> notIn(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            specs.add((root, query, cb) -> resolvePath(root, field).in(values).not());
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Logical composition
    // ------------------------------------------------------------------ //

    @SafeVarargs
    public final SpecificationBuilder<T> and(Specification<T>... specifications) {
        Specification<T> combined = Specification.allOf(Arrays.asList(specifications));
        specs.add(combined);
        return this;
    }

    @SafeVarargs
    public final SpecificationBuilder<T> or(Specification<T>... specifications) {
        Specification<T> combined = Specification.anyOf(Arrays.asList(specifications));
        specs.add(combined);
        return this;
    }

    public SpecificationBuilder<T> not(Specification<T> specification) {
        specs.add(Specification.not(specification));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Join (associated entity) queries
    // ------------------------------------------------------------------ //

    /**
     * Adds an INNER JOIN predicate on an associated field.
     *
     * @param joinField   the attribute name representing the association
     * @param joinBuilder a consumer that configures a nested {@link SpecificationBuilder}
     *                    operating on the joined type
     * @param <J>         the joined entity type
     */
    public <J> SpecificationBuilder<T> innerJoin(String joinField,
                                                  JoinSpecification<J> joinBuilder) {
        specs.add(buildJoinSpec(joinField, JoinType.INNER, joinBuilder));
        return this;
    }

    public <J> SpecificationBuilder<T> leftJoin(String joinField,
                                                 JoinSpecification<J> joinBuilder) {
        specs.add(buildJoinSpec(joinField, JoinType.LEFT, joinBuilder));
        return this;
    }

    public <J> SpecificationBuilder<T> rightJoin(String joinField,
                                                  JoinSpecification<J> joinBuilder) {
        specs.add(buildJoinSpec(joinField, JoinType.RIGHT, joinBuilder));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Raw predicate (escape hatch)
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> predicate(Specification<T> specification) {
        if (specification != null) {
            specs.add(specification);
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    public Specification<T> build() {
        return Specification.allOf(specs);
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    @SuppressWarnings("unchecked")
    private <X> Path<X> resolvePath(Root<T> root, String field) {
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            return (Path<X>) root.get(parts[0]).get(parts[1]);
        }
        return root.get(field);
    }

    @SuppressWarnings("unchecked")
    private <Y extends Comparable<? super Y>> Path<Y> resolveComparablePath(Root<T> root, String field) {
        return (Path<Y>) (Path<?>) resolvePath(root, field);
    }

    @SuppressWarnings("unchecked")
    private <J> Specification<T> buildJoinSpec(String joinField, JoinType joinType,
                                               JoinSpecification<J> joinBuilder) {
        return (root, query, cb) -> {
            Join<T, J> join = root.join(joinField, joinType);
            List<Predicate> predicates = new ArrayList<>();
            joinBuilder.configure(join, query, cb, predicates);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ------------------------------------------------------------------ //
    //  Inner interface for join configuration
    // ------------------------------------------------------------------ //

    @FunctionalInterface
    public interface JoinSpecification<J> {
        void configure(Join<?, J> join, CriteriaQuery<?> query,
                       CriteriaBuilder cb, List<Predicate> predicates);
    }
}
