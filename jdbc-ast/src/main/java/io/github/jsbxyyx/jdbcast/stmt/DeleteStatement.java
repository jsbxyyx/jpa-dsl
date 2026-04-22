package io.github.jsbxyyx.jdbcast.stmt;

import io.github.jsbxyyx.jdbcast.condition.Condition;

import java.util.List;

/**
 * An immutable DELETE statement AST node:
 * {@code DELETE FROM entity [AS alias] [WHERE condition] [RETURNING cols]}.
 */
public final class DeleteStatement {

    private final Class<?>     entity;
    private final String       tableAlias;
    private final Condition    where;
    private final List<String> returningCols;

    public DeleteStatement(Class<?> entity,
                    String tableAlias,
                    Condition where,
                    List<String> returningCols) {
        this.entity        = entity;
        this.tableAlias    = tableAlias;
        this.where         = where;
        this.returningCols = List.copyOf(returningCols);
    }

    public Class<?>     entity()        { return entity; }
    public String       tableAlias()    { return tableAlias; }
    public Condition    where()         { return where; }
    public List<String> returningCols() { return returningCols; }
}
