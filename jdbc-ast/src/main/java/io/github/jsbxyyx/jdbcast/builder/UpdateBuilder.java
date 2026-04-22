package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.expr.LiteralExpr;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link UpdateStatement}.
 *
 * <pre>{@code
 * TableRef<TUser> u = TableRef.of(TUser.class, "u");
 *
 * UpdateStatement stmt = SQL.update(TUser.class)
 *     .set(TUser::getStatus, "INACTIVE")
 *     .set(TUser::getAge, 99)
 *     .where(u.col(TUser::getId).eq(1L))
 *     .build();
 * }</pre>
 */
public final class UpdateBuilder<T> {

    private final Class<T>               entity;
    private String                       tableAlias    = null;
    private final List<ColumnAssignment> assignments   = new ArrayList<>();
    private Condition                    where         = null;
    private final List<String>           returningCols = new ArrayList<>();

    UpdateBuilder(Class<T> entity) {
        this.entity = entity;
    }

    public UpdateBuilder<T> alias(String alias) {
        this.tableAlias = alias;
        return this;
    }

    public <V> UpdateBuilder<T> set(SFunction<T, V> getter, V value) {
        assignments.add(new ColumnAssignment(getter, new LiteralExpr<>(value)));
        return this;
    }

    public <V> UpdateBuilder<T> setExpr(SFunction<T, V> getter, Expr<V> expr) {
        assignments.add(new ColumnAssignment(getter, expr));
        return this;
    }

    public UpdateBuilder<T> where(Condition condition) {
        this.where = condition;
        return this;
    }

    /** Lambda-style WHERE builder — accumulated predicates are AND-combined. */
    public UpdateBuilder<T> where(Consumer<ConditionBuilder> builder) {
        ConditionBuilder cb = new ConditionBuilder();
        builder.accept(cb);
        Condition c = cb.build();
        if (c != null) this.where = c;
        return this;
    }

    public UpdateBuilder<T> andWhere(Condition condition) {
        this.where = (this.where == null) ? condition : this.where.and(condition);
        return this;
    }

    public UpdateBuilder<T> returning(String... cols) {
        returningCols.addAll(Arrays.asList(cols));
        return this;
    }

    public UpdateStatement build() {
        return new UpdateStatement(entity, tableAlias, assignments, where, returningCols);
    }
}
