package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.ArithmeticExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ParameterExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
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
    private WhereBuilder<T> whBuilder;

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
    public <V> UpdateBuilder<T> set(SFunction<T, V> prop, V value) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(ref.propertyName(), value));
        return this;
    }

    /**
     * Conditional overload: adds the SET assignment only when {@code condition} is {@code true}.
     * When {@code condition} is {@code false} the assignment is completely skipped.
     *
     * @param prop      a method reference identifying the property (and thus the column)
     * @param value     the new value for that column
     * @param condition when {@code false} this call is a no-op
     */
    public <V> UpdateBuilder<T> set(SFunction<T, V> prop, V value, boolean condition) {
        if (condition) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            assignments.add(new AbstractMap.SimpleImmutableEntry<>(ref.propertyName(), value));
        }
        return this;
    }

    /**
     * Adds a SET assignment where the right-hand side is a SQL expression.
     *
     * <p>Use this when the new value should be computed by the database rather than bound
     * as a JDBC parameter. Examples:
     * <pre>{@code
     * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
     *
     * // SET age = age + 1
     * .setExpr(TUser::getAge, lit("age + 1"))
     *
     * // SET score = ROUND(score * 1.1, 2)
     * .setExpr(TUser::getScore, lit("ROUND(score * 1.1, 2)"))
     * }</pre>
     *
     * <p><strong>Warning:</strong> never embed user-controlled data directly in a
     * {@code LiteralExpression}. Use the value-based {@link #set(SFunction, Object)} overload
     * and let JDBC bind user input as parameters.
     *
     * @param prop       a method reference identifying the property (and thus the column)
     * @param expression the SQL expression used as the right-hand side of the SET clause
     */
    public UpdateBuilder<T> setExpr(SFunction<T, ?> prop, SqlExpression<?> expression) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(ref.propertyName(), expression));
        return this;
    }

    /** Adds another numeric column to the target column: {@code target = target + otherColumn}. */
    public UpdateBuilder<T> add(SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp) {
        return arithmetic(prop, ColumnExpression.of(otherProp), ArithmeticExpression.Operator.ADD);
    }

    /** Conditional column-to-column addition; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> add(
            SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp, boolean condition) {
        return condition ? add(prop, otherProp) : this;
    }

    /** Adds a numeric constant to the target column using a bound JDBC parameter. */
    public UpdateBuilder<T> add(SFunction<T, ? extends Number> prop, Number value) {
        return arithmetic(prop, ParameterExpression.of(requireNumericValue(value)), ArithmeticExpression.Operator.ADD);
    }

    /** Conditional constant addition; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> add(SFunction<T, ? extends Number> prop, Number value, boolean condition) {
        return condition ? add(prop, value) : this;
    }

    /** Subtracts another numeric column from the target column. */
    public UpdateBuilder<T> subtract(SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp) {
        return arithmetic(prop, ColumnExpression.of(otherProp), ArithmeticExpression.Operator.SUBTRACT);
    }

    /** Conditional column-to-column subtraction; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> subtract(
            SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp, boolean condition) {
        return condition ? subtract(prop, otherProp) : this;
    }

    /** Subtracts a numeric constant from the target column using a bound JDBC parameter. */
    public UpdateBuilder<T> subtract(SFunction<T, ? extends Number> prop, Number value) {
        return arithmetic(
                prop, ParameterExpression.of(requireNumericValue(value)), ArithmeticExpression.Operator.SUBTRACT);
    }

    /** Conditional constant subtraction; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> subtract(SFunction<T, ? extends Number> prop, Number value, boolean condition) {
        return condition ? subtract(prop, value) : this;
    }

    /** Multiplies the target column by another numeric column. */
    public UpdateBuilder<T> multiply(SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp) {
        return arithmetic(prop, ColumnExpression.of(otherProp), ArithmeticExpression.Operator.MULTIPLY);
    }

    /** Conditional column-to-column multiplication; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> multiply(
            SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp, boolean condition) {
        return condition ? multiply(prop, otherProp) : this;
    }

    /** Multiplies the target column by a numeric constant using a bound JDBC parameter. */
    public UpdateBuilder<T> multiply(SFunction<T, ? extends Number> prop, Number value) {
        return arithmetic(
                prop, ParameterExpression.of(requireNumericValue(value)), ArithmeticExpression.Operator.MULTIPLY);
    }

    /** Conditional constant multiplication; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> multiply(SFunction<T, ? extends Number> prop, Number value, boolean condition) {
        return condition ? multiply(prop, value) : this;
    }

    /** Divides the target column by another numeric column. */
    public UpdateBuilder<T> divide(SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp) {
        return arithmetic(prop, ColumnExpression.of(otherProp), ArithmeticExpression.Operator.DIVIDE);
    }

    /** Conditional column-to-column division; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> divide(
            SFunction<T, ? extends Number> prop, SFunction<T, ? extends Number> otherProp, boolean condition) {
        return condition ? divide(prop, otherProp) : this;
    }

    /** Divides the target column by a numeric constant using a bound JDBC parameter. */
    public UpdateBuilder<T> divide(SFunction<T, ? extends Number> prop, Number value) {
        return arithmetic(
                prop, ParameterExpression.of(requireNumericValue(value)), ArithmeticExpression.Operator.DIVIDE);
    }

    /** Conditional constant division; skips the assignment when {@code condition} is false. */
    public UpdateBuilder<T> divide(SFunction<T, ? extends Number> prop, Number value, boolean condition) {
        return condition ? divide(prop, value) : this;
    }

    private UpdateBuilder<T> arithmetic(
            SFunction<T, ? extends Number> prop, SqlExpression<?> right, ArithmeticExpression.Operator operator) {
        PropertyRef ref = PropertyRefResolver.resolve(prop);
        String propertyName = ref.propertyName();

        // Consecutive arithmetic operations on the same target are composed into one SET expression.
        // A regular set(...) assignment remains an independent assignment and keeps its existing behavior.
        for (int i = assignments.size() - 1; i >= 0; i--) {
            Map.Entry<String, Object> assignment = assignments.get(i);
            if (assignment.getKey().equals(propertyName)) {
                if (assignment.getValue() instanceof ArithmeticExpression previous) {
                    assignments.set(
                            i,
                            new AbstractMap.SimpleImmutableEntry<>(
                                    propertyName, new ArithmeticExpression(operator, previous, right)));
                    return this;
                }
                break;
            }
        }

        ArithmeticExpression expression = new ArithmeticExpression(operator, ColumnExpression.of(ref), right);
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(propertyName, expression));
        return this;
    }

    private static Number requireNumericValue(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("Arithmetic update value must not be null");
        }
        return value;
    }

    // ------------------------------------------------------------------ //
    //  WHERE – lambda / AST overloads
    // ------------------------------------------------------------------ //

    /** Adds WHERE predicates via a nested builder (accumulated with AND). */
    public UpdateBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(wb());
        return this;
    }

    /** Adds a pre-built predicate AST node to the WHERE clause. */
    public UpdateBuilder<T> where(PredicateNode node) {
        wb().predicate(node);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  WHERE – direct shortcut methods (aligned with JPA UpdateBuilder)
    // ------------------------------------------------------------------ //

    public <V> UpdateBuilder<T> eq(SFunction<T, V> prop, V value) {
        wb().eq(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> eq(SFunction<T, V> prop, V value, boolean condition) {
        wb().eq(prop, value, condition);
        return this;
    }

    public <V> UpdateBuilder<T> ne(SFunction<T, V> prop, V value) {
        wb().ne(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> ne(SFunction<T, V> prop, V value, boolean condition) {
        wb().ne(prop, value, condition);
        return this;
    }

    public UpdateBuilder<T> isNull(SFunction<T, ?> prop) {
        wb().isNull(prop);
        return this;
    }

    public UpdateBuilder<T> isNull(SFunction<T, ?> prop, boolean condition) {
        wb().isNull(prop, condition);
        return this;
    }

    public UpdateBuilder<T> isNotNull(SFunction<T, ?> prop) {
        wb().isNotNull(prop);
        return this;
    }

    public UpdateBuilder<T> isNotNull(SFunction<T, ?> prop, boolean condition) {
        wb().isNotNull(prop, condition);
        return this;
    }

    public UpdateBuilder<T> like(SFunction<T, String> prop, String pattern) {
        wb().like(prop, pattern);
        return this;
    }

    public UpdateBuilder<T> like(SFunction<T, String> prop, String pattern, boolean condition) {
        wb().like(prop, pattern, condition);
        return this;
    }

    public UpdateBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern) {
        wb().likeIgnoreCase(prop, pattern);
        return this;
    }

    public UpdateBuilder<T> likeIgnoreCase(SFunction<T, String> prop, String pattern, boolean condition) {
        wb().likeIgnoreCase(prop, pattern, condition);
        return this;
    }

    public <V> UpdateBuilder<T> gt(SFunction<T, V> prop, V value) {
        wb().gt(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> gt(SFunction<T, V> prop, V value, boolean condition) {
        wb().gt(prop, value, condition);
        return this;
    }

    public <V> UpdateBuilder<T> gte(SFunction<T, V> prop, V value) {
        wb().gte(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> gte(SFunction<T, V> prop, V value, boolean condition) {
        wb().gte(prop, value, condition);
        return this;
    }

    public <V> UpdateBuilder<T> lt(SFunction<T, V> prop, V value) {
        wb().lt(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> lt(SFunction<T, V> prop, V value, boolean condition) {
        wb().lt(prop, value, condition);
        return this;
    }

    public <V> UpdateBuilder<T> lte(SFunction<T, V> prop, V value) {
        wb().lte(prop, value);
        return this;
    }

    public <V> UpdateBuilder<T> lte(SFunction<T, V> prop, V value, boolean condition) {
        wb().lte(prop, value, condition);
        return this;
    }

    public <V> UpdateBuilder<T> between(SFunction<T, V> prop, V lo, V hi) {
        wb().between(prop, lo, hi);
        return this;
    }

    public <V> UpdateBuilder<T> between(SFunction<T, V> prop, V lo, V hi, boolean condition) {
        wb().between(prop, lo, hi, condition);
        return this;
    }

    public <V> UpdateBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values) {
        wb().in(prop, values);
        return this;
    }

    public <V> UpdateBuilder<T> in(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        wb().in(prop, values, condition);
        return this;
    }

    public <V> UpdateBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values) {
        wb().notIn(prop, values);
        return this;
    }

    public <V> UpdateBuilder<T> notIn(SFunction<T, V> prop, Collection<? extends V> values, boolean condition) {
        wb().notIn(prop, values, condition);
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Build
    // ------------------------------------------------------------------ //

    /**
     * Builds the {@link UpdateSpec}.
     *
     * @throws IllegalStateException if no WHERE condition was specified (safety guard against
     *         accidental full-table updates), unless {@code jdbcdsl.allow-empty-where=true}
     *         is configured globally
     */
    public UpdateSpec<T> build() {
        PredicateNode where = whereNode();
        if (where == null && !JdbcDslConfig.isAllowEmptyWhere()) {
            throw new IllegalStateException(
                    "UpdateBuilder requires at least one where(...) condition to prevent accidental full-table updates. "
                            + "Use buildUnsafe() to bypass, or set jdbcdsl.allow-empty-where=true globally.");
        }
        return new UpdateSpec<>(entityClass, assignments, where);
    }

    /**
     * Builds the {@link UpdateSpec} without requiring a WHERE condition.
     * <strong>Use with caution</strong>: this allows UPDATE without a WHERE clause (updates all rows).
     */
    public UpdateSpec<T> buildUnsafe() {
        return new UpdateSpec<>(entityClass, assignments, whereNode());
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
