package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for constructing JPA batch DELETE statements using type-safe
 * JPA Static Metamodel attribute references.
 *
 * <p>Usage example:
 * <pre>{@code
 * DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
 *     .eq(User_.status, "INACTIVE")
 *     .lt(User_.age, 18)
 *     .build();
 * int affected = userRepository.delete(spec);
 * }</pre>
 *
 * <p>WHERE predicates are added unconditionally; use the {@code condition}
 * overloads to skip a predicate explicitly.
 *
 * <p>At least one WHERE condition must be added before executing the delete to
 * prevent accidental full-table deletion.
 *
 * @param <T> the root entity type
 */
public class DeleteBuilder<T> {

    private final Class<T> entityClass;
    private final List<WhereCondition<T>> whereConditions = new ArrayList<>();

    private DeleteBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Creates a new {@code DeleteBuilder} for the given entity class.
     *
     * @param entityClass the JPA entity class to delete from
     * @param <T>         the entity type
     * @return a new builder instance
     */
    public static <T> DeleteBuilder<T> builder(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass);
    }

    // ------------------------------------------------------------------ //
    //  WHERE conditions
    // ------------------------------------------------------------------ //

    public <V> DeleteBuilder<T> eq(SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.equal(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * Unlike {@link #eq(SingularAttribute, Object)}, a {@code null} value is <em>not</em> skipped;
     * the predicate is applied as-is when {@code condition} is {@code true}.
     */
    public <V> DeleteBuilder<T> eq(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.equal(root.get(attr), value));
        }
        return this;
    }

    public <V> DeleteBuilder<T> ne(SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.notEqual(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> DeleteBuilder<T> ne(SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.notEqual(root.get(attr), value));
        }
        return this;
    }

    public DeleteBuilder<T> isNull(SingularAttribute<? super T, ?> attr) {
        whereConditions.add((root, cb) -> cb.isNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public DeleteBuilder<T> isNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNull(attr);
        }
        return this;
    }

    public DeleteBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr) {
        whereConditions.add((root, cb) -> cb.isNotNull(root.get(attr)));
        return this;
    }

    /** Conditional overload: adds the predicate only when {@code condition} is {@code true}. */
    public DeleteBuilder<T> isNotNull(SingularAttribute<? super T, ?> attr, boolean condition) {
        if (condition) {
            isNotNull(attr);
        }
        return this;
    }

    public DeleteBuilder<T> like(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value + "%" : null;
        whereConditions.add((root, cb) -> cb.like(root.get(attr), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public DeleteBuilder<T> like(SingularAttribute<? super T, String> attr, String value, boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value + "%" : null;
            whereConditions.add((root, cb) -> cb.like(root.get(attr), pattern));
        }
        return this;
    }

    public DeleteBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value) {
        String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
        whereConditions.add((root, cb) -> cb.like(cb.lower(root.get(attr)), pattern));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; {@code null} produces a {@code null} pattern
     * passed directly to the LIKE expression.
     */
    public DeleteBuilder<T> likeIgnoreCase(SingularAttribute<? super T, String> attr, String value,
                                           boolean condition) {
        if (condition) {
            String pattern = value != null ? "%" + value.toLowerCase() + "%" : null;
            whereConditions.add((root, cb) -> cb.like(cb.lower(root.get(attr)), pattern));
        }
        return this;
    }

    public <V extends Comparable<? super V>> DeleteBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.greaterThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> DeleteBuilder<T> gt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.greaterThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> DeleteBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> DeleteBuilder<T> gte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> DeleteBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.lessThan(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> DeleteBuilder<T> lt(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.lessThan(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> DeleteBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value) {
        whereConditions.add((root, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} value is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> DeleteBuilder<T> lte(
            SingularAttribute<? super T, V> attr, V value, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.lessThanOrEqualTo(root.get(attr), value));
        }
        return this;
    }

    public <V extends Comparable<? super V>> DeleteBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper) {
        whereConditions.add((root, cb) -> cb.between(root.get(attr), lower, upper));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * {@code null} bound values are <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V extends Comparable<? super V>> DeleteBuilder<T> between(
            SingularAttribute<? super T, V> attr, V lower, V upper, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> cb.between(root.get(attr), lower, upper));
        }
        return this;
    }

    public <V> DeleteBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values) {
        whereConditions.add((root, cb) -> root.get(attr).in(values));
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> DeleteBuilder<T> in(SingularAttribute<? super T, V> attr, Collection<V> values, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> root.get(attr).in(values));
        }
        return this;
    }

    public <V> DeleteBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values) {
        whereConditions.add((root, cb) -> root.get(attr).in(values).not());
        return this;
    }

    /**
     * Conditional overload: adds the predicate only when {@code condition} is {@code true}.
     * A {@code null} or empty collection is <em>not</em> skipped; the predicate is applied as-is.
     */
    public <V> DeleteBuilder<T> notIn(SingularAttribute<? super T, V> attr, Collection<V> values, boolean condition) {
        if (condition) {
            whereConditions.add((root, cb) -> root.get(attr).in(values).not());
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    /**
     * Builds and returns an immutable {@link DeleteSpec} that encapsulates all
     * WHERE clauses collected by this builder.
     *
     * @return a new {@link DeleteSpec} ready to be passed to
     *         {@link JpaDeleteExecutor#delete(DeleteSpec)}
     */
    public DeleteSpec<T> build() {
        return new DeleteSpec<>(this);
    }

    // ------------------------------------------------------------------ //
    //  Package-private execution (called by JpaDeleteExecutorImpl)
    // ------------------------------------------------------------------ //

    /**
     * Builds a {@link CriteriaDelete} from the collected WHERE clauses
     * and executes it via the supplied {@link EntityManager}.
     *
     * @param em the entity manager to execute the delete with
     * @return the number of rows affected
     * @throws IllegalStateException if no WHERE clause has been added (safety guard
     *                               against accidental full-table deletion)
     */
    int execute(EntityManager em) {
        if (whereConditions.isEmpty()) {
            throw new IllegalStateException(
                    "At least one WHERE condition is required before calling execute(). "
                    + "Add a condition to avoid accidental full-table deletion.");
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);

        List<Predicate> predicates = new ArrayList<>();
        for (WhereCondition<T> condition : whereConditions) {
            predicates.add(condition.toPredicate(root, cb));
        }
        delete.where(predicates.toArray(new Predicate[0]));

        return em.createQuery(delete).executeUpdate();
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    @FunctionalInterface
    private interface WhereCondition<T> {
        Predicate toPredicate(Root<T> root, CriteriaBuilder cb);
    }
}
