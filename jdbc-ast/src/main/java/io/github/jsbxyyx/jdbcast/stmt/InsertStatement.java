package io.github.jsbxyyx.jdbcast.stmt;

import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;

import java.util.List;

/**
 * An immutable INSERT statement AST node.
 *
 * <p>When {@code assignments} is empty the executor inserts an entity object
 * provided at execution time via reflection. When {@code assignments} is non-empty
 * the explicit column-value pairs are used.
 */
public final class InsertStatement {

    private final Class<?>              entity;
    private final List<ColumnAssignment> assignments;
    private final List<String>          returningCols;

    public InsertStatement(Class<?> entity,
                    List<ColumnAssignment> assignments,
                    List<String> returningCols) {
        this.entity        = entity;
        this.assignments   = List.copyOf(assignments);
        this.returningCols = List.copyOf(returningCols);
    }

    public Class<?>               entity()        { return entity; }
    public List<ColumnAssignment>  assignments()   { return assignments; }
    public List<String>            returningCols() { return returningCols; }
}
