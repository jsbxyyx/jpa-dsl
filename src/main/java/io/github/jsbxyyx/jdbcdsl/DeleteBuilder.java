package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.function.Consumer;

/**
 * Fluent builder for {@link DeleteSpec} (JDBC DELETE statements).
 *
 * <p>Usage:
 * <pre>{@code
 * DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
 *     .where(w -> w.eq(TUser::getStatus, "INACTIVE"))
 *     .build();
 * int affected = executor.executeDelete(spec);
 * }</pre>
 *
 * @param <T> the root entity type
 */
public final class DeleteBuilder<T> {

    private final Class<T> entityClass;
    private final String alias;
    private PredicateNode where;

    private DeleteBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    /** Starts building a DELETE for the given entity class. */
    public static <T> DeleteBuilder<T> from(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass, "t");
    }

    /** Adds a WHERE predicate via a nested builder. */
    public DeleteBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        this.where = wb.buildNode();
        return this;
    }

    /** Sets the WHERE predicate directly using a pre-built AST node. */
    public DeleteBuilder<T> where(PredicateNode node) {
        this.where = node;
        return this;
    }

    /**
     * Builds the {@link DeleteSpec}.
     *
     * @throws IllegalStateException if no WHERE condition was specified (safety guard against
     *         accidental full-table deletes), unless {@code jdbcdsl.allow-empty-where=true}
     *         is configured globally
     */
    public DeleteSpec<T> build() {
        if (where == null && !JdbcDslConfig.isAllowEmptyWhere()) {
            throw new IllegalStateException(
                    "DeleteBuilder requires at least one where(...) condition to prevent accidental full-table deletes. " +
                    "Use buildUnsafe() to bypass, or set jdbcdsl.allow-empty-where=true globally.");
        }
        return new DeleteSpec<>(entityClass, where);
    }

    /**
     * Builds the {@link DeleteSpec} without requiring a WHERE condition.
     * <strong>Use with caution</strong>: this allows DELETE without a WHERE clause (deletes all rows).
     */
    public DeleteSpec<T> buildUnsafe() {
        return new DeleteSpec<>(entityClass, where);
    }
}
