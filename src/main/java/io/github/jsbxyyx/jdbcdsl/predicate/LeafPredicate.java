package io.github.jsbxyyx.jdbcdsl.predicate;

import io.github.jsbxyyx.jdbcdsl.PropertyRef;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;

import java.util.Collection;

/**
 * An atomic (leaf) predicate referencing a SQL expression on the left-hand side.
 *
 * <p>The left-hand side is represented as a {@link SqlExpression}, which may be a plain
 * column ({@link ColumnExpression}), a scalar function, or an aggregate.
 * Backward-compatible factory methods that accept {@link PropertyRef} are retained.
 */
public final class LeafPredicate implements PredicateNode {

    public enum Op {
        EQ, NE, GT, GTE, LT, LTE,
        LIKE, LIKE_IC,
        IN, NOT_IN,
        BETWEEN,
        IS_NULL, IS_NOT_NULL
    }

    /** The left-hand SQL expression (column, function, or aggregate). */
    private final SqlExpression<?> expression;
    private final Op op;
    /** Primary value (used for all single-value ops and the lower bound of BETWEEN). */
    private final Object value;
    /** Upper bound of BETWEEN; the collection for IN/NOT_IN. */
    private final Object value2;

    private LeafPredicate(SqlExpression<?> expression, Op op, Object value, Object value2) {
        this.expression = expression;
        this.op = op;
        this.value = value;
        this.value2 = value2;
    }

    // ------------------------------------------------------------------ //
    //  Backward-compatible factories (PropertyRef + alias)
    // ------------------------------------------------------------------ //

    public static LeafPredicate of(PropertyRef ref, String alias, Op op, Object value) {
        return new LeafPredicate(ColumnExpression.of(ref, alias), op, value, null);
    }

    public static LeafPredicate ofBetween(PropertyRef ref, String alias, Object lo, Object hi) {
        return new LeafPredicate(ColumnExpression.of(ref, alias), Op.BETWEEN, lo, hi);
    }

    public static LeafPredicate ofIn(PropertyRef ref, String alias, Collection<?> values, boolean negated) {
        return new LeafPredicate(ColumnExpression.of(ref, alias), negated ? Op.NOT_IN : Op.IN, values, null);
    }

    public static LeafPredicate ofNullCheck(PropertyRef ref, String alias, boolean isNull) {
        return new LeafPredicate(ColumnExpression.of(ref, alias), isNull ? Op.IS_NULL : Op.IS_NOT_NULL, null, null);
    }

    // ------------------------------------------------------------------ //
    //  New factories (SqlExpression directly)
    // ------------------------------------------------------------------ //

    /** Creates a single-value predicate with an arbitrary SQL expression on the left. */
    public static LeafPredicate ofExpr(SqlExpression<?> expression, Op op, Object value) {
        return new LeafPredicate(expression, op, value, null);
    }

    /** Creates a BETWEEN predicate with an arbitrary SQL expression on the left. */
    public static LeafPredicate ofExprBetween(SqlExpression<?> expression, Object lo, Object hi) {
        return new LeafPredicate(expression, Op.BETWEEN, lo, hi);
    }

    /** Creates an IN / NOT IN predicate with an arbitrary SQL expression on the left. */
    public static LeafPredicate ofExprIn(SqlExpression<?> expression, Collection<?> values, boolean negated) {
        return new LeafPredicate(expression, negated ? Op.NOT_IN : Op.IN, values, null);
    }

    /** Creates an IS NULL / IS NOT NULL predicate with an arbitrary SQL expression on the left. */
    public static LeafPredicate ofExprNullCheck(SqlExpression<?> expression, boolean isNull) {
        return new LeafPredicate(expression, isNull ? Op.IS_NULL : Op.IS_NOT_NULL, null, null);
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    /** Returns the left-hand SQL expression. */
    public SqlExpression<?> getExpression() {
        return expression;
    }

    /**
     * Convenience accessor that returns the {@link PropertyRef} when the expression is a
     * {@link ColumnExpression}, or {@code null} otherwise.
     */
    public PropertyRef getPropertyRef() {
        if (expression instanceof ColumnExpression<?> col) {
            return col.getPropertyRef();
        }
        return null;
    }

    /**
     * Convenience accessor that returns the explicit table alias when the expression is a
     * {@link ColumnExpression}, or {@code null} otherwise.
     */
    public String getTableAlias() {
        if (expression instanceof ColumnExpression<?> col) {
            return col.getTableAlias();
        }
        return null;
    }

    public Op getOp() { return op; }
    public Object getValue() { return value; }
    public Object getValue2() { return value2; }
}
