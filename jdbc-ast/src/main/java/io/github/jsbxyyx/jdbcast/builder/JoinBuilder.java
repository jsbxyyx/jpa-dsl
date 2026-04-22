package io.github.jsbxyyx.jdbcast.builder;

import io.github.jsbxyyx.jdbcast.clause.JoinClause;
import io.github.jsbxyyx.jdbcast.clause.JoinType;
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.Condition;

/**
 * Intermediate builder for completing a JOIN clause.
 *
 * <p>Usage:
 * <pre>{@code
 * SQL.from(u)
 *    .join(o).on(u.col(TUser::getId).eq(o.col(TOrder::getUserId)))
 *    .select(...)
 *    .build();
 * }</pre>
 */
public final class JoinBuilder {

    private final SelectBuilder parent;
    private final TableRef<?>   table;
    private final JoinType      type;

    JoinBuilder(SelectBuilder parent, TableRef<?> table, JoinType type) {
        this.parent = parent;
        this.table  = table;
        this.type   = type;
    }

    /** Specifies the ON condition and returns the parent {@link SelectBuilder}. */
    public SelectBuilder on(Condition condition) {
        parent.addJoin(new JoinClause(type, table, condition));
        return parent;
    }
}
