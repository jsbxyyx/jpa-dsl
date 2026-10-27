package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.renderer.MetaResolver;

import java.util.Objects;

/** A column assignment in an INSERT or UPDATE statement: {@code column = expr}. */
public final class ColumnAssignment {

    private final SFunction<?, ?> getter;
    private final Class<?> entityClass;
    private final String propertyName;
    private final Expr<?> value;

    /** Creates an assignment identified by a type-safe getter. */
    public ColumnAssignment(SFunction<?, ?> getter, Expr<?> value) {
        this.getter = Objects.requireNonNull(getter, "getter");
        this.entityClass = null;
        this.propertyName = null;
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * Creates an assignment from entity metadata and a property name.
     * This is useful for adapters whose public DSL already resolved a property to a name.
     */
    public ColumnAssignment(Class<?> entityClass, String propertyName, Expr<?> value) {
        this.getter = null;
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.propertyName = Objects.requireNonNull(propertyName, "propertyName");
        this.value = Objects.requireNonNull(value, "value");
    }

    public SFunction<?, ?> getter() {
        return getter;
    }

    public Expr<?> value() {
        return value;
    }

    /** Resolves the assignment column through the renderer's metadata strategy. */
    public String columnName(MetaResolver meta) {
        return getter != null ? meta.columnName(getter) : meta.columnName(entityClass, propertyName);
    }
}
