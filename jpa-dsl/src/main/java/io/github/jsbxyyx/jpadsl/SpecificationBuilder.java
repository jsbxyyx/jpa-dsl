package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.core.JoinCondition;
import io.github.jsbxyyx.jpadsl.join.JoinStrategyResolver;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing JPA {@link Specification} instances using
 * type-safe JPA Static Metamodel attribute references.
 *
 * <p>All field references use {@link SingularAttribute} or plural attribute types
 * from the JPA Static Metamodel (e.g. {@code User_.status}), providing compile-time
 * verification that referenced fields exist and have correct types.
 *
 * <p>Predicate methods always add the predicate unconditionally, even when the supplied
 * value is {@code null}. Use the {@code condition} overloads (e.g.
 * {@link #eq(SingularAttribute, Object, boolean)}) to skip a predicate explicitly.
 *
 * <p>Example:
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.<User>builder()
 *     .eq(User_.status, "ACTIVE")
 *     .like(User_.name, "John")
 *     .gte(User_.age, 18)
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

    public <V> SpecificationBuilder<T> eq(SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.equal(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * Unlike {@link #eq(SingularAttribute, Object)}, a {@code null} value is <em>not</em> skipped;
     * the predicate is applied as-is when {@code condition} is {@code true}.
     */
    public <V> SpecificationBuilder<T> eq(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.equal(root.get(attr), value));
        }
        return this;
    }

    public <V> SpecificationBuilder<T> ne(SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.notEqual(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> SpecificationBuilder<T> ne(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.notEqual(root.get(attr), value));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Null checks
    // ------------------------------------------------------------------ //

    public SpecificationBuilder<T> isNull(SingularAttribute<? super T, ?> attr) {
        specs.add((root, query, cb) -> cb.isNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public SpecificationBuilder<T> isNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNull(attr);
        }
        return this;
    }

    public SpecificationBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        specs.add((root, query, cb) -> cb.isNotNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public SpecificationBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNotNull(attr);
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  String predicates
    // ------------------------------------------------------------------ //

    /**
     * Adds a LIKE predicate. The value is automatically wrapped with {@code %} on both sides,
     * producing a "contains" match.
     */
    public SpecificationBuilder<T> like(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value + "%" : null;
        specs.add((root, query, cb) -> cb.like(root.get(attr), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public SpecificationBuilder<T> like(SingularAttribute<? super T, String> attr, String value, boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value + "%" : null;
            specs.add((root, query, cb) -> cb.like(root.get(attr), pattern));
        }
        return this;
    }

    /**
     * Adds a case-insensitive LIKE predicate with automatic {@code %} wrapping.
     */
    public SpecificationBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
        specs.add((root, query, cb) ->
                cb.like(cb.lower(root.get(attr)), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public SpecificationBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value,
                                                  boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get(attr)), pattern));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Comparison predicates
    // ------------------------------------------------------------------ //

    public <V extends Comparable<? super V>> SpecificationBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.greaterThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> SpecificationBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.greaterThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> SpecificationBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.lessThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> SpecificationBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.lessThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> SpecificationBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        specs.add((root, query, cb) -> cb.between(root.get(attr), lower, upper));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * {@code null} bound values are <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> SpecificationBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper, boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> cb.between(root.get(attr), lower, upper));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IN predicate
    // ------------------------------------------------------------------ //

    public <V> SpecificationBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        specs.add((root, query, cb) -> root.get(attr).in(values));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> SpecificationBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values,
                                          boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> root.get(attr).in(values));
        }
        return this;
    }

    public <V> SpecificationBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values) {
        specs.add((root, query, cb) -> root.get(attr).in(values).not());
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> SpecificationBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values,
                                             boolean condition) {
        if (condition) {
            specs.add((root, query, cb) -> root.get(attr).in(values).not());
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Logical composition
    // ------------------------------------------------------------------ //

    @SafeVarargs
    public final SpecificationBuilder<T> and(Specification<T>... specifications) {
        specs.add(Specification.allOf(Arrays.asList(specifications)));
        return this;
    }

    @SafeVarargs
    public final SpecificationBuilder<T> or(Specification<T>... specifications) {
        specs.add(Specification.anyOf(Arrays.asList(specifications)));
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
     * Adds a join on a singular (ManyToOne / OneToOne) association.
     *
     * @param attr      the metamodel attribute for the association (e.g. {@code Order_.user})
     * @param joinType  INNER, LEFT or RIGHT
     * @param configure callback to add predicates on the joined entity
     * @param <J>       the joined entity type
     */
    public <J> SpecificationBuilder<T> join(SingularAttribute<? super T, J> attr,
                                            JoinType joinType,
                                            JoinSpecification<J> configure) {
        specs.add((root, query, cb) -> {
            Join<T, J> join = root.join(attr, joinType);
            List<Predicate> predicates = new ArrayList<>();
            configure.configure(join, query, cb, predicates);
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        return this;
    }

    /**
     * Adds a join on a plural (OneToMany / ManyToMany) List association.
     *
     * @param attr      the metamodel attribute for the association (e.g. {@code User_.orders})
     * @param joinType  INNER, LEFT or RIGHT
     * @param configure callback to add predicates on the joined entity
     * @param <J>       the joined entity type
     */
    public <J> SpecificationBuilder<T> join(ListAttribute<? super T, J> attr,
                                            JoinType joinType,
                                            JoinSpecification<J> configure) {
        specs.add((root, query, cb) -> {
            ListJoin<T, J> join = root.join(attr, joinType);
            List<Predicate> predicates = new ArrayList<>();
            configure.configure(join, query, cb, predicates);
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        return this;
    }

    /**
     * Adds a join on a plural (OneToMany / ManyToMany) Set association.
     *
     * @param attr      the metamodel attribute for the association
     * @param joinType  INNER, LEFT or RIGHT
     * @param configure callback to add predicates on the joined entity
     * @param <J>       the joined entity type
     */
    public <J> SpecificationBuilder<T> join(SetAttribute<? super T, J> attr,
                                            JoinType joinType,
                                            JoinSpecification<J> configure) {
        specs.add((root, query, cb) -> {
            SetJoin<T, J> join = root.join(attr, joinType);
            List<Predicate> predicates = new ArrayList<>();
            configure.configure(join, query, cb, predicates);
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        return this;
    }

    /**
     * Adds a join on a plural (OneToMany / ManyToMany) Collection association.
     *
     * @param attr      the metamodel attribute for the association
     * @param joinType  INNER, LEFT or RIGHT
     * @param configure callback to add predicates on the joined entity
     * @param <J>       the joined entity type
     */
    public <J> SpecificationBuilder<T> join(CollectionAttribute<? super T, J> attr,
                                            JoinType joinType,
                                            JoinSpecification<J> configure) {
        specs.add((root, query, cb) -> {
            Join<T, J> join = root.join(attr, joinType);
            List<Predicate> predicates = new ArrayList<>();
            configure.configure(join, query, cb, predicates);
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        return this;
    }

    /**
     * Adds a no-foreign-key join between the driving entity and {@code targetEntity}.
     *
     * <p>The runtime strategy is resolved automatically by {@link JoinStrategyResolver}:
     * <ul>
     *   <li>When Hibernate 6+ is present, a true SQL {@code JOIN ... ON} is produced
     *       (full LEFT / RIGHT semantics preserved).</li>
     *   <li>Otherwise the standard JPA fallback is used: {@code targetEntity} is added
     *       as a second FROM clause (cross join) and the ON condition moves to WHERE
     *       (effectively INNER JOIN).</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * SpecificationBuilder.<User>builder()
     *     .join(Order.class, JoinType.LEFT, (userRoot, orderJoin, cb) ->
     *         cb.equal(userRoot.get(User_.id), orderJoin.get(Order_.userId)))
     *     .build();
     * }</pre>
     *
     * @param targetEntity the class of the entity to join
     * @param joinType     INNER, LEFT or RIGHT
     * @param onCondition  factory for the JOIN ON predicate
     * @param <J>          the joined entity type
     */
    public <J> SpecificationBuilder<T> join(Class<J> targetEntity,
                                            JoinType joinType,
                                            JoinCondition<T, J> onCondition) {
        specs.add((root, query, cb) ->
                JoinStrategyResolver.resolve().buildJoin(root, query, cb, targetEntity, joinType, onCondition));
        return this;
    }

    /**
     * Adds a raw {@link Specification} predicate (escape hatch for complex cases).
     */
    public SpecificationBuilder<T> predicate(Specification<T> specification) {
        if (specification != null) {
            specs.add(specification);
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    /**
     * Builds and returns the composed {@link Specification}.
     */
    public Specification<T> build() {
        return Specification.allOf(specs);
    }

    // ------------------------------------------------------------------ //
    //  Inner interface for join configuration
    // ------------------------------------------------------------------ //

    /**
     * Functional interface for configuring predicates on a joined entity.
     *
     * @param <J> the joined entity type
     */
    @FunctionalInterface
    public interface JoinSpecification<J> {
        void configure(Join<?, J> join, CriteriaQuery<?> query,
                       CriteriaBuilder cb, List<Predicate> predicates);
    }
}
