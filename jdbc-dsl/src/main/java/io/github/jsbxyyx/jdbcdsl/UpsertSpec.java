package io.github.jsbxyyx.jdbcdsl;

import java.util.List;

/**
 * Immutable specification for an UPSERT (INSERT … ON CONFLICT / MERGE) statement.
 *
 * <p>Use {@link UpsertBuilder} to create instances:
 * <pre>{@code
 * UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
 *     .onConflict(TUser::getUsername)   // unique key columns
 *     .doUpdateAll()                     // update all non-conflict columns on match
 *     .build();
 * executor.upsert(spec, userEntity);
 * }</pre>
 *
 * @param <T> the entity type
 */
public final class UpsertSpec<T> {

    private final Class<T> entityClass;
    /** Columns that identify a conflict (unique / primary key). Used for ON CONFLICT / MERGE ON. */
    private final List<String> conflictColumns;
    /**
     * Columns to include in the UPDATE clause.
     * Empty list means "all non-conflict columns" (resolved at render time).
     */
    private final List<String> updateColumns;
    /**
     * When {@code true}, the conflict action is "do nothing" — duplicates are silently skipped
     * without updating the existing row.
     *
     * <p>SQL syntax per dialect:
     * <ul>
     *   <li>PostgreSQL: {@code ON CONFLICT [(target)] DO NOTHING}</li>
     *   <li>MySQL: {@code INSERT IGNORE INTO …}</li>
     *   <li>H2 / Oracle / SQL Server: MERGE with only a WHEN NOT MATCHED INSERT branch</li>
     * </ul>
     */
    private final boolean doNothing;

    UpsertSpec(Class<T> entityClass, List<String> conflictColumns, List<String> updateColumns,
               boolean doNothing) {
        this.entityClass = entityClass;
        this.conflictColumns = List.copyOf(conflictColumns);
        this.updateColumns = List.copyOf(updateColumns);
        this.doNothing = doNothing;
    }

    public Class<T> getEntityClass() { return entityClass; }

    /** Returns the conflict-target column names (may be empty for MySQL). */
    public List<String> getConflictColumns() { return conflictColumns; }

    /**
     * Returns the explicit update columns, or an empty list when all non-conflict columns
     * should be updated (the default resolved by
     * {@link io.github.jsbxyyx.jdbcdsl.dialect.Dialect#resolveUpdateColumns}).
     */
    public List<String> getUpdateColumns() { return updateColumns; }

    /**
     * Returns {@code true} when the conflict action is "do nothing" (skip duplicates silently).
     */
    public boolean isDoNothing() { return doNothing; }
}
