package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link UpdateSpec} (JDBC UPDATE statements).
 *
 * <p>Usage:
 * <pre>{@code
 * UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
 *     .set(TUser::getStatus, "INACTIVE")
 *     .set(TUser::getAge, 30)
 *     .where(w -> w.eq(TUser::getId, 1L))
 *     .build();
 * int affected = executor.executeUpdate(spec);
 * }</pre>
 *
 * @param <T> the root entity type
 */
public final class UpdateBuilder<T> {

    private final Class<T> entityClass;
    private final String alias;
    private final List<Map.Entry<String, Object>> assignments = new ArrayList<>();
    private PredicateNode where;

    private UpdateBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    /** Starts building an UPDATE for the given entity class. */
    public static <T> UpdateBuilder<T> from(Class<T> entityClass) {
        return new UpdateBuilder<>(entityClass, "t");
    }

    /**
     * Adds a SET assignment: {@code column = value} derived from the property getter.
     *
     * @param prop  a method reference identifying the property (and thus the column)
     * @param value the new value for that column
     */
    public UpdateBuilder<T> set(SFunction<T, ?> prop, Object value) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(ref.propertyName(), value));
        return this;
    }

    /** Adds a WHERE predicate via a nested builder. */
    public UpdateBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        this.where = wb.buildNode();
        return this;
    }

    /** Sets the WHERE predicate directly using a pre-built AST node. */
    public UpdateBuilder<T> where(PredicateNode node) {
        this.where = node;
        return this;
    }

    /**
     * Builds the {@link UpdateSpec}.
     *
     * @throws IllegalStateException if no SET assignments were added
     */
    public UpdateSpec<T> build() {
        if (assignments.isEmpty()) {
            throw new IllegalStateException("UpdateBuilder requires at least one set(...) assignment.");
        }
        return new UpdateSpec<>(entityClass, assignments, where);
    }

    /**
     * Builds the {@link UpdateSpec} without requiring any SET assignments.
     * <strong>Use with caution</strong>.
     */
    public UpdateSpec<T> buildUnsafe() {
        return new UpdateSpec<>(entityClass, assignments, where);
    }
}
