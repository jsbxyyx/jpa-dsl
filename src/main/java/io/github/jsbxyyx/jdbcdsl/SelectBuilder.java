package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link SelectSpec}.
 *
 * <p>Usage example:
 * <pre>{@code
 * SelectSpec<User, UserDto> spec = SelectBuilder
 *     .from(User.class)
 *     .select(User::getId, User::getName)
 *     .where(w -> w.eq(User::getStatus, "ACTIVE"))
 *     .orderBy(JSort.byAsc(User::getName))
 *     .mapTo(UserDto.class);
 * }</pre>
 */
public final class SelectBuilder<T> {

    private final Class<T> entityClass;
    private final String alias;
    private final List<PropertyRef> selectedProperties = new ArrayList<>();
    private PredicateNode where;
    private final List<JoinSpec> joins = new ArrayList<>();
    private JSort<T> sort = JSort.unsorted();

    private SelectBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    /** Starts building a select from the given entity class with default alias {@code "t"}. */
    public static <T> SelectBuilder<T> from(Class<T> entityClass) {
        return new SelectBuilder<>(entityClass, "t");
    }

    /** Starts building a select from the given entity class with a custom alias. */
    public static <T> SelectBuilder<T> from(Class<T> entityClass, String alias) {
        return new SelectBuilder<>(entityClass, alias);
    }

    /** Specifies which properties to include in the SELECT clause (in order). */
    @SafeVarargs
    public final SelectBuilder<T> select(SFunction<T, ?>... props) {
        for (SFunction<T, ?> prop : props) {
            selectedProperties.add(PropertyRefResolver.resolve(prop));
        }
        return this;
    }

    /** Adds a WHERE predicate via a nested builder. */
    public SelectBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        this.where = wb.buildNode();
        return this;
    }

    /** Sets the WHERE predicate directly using a pre-built AST node. */
    public SelectBuilder<T> where(PredicateNode node) {
        this.where = node;
        return this;
    }

    /** Adds a JOIN clause. */
    public <J> SelectBuilder<T> join(Class<J> joinEntity, String joinAlias,
                                     JoinType type, Consumer<OnBuilder> onConsumer) {
        OnBuilder ob = new OnBuilder();
        onConsumer.accept(ob);
        joins.add(new JoinSpec(joinEntity, joinAlias, type, ob.getConditions()));
        return this;
    }

    /** Sets the ORDER BY clause. */
    public SelectBuilder<T> orderBy(JSort<T> sort) {
        this.sort = sort != null ? sort : JSort.unsorted();
        return this;
    }

    /**
     * Finalizes the spec by specifying the DTO class to project results into.
     *
     * @param dtoClass the result type; must have a constructor matching the selected properties in order
     */
    public <R> SelectSpec<T, R> mapTo(Class<R> dtoClass) {
        return new SelectSpec<>(entityClass, alias, selectedProperties, where, joins, sort, dtoClass);
    }
}
