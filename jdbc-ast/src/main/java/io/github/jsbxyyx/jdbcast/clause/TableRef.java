package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.expr.ColExpr;
import io.github.jsbxyyx.jdbcast.expr.StarExpr;

/**
 * A typed table reference that anchors all column references.
 *
 * <p>Use {@link #col(SFunction)} to create type-safe column expressions tied to this table's alias.
 * This solves the self-join alias ambiguity problem: two {@code TableRef<TUser>} with different aliases
 * produce distinct, unambiguous {@link ColExpr} instances.
 *
 * <pre>{@code
 * TableRef<TUser> u  = TableRef.of(TUser.class, "u");
 * TableRef<TOrder> o = TableRef.of(TOrder.class, "o");
 *
 * SelectStatement stmt = SQL.from(u)
 *     .join(o).on(u.col(TUser::getId).eq(o.col(TOrder::getUserId)))
 *     .select(u.col(TUser::getUsername), o.col(TOrder::getAmount))
 *     .build();
 * }</pre>
 *
 * @param <T> the entity type this reference points to
 */
public final class TableRef<T> {

    private final Class<T> entityClass;
    private final String alias;

    private TableRef(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias       = alias;
    }

    /** Creates a table reference with an explicit alias (required for joins and self-joins). */
    public static <T> TableRef<T> of(Class<T> entityClass, String alias) {
        return new TableRef<>(entityClass, alias);
    }

    /** Creates a table reference with no alias (suitable for simple single-table queries). */
    public static <T> TableRef<T> of(Class<T> entityClass) {
        return new TableRef<>(entityClass, null);
    }

    public Class<T> entityClass() { return entityClass; }
    public String alias()         { return alias; }

    /**
     * Creates a column expression referencing a property of this entity, qualified by this
     * table's alias.
     *
     * @param getter  a method reference to the entity getter, e.g. {@code TUser::getUsername}
     * @param <V>     the column's Java type
     */
    public <V> ColExpr<V> col(SFunction<T, V> getter) {
        return new ColExpr<>(getter, alias);
    }

    /** {@code tableAlias.*} wildcard for this table. */
    public StarExpr star() {
        return alias != null ? StarExpr.of(alias) : StarExpr.ALL;
    }
}
