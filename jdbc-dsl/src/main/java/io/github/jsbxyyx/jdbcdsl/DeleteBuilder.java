package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.Collection;
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
    private WhereBuilder<T> whBuilder;

    private DeleteBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    /** Starts building a DELETE for the given entity class. */
    public static <T> DeleteBuilder<T> from(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass, "t");
    }

    // ------------------------------------------------------------------ //
    //  WHERE – lambda / AST overloads
    // ------------------------------------------------------------------ //

    /** Adds WHERE predicates via a nested builder (accumulated with AND). */
    public DeleteBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(wb());
        return this;
    }

    /** Adds a pre-built predicate AST node to the WHERE clause. */
    public DeleteBuilder<T> where(PredicateNode node) {
        wb().predicate(node);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  WHERE – direct shortcut methods (aligned with JPA DeleteBuilder)
    // ------------------------------------------------------------------ //

    public <V> DeleteBuilder<T> eq(SFunction<T, V> prop, V value) {
        wb().eq(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> eq(SFunction<T, V> prop, V value, boolean condition) {
        wb().eq(prop, value, condition);
        return this;
    }

    public <V> DeleteBuilder<T> ne(SFunction<T, V> prop, V value) {
        wb().ne(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> ne(SFunction<T, V> prop, V value, boolean condition) {
        wb().ne(prop, value, condition);
        return this;
    }

    public DeleteBuilder<T> isNull(SFunction<T, ?> prop) {
        wb().isNull(prop);
        return this;
    }

    public DeleteBuilder<T> isNull(SFunction<T, ?> prop, boolean condition) {
        wb().isNull(prop, condition);
        return this;
    }

    public DeleteBuilder<T> isNotNull(SFunction<T, ?> prop) {
        wb().isNotNull(prop);
        return this;
    }

    public DeleteBuilder<T> isNotNull(SFunction<T, ?> prop, boolean condition) {
        wb().isNotNull(prop, condition);
        return this;
    }

    public DeleteBuilder<T> like(SFunction<T, String> prop, String pattern) {
        wb().like(prop, pattern);
        return this;
    }

    public DeleteBuilder<T> like(SFunction<T, String> prop, String pattern, boolean condition) {
        wb().like(prop, pattern, condition);
        return this;
    }

    public DeleteBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern) {
        wb().likeIgnoreCase(prop, pattern);
        return this;
    }

    public DeleteBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern, boolean condition) {
        wb().likeIgnoreCase(prop, pattern, condition);
        return this;
    }

    public <V> DeleteBuilder<T> gt(SFunction<T, V> prop, V value) {
        wb().gt(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> gt(SFunction<T, V> prop, V value, boolean condition) {
        wb().gt(prop, value, condition);
        return this;
    }

    public <V> DeleteBuilder<T> gte(SFunction<T, V> prop, V value) {
        wb().gte(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> gte(SFunction<T, V> prop, V value, boolean condition) {
        wb().gte(prop, value, condition);
        return this;
    }

    public <V> DeleteBuilder<T> lt(SFunction<T, V> prop, V value) {
        wb().lt(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> lt(SFunction<T, V> prop, V value, boolean condition) {
        wb().lt(prop, value, condition);
        return this;
    }

    public <V> DeleteBuilder<T> lte(SFunction<T, V> prop, V value) {
        wb().lte(prop, value);
        return this;
    }

    public <V> DeleteBuilder<T> lte(SFunction<T, V> prop, V value, boolean condition) {
        wb().lte(prop, value, condition);
        return this;
    }

    public <V> DeleteBuilder<T> between(SFunction<T, V> prop, V lo, V hi) {
        wb().between(prop, lo, hi);
        return this;
    }

    public <V> DeleteBuilder<T> between(SFunction<T, V> prop, V lo, V hi, boolean condition) {
        wb().between(prop, lo, hi, condition);
        return this;
    }

    public <V> DeleteBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values) {
        wb().in(prop, values);
        return this;
    }

    public <V> DeleteBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        wb().in(prop, values, condition);
        return this;
    }

    public <V> DeleteBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values) {
        wb().notIn(prop, values);
        return this;
    }

    public <V> DeleteBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        wb().notIn(prop, values, condition);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    /**
     * Builds the {@link DeleteSpec}.
     *
     * @throws IllegalStateException if no WHERE condition was specified (safety guard against
     *         accidental full-table deletes), unless {@code jdbcdsl.allow-empty-where=true}
     *         is configured globally
     */
    public DeleteSpec<T> build() {
        PredicateNode where = whereNode();
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
        return new DeleteSpec<>(entityClass, whereNode());
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private WhereBuilder<T> wb() {
        if (whBuilder == null) {
            whBuilder = new WhereBuilder<>(entityClass, alias);
        }
        return whBuilder;
    }

    private PredicateNode whereNode() {
        return whBuilder != null ? whBuilder.buildNode() : null;
    }
}
