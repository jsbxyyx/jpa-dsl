package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link DeleteStatement}.
 *
 * <pre>{@code
 * TableRef<TUser> u = TableRef.of(TUser.class, "u");
 *
 * DeleteStatement stmt = SQL.deleteFrom(TUser.class)
 *     .where(u.col(TUser::getStatus).eq("INACTIVE"))
 *     .build();
 * }</pre>
 */
public final class DeleteBuilder<T> {

    private final Class<T>     entity;
    private String             tableAlias    = null;
    private Condition          where         = null;
    private final List<String> returningCols = new ArrayList<>();

    DeleteBuilder(Class<T> entity) {
        this.entity = entity;
    }

    public DeleteBuilder<T> alias(String alias) {
        this.tableAlias = alias;
        return this;
    }

    public DeleteBuilder<T> where(Condition condition) {
        this.where = condition;
        return this;
    }

    /** Lambda-style WHERE builder — accumulated predicates are AND-combined. */
    public DeleteBuilder<T> where(Consumer<ConditionBuilder> builder) {
        ConditionBuilder cb = new ConditionBuilder();
        builder.accept(cb);
        Condition c = cb.build();
        if (c != null) this.where = c;
        return this;
    }

    public DeleteBuilder<T> andWhere(Condition condition) {
        this.where = (this.where == null) ? condition : this.where.and(condition);
        return this;
    }

    public DeleteBuilder<T> returning(String... cols) {
        returningCols.addAll(Arrays.asList(cols));
        return this;
    }

    public DeleteStatement build() {
        return new DeleteStatement(entity, tableAlias, where, returningCols);
    }
}
