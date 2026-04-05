package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.NotPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.OrPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for WHERE clause predicates.
 *
 * <p>All field references use {@link SFunction} method references to guarantee static compilation.
 * Each method has a {@code boolean condition} overload that skips the predicate when {@code false}.
 *
 * @param <T> the root entity type
 */
public final class WhereBuilder<T> {

    private final List<PredicateNode> predicates = new ArrayList<>();
    private final String alias;
    private final Class<T> entityClass;

    WhereBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    // ------------------------------------------------------------------ //
    //  EQ / NE
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> eq(SFunction<T, ?> prop, Object value) {
        return eq(prop, value, true);
    }

    public WhereBuilder<T> eq(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.EQ, value));
        }
        return this;
    }

    /** Cross-entity equality: allows filtering on joined table properties with an explicit alias. */
    public <X> WhereBuilder<T> eq(SFunction<X, ?> prop, String tableAlias, Object value) {
        predicates.add(LeafPredicate.of(PropertyRefResolver.resolve(prop), tableAlias, LeafPredicate.Op.EQ, value));
        return this;
    }

    public WhereBuilder<T> ne(SFunction<T, ?> prop, Object value) {
        return ne(prop, value, true);
    }

    public WhereBuilder<T> ne(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.NE, value));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  GT / GTE / LT / LTE
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> gt(SFunction<T, ?> prop, Object value) {
        return gt(prop, value, true);
    }

    public WhereBuilder<T> gt(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.GT, value));
        }
        return this;
    }

    public WhereBuilder<T> gte(SFunction<T, ?> prop, Object value) {
        return gte(prop, value, true);
    }

    public WhereBuilder<T> gte(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.GTE, value));
        }
        return this;
    }

    public WhereBuilder<T> lt(SFunction<T, ?> prop, Object value) {
        return lt(prop, value, true);
    }

    public WhereBuilder<T> lt(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LT, value));
        }
        return this;
    }

    public WhereBuilder<T> lte(SFunction<T, ?> prop, Object value) {
        return lte(prop, value, true);
    }

    public WhereBuilder<T> lte(SFunction<T, ?> prop, Object value, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LTE, value));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  LIKE
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> like(SFunction<T, ?> prop, String pattern) {
        return like(prop, pattern, true);
    }

    public WhereBuilder<T> like(SFunction<T, ?> prop, String pattern, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.of(resolve(prop), alias, LeafPredicate.Op.LIKE, "%" + pattern + "%"));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IN / NOT IN
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> in(SFunction<T, ?> prop, Collection<?> values) {
        return in(prop, values, true);
    }

    public WhereBuilder<T> in(SFunction<T, ?> prop, Collection<?> values, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofIn(resolve(prop), alias, values, false));
        }
        return this;
    }

    public WhereBuilder<T> notIn(SFunction<T, ?> prop, Collection<?> values) {
        return notIn(prop, values, true);
    }

    public WhereBuilder<T> notIn(SFunction<T, ?> prop, Collection<?> values, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofIn(resolve(prop), alias, values, true));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  BETWEEN
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> between(SFunction<T, ?> prop, Object lo, Object hi) {
        return between(prop, lo, hi, true);
    }

    public WhereBuilder<T> between(SFunction<T, ?> prop, Object lo, Object hi, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofBetween(resolve(prop), alias, lo, hi));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  IS NULL / IS NOT NULL
    // ------------------------------------------------------------------ //

    public WhereBuilder<T> isNull(SFunction<T, ?> prop) {
        return isNull(prop, true);
    }

    public WhereBuilder<T> isNull(SFunction<T, ?> prop, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofNullCheck(resolve(prop), alias, true));
        }
        return this;
    }

    public WhereBuilder<T> isNotNull(SFunction<T, ?> prop) {
        return isNotNull(prop, true);
    }

    public WhereBuilder<T> isNotNull(SFunction<T, ?> prop, boolean condition) {
        if (condition) {
            predicates.add(LeafPredicate.ofNullCheck(resolve(prop), alias, false));
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  AND / OR composition
    // ------------------------------------------------------------------ //

    /** Adds a nested AND group. */
    public WhereBuilder<T> and(Consumer<WhereBuilder<T>> nested) {
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, alias);
        nested.accept(sub);
        PredicateNode node = sub.buildNode();
        if (node != null) {
            predicates.add(node);
        }
        return this;
    }

    /** Adds a nested OR group. */
    public WhereBuilder<T> or(Consumer<WhereBuilder<T>> nested) {
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, alias);
        nested.accept(sub);
        if (!sub.predicates.isEmpty()) {
            predicates.add(new OrPredicate(new ArrayList<>(sub.predicates)));
        }
        return this;
    }

    /** Adds a pre-built predicate node. */
    public WhereBuilder<T> predicate(PredicateNode node) {
        if (node != null) {
            predicates.add(node);
        }
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Internal
    // ------------------------------------------------------------------ //

    PredicateNode buildNode() {
        if (predicates.isEmpty()) {
            return null;
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        return new AndPredicate(new ArrayList<>(predicates));
    }

    private PropertyRef resolve(SFunction<T, ?> fn) {
        return PropertyRefResolver.resolve(fn);
    }
}
