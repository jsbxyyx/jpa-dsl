package io.github.jsbxyyx.jdbcdsl.expr;

import io.github.jsbxyyx.jdbcdsl.PropertyRef;
import io.github.jsbxyyx.jdbcdsl.PropertyRefResolver;
import io.github.jsbxyyx.jdbcdsl.SFunction;

/**
 * A SQL expression that references a single entity property column ({@code alias.column}).
 *
 * <p>The optional {@code tableAlias} overrides automatic alias resolution. When {@code null},
 * {@link io.github.jsbxyyx.jdbcdsl.SqlRenderer} resolves the alias from the query spec.
 *
 * @param <V> the Java type of the column value
 */
public final class ColumnExpression<V> implements SqlExpression<V> {

    private final PropertyRef propertyRef;
    private final String tableAlias;

    private ColumnExpression(PropertyRef propertyRef, String tableAlias) {
        this.propertyRef = propertyRef;
        this.tableAlias = tableAlias;
    }

    /** Creates a column expression from a method reference; alias resolved at render time. */
    public static <T, V> ColumnExpression<V> of(SFunction<T, V> prop) {
        return new ColumnExpression<>(PropertyRefResolver.resolve(prop), null);
    }

    /** Creates a column expression from a method reference with an explicit table alias. */
    public static <T, V> ColumnExpression<V> of(SFunction<T, V> prop, String tableAlias) {
        return new ColumnExpression<>(PropertyRefResolver.resolve(prop), tableAlias);
    }

    /** Creates a column expression from a pre-resolved {@link PropertyRef}; alias resolved at render time. */
    public static <V> ColumnExpression<V> of(PropertyRef ref) {
        return new ColumnExpression<>(ref, null);
    }

    /** Creates a column expression from a pre-resolved {@link PropertyRef} with an explicit table alias. */
    public static <V> ColumnExpression<V> of(PropertyRef ref, String tableAlias) {
        return new ColumnExpression<>(ref, tableAlias);
    }

    /** Returns the resolved property reference. */
    public PropertyRef getPropertyRef() {
        return propertyRef;
    }

    /**
     * Returns the explicit table alias, or {@code null} if it should be resolved automatically
     * from the query spec.
     */
    public String getTableAlias() {
        return tableAlias;
    }
}
