package com.jpadsl.core;

import com.jpadsl.spec.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing complex JPA Specifications using a chainable DSL API.
 *
 * <p>Usage example:
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.<User>builder()
 *     .equal("status", "ACTIVE")
 *     .like("name", "John")
 *     .greaterThan("age", 18)
 *     .build();
 * }</pre>
 */
public class SpecificationBuilder<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();
    private final List<JoinConfig> joinConfigs = new ArrayList<>();

    private SpecificationBuilder() {}

    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
    }

    public SpecificationBuilder<T> equal(String field, Object value) {
        specifications.add(new EqualSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> notEqual(String field, Object value) {
        specifications.add(new NotEqualSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> like(String field, String value) {
        specifications.add(new LikeSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> in(String field, Collection<?> values) {
        specifications.add(new InSpecification<>(field, values));
        return this;
    }

    @SafeVarargs
    public final <V> SpecificationBuilder<T> in(String field, V... values) {
        specifications.add(new InSpecification<>(field, Arrays.asList(values)));
        return this;
    }

    public SpecificationBuilder<T> between(String field, Comparable<?> lower, Comparable<?> upper) {
        specifications.add(new BetweenSpecification<>(field, lower, upper));
        return this;
    }

    public SpecificationBuilder<T> greaterThan(String field, Comparable<?> value) {
        specifications.add(new GreaterThanSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> lessThan(String field, Comparable<?> value) {
        specifications.add(new LessThanSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> greaterThanOrEqual(String field, Comparable<?> value) {
        specifications.add(new GreaterThanOrEqualSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> lessThanOrEqual(String field, Comparable<?> value) {
        specifications.add(new LessThanOrEqualSpecification<>(field, value));
        return this;
    }

    public SpecificationBuilder<T> isNull(String field) {
        specifications.add(new IsNullSpecification<>(field));
        return this;
    }

    public SpecificationBuilder<T> isNotNull(String field) {
        specifications.add(new IsNotNullSpecification<>(field));
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

    /**
     * Adds a join with the given association name and join type.
     */
    public SpecificationBuilder<T> join(String association, jakarta.persistence.criteria.JoinType joinType) {
        joinConfigs.add(new JoinConfig(association, joinType));
        return this;
    }

    /**
     * Adds a join using the wrapper JoinType enum.
     */
    public SpecificationBuilder<T> join(String association, JoinType joinType) {
        joinConfigs.add(new JoinConfig(association, joinType.getJpaJoinType()));
        return this;
    }

    /**
     * Adds a specification directly.
     */
    public SpecificationBuilder<T> add(Specification<T> spec) {
        specifications.add(spec);
        return this;
    }

    /**
     * Builds the final Specification combining all conditions with AND.
     *
     * @return a combined Specification, or null if no conditions were added
     */
    public Specification<T> build() {
        if (specifications.isEmpty() && joinConfigs.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            for (JoinConfig joinConfig : joinConfigs) {
                root.join(joinConfig.association, joinConfig.joinType);
            }
            if (specifications.isEmpty()) {
                return cb.conjunction();
            }
            Predicate[] predicates = specifications.stream()
                    .map(spec -> spec.toPredicate(root, query, cb))
                    .toArray(Predicate[]::new);
            return cb.and(predicates);
        };
    }

    private static class JoinConfig {
        final String association;
        final jakarta.persistence.criteria.JoinType joinType;

        JoinConfig(String association, jakarta.persistence.criteria.JoinType joinType) {
            this.association = association;
            this.joinType = joinType;
        }
    }
}
