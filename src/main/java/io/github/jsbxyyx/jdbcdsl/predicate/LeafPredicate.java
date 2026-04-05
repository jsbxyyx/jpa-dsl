package io.github.jsbxyyx.jdbcdsl.predicate;

import io.github.jsbxyyx.jdbcdsl.PropertyRef;

import java.util.Collection;

/**
 * An atomic (leaf) predicate referencing a single property.
 */
public final class LeafPredicate implements PredicateNode {

    public enum Op {
        EQ, NE, GT, GTE, LT, LTE,
        LIKE,
        IN, NOT_IN,
        BETWEEN,
        IS_NULL, IS_NOT_NULL
    }

    private final PropertyRef propertyRef;
    private final String tableAlias;
    private final Op op;
    /** Primary value (used for all single-value ops and the lower bound of BETWEEN). */
    private final Object value;
    /** Upper bound of BETWEEN; the collection for IN/NOT_IN. */
    private final Object value2;

    private LeafPredicate(PropertyRef propertyRef, String tableAlias, Op op, Object value, Object value2) {
        this.propertyRef = propertyRef;
        this.tableAlias = tableAlias;
        this.op = op;
        this.value = value;
        this.value2 = value2;
    }

    public static LeafPredicate of(PropertyRef ref, String alias, Op op, Object value) {
        return new LeafPredicate(ref, alias, op, value, null);
    }

    public static LeafPredicate ofBetween(PropertyRef ref, String alias, Object lo, Object hi) {
        return new LeafPredicate(ref, alias, Op.BETWEEN, lo, hi);
    }

    public static LeafPredicate ofIn(PropertyRef ref, String alias, Collection<?> values, boolean negated) {
        return new LeafPredicate(ref, alias, negated ? Op.NOT_IN : Op.IN, values, null);
    }

    public static LeafPredicate ofNullCheck(PropertyRef ref, String alias, boolean isNull) {
        return new LeafPredicate(ref, alias, isNull ? Op.IS_NULL : Op.IS_NOT_NULL, null, null);
    }

    public PropertyRef getPropertyRef() { return propertyRef; }
    public String getTableAlias() { return tableAlias; }
    public Op getOp() { return op; }
    public Object getValue() { return value; }
    public Object getValue2() { return value2; }
}
