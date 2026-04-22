package io.github.jsbxyyx.jdbcast.stmt;

import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.condition.Condition;

import java.util.List;

/**
 * An immutable UPDATE statement AST node:
 * {@code UPDATE entity [AS alias] SET col=val [, ...] [WHERE condition] [RETURNING cols]}.
 */
public final class UpdateStatement {

    private final Class<?>               entity;
    private final String                 tableAlias;
    private final List<ColumnAssignment> assignments;
    private final Condition              where;
    private final List<String>           returningCols;

    public UpdateStatement(Class<?> entity,
                    String tableAlias,
                    List<ColumnAssignment> assignments,
                    Condition where,
                    List<String> returningCols) {
        this.entity        = entity;
        this.tableAlias    = tableAlias;
        this.assignments   = List.copyOf(assignments);
        this.where         = where;
        this.returningCols = List.copyOf(returningCols);
    }

    public Class<?>               entity()        { return entity; }
    public String                 tableAlias()    { return tableAlias; }
    public List<ColumnAssignment>  assignments()   { return assignments; }
    public Condition              where()         { return where; }
    public List<String>           returningCols() { return returningCols; }
}
