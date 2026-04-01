package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing JPA batch UPDATE statements using type-safe
 * JPA Static Metamodel attribute references.
 *
 * <p>Usage example:
 * <pre>{@code
 * UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
 *     .set(User_.status, "INACTIVE")
 *     .eq(User_.status, "ACTIVE")
 *     .lt(User_.age, 18)
 *     .build();
 * int affected = userRepository.executeUpdate(update);
 * }</pre>
 *
 * <p>SET clauses always apply (null values set the column to NULL).
 * WHERE predicates are also always added unconditionally; use the {@code condition}
 * overloads to skip a predicate explicitly.
 *
 * @param <T> the root entity type
 */
public class UpdateBuilder<T> {

    private final Class<T> entityClass;
    private final List<SetClause<T, ?>> setClauses = new ArrayList<>();
    private final List<WhereCondition<T>> whereConditions = new ArrayList<>();

    private UpdateBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Creates a new {@code UpdateBuilder} for the given entity class.
     *
     * @param entityClass the JPA entity class to update
     * @param <T>         the entity type
     * @return a new builder instance
     */
    public static <T> UpdateBuilder<T> builder(Class<T> entityClass) {
        return new UpdateBuilder<>(entityClass);
    }

    // ------------------------------------------------------------------ //
    //  SET clauses (null values explicitly set the column to NULL)
    // ------------------------------------------------------------------ //

    /**
     * Adds a SET clause. A {@code null} value will set the column to NULL in the database.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> UpdateBuilder<T> set(SingularAttribute<? super T, V> attr, V value) {
        setClauses.add(new SetClause(attr, value));
        return this;
    }

    // ------------------------------------------------------------------ //
    //  WHERE conditions
    // ------------------------------------------------------------------ //

    public <V> UpdateBuilder<T> eq(SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.equal(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * Unlike {@link #eq(SingularAttribute, Object)}, a {@code null} value is <em>not</em> skipped;
     * the predicate is applied as-is when {@code condition} is {@code true}.
     */
    public <V> UpdateBuilder<T> eq(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.equal(root.get(attr), value));
        }
        return this;
    }

    public <V> UpdateBuilder<T> ne(SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.notEqual(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> UpdateBuilder<T> ne(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.notEqual(root.get(attr), value));
        }
        return this;
    }

    public UpdateBuilder<T> isNull(SingularAttribute<? super T, ?> attr) {
        whereConditions.add((root, cb) -> cb.isNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public UpdateBuilder<T> isNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNull(attr);
        }
        return this;
    }

    public UpdateBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        whereConditions.add((root, cb) -> cb.isNotNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public UpdateBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNotNull(attr);
        }
        return this;
    }

    public UpdateBuilder<T> like(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value + "%" : null;
        whereConditions.add((root, cb) -> cb.like(root.get(attr), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public UpdateBuilder<T> like(SingularAttribute<? super T, String> attr, String value, boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value + "%" : null;
            whereConditions.add((root, cb) -> cb.like(root.get(attr), pattern));
        }
        return this;
    }

    public UpdateBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
        whereConditions.add((root, cb) -> cb.like(cb.lower(root.get(attr)), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public UpdateBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value,
                                           boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
            whereConditions.add((root, cb) -> cb.like(cb.lower(root.get(attr)), pattern));
        }
        return this;
    }

    public <V extends Comparable<? super V>> UpdateBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.greaterThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> UpdateBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.greaterThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> UpdateBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> UpdateBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> UpdateBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.lessThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> UpdateBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.lessThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> UpdateBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> UpdateBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> UpdateBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        whereConditions.add((root, cb) -> cb.between(root.get(attr), lower, upper));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * {@code null} bound values are <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> UpdateBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.between(root.get(attr), lower, upper));
        }
        return this;
    }

    public <V> UpdateBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        whereConditions.add((root, cb) -> root.get(attr).in(values));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> UpdateBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> root.get(attr).in(values));
        }
        return this;
    }

    public <V> UpdateBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values) {
        whereConditions.add((root, cb) -> root.get(attr).in(values).not());
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> UpdateBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> root.get(attr).in(values).not());
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    /**
     * Returns this builder as the update definition object (the builder itself
     * carries all SET and WHERE state and is passed to
     * {@link JpaUpdateExecutor#executeUpdate(UpdateBuilder)}).
     */
    public UpdateBuilder<T> build() {
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Package-private execution (called by JpaUpdateExecutorImpl)
    // ------------------------------------------------------------------ //

    /**
     * Builds a {@link CriteriaUpdate} from the collected SET and WHERE clauses
     * and executes it via the supplied {@link EntityManager}.
     *
     * @param em the entity manager to execute the update with
     * @return the number of rows affected
     * @throws IllegalStateException if no SET clause has been added
     */
    int execute(EntityManager em) {
        if (setClauses.isEmpty()) {
            throw new IllegalStateException(
                    "At least one SET clause is required before calling execute()");
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);

        for (SetClause<T, ?> clause : setClauses) {
            clause.apply(update, root);
        }

        List<Predicate> predicates = new ArrayList<>();
        for (WhereCondition<T> condition : whereConditions) {
            predicates.add(condition.toPredicate(root, cb));
        }
        if (!predicates.isEmpty()) {
            update.where(predicates.toArray(new Predicate[0]));
        }

        return em.createQuery(update).executeUpdate();
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private static final class SetClause<T, V> {
        private final SingularAttribute<? super T, V> attr;
        private final V value;

        SetClause(SingularAttribute<? super T, V> attr, V value) {
            this.attr = attr;
            this.value = value;
        }

        void apply(CriteriaUpdate<T> update, Root<T> root) {
            update.set(root.get(attr), value);
        }
    }

    @FunctionalInterface
    private interface WhereCondition<T> {
        Predicate toPredicate(Root<T> root, CriteriaBuilder cb);
    }
}
