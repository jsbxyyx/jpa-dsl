package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.expr.LiteralExpr;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for {@link InsertStatement}.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Entity mode</b> — leave assignments empty; provide the entity at execution time.</li>
 *   <li><b>Explicit mode</b> — use {@link #set(SFunction, Object)} / {@link #setExpr(SFunction, Expr)}.</li>
 * </ul>
 *
 * <pre>{@code
 * // Explicit mode
 * InsertStatement stmt = SQL.insertInto(TUser.class)
 *     .set(TUser::getUsername, "alice")
 *     .set(TUser::getAge, 30)
 *     .build();
 * }</pre>
 */
public final class InsertBuilder<T> {

    private final Class<T>              entity;
    private final List<ColumnAssignment> assignments    = new ArrayList<>();
    private final List<String>          returningCols  = new ArrayList<>();

    InsertBuilder(Class<T> entity) {
        this.entity = entity;
    }

    public <V> InsertBuilder<T> set(SFunction<T, V> getter, V value) {
        assignments.add(new ColumnAssignment(getter, new LiteralExpr<>(value)));
        return this;
    }

    public <V> InsertBuilder<T> setExpr(SFunction<T, V> getter, Expr<V> expr) {
        assignments.add(new ColumnAssignment(getter, expr));
        return this;
    }

    public InsertBuilder<T> returning(String... cols) {
        returningCols.addAll(Arrays.asList(cols));
        return this;
    }

    public InsertStatement build() {
        return new InsertStatement(entity, assignments, returningCols);
    }
}
