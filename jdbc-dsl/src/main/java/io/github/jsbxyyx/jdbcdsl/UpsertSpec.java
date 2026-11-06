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

    private final List<ColumnSelection> conflictSelections;
    private final List<ColumnSelection> updateSelections;
    /**
     * When {@code true}, the conflict action is "do nothing" — duplicates are silently skipped
     * without updating the existing row.
     *
     * <p>SQL syntax per dialect:
     * <ul>
     *   <li>PostgreSQL: {@code ON CONFLICT [(target)] DO NOTHING}</li>
     *   <li>MySQL: unsupported because {@code INSERT IGNORE} also suppresses non-duplicate errors</li>
     *   <li>H2 / Oracle / SQL Server: MERGE with only a WHEN NOT MATCHED INSERT branch</li>
     * </ul>
     */
    private final boolean doNothing;

    UpsertSpec(Class<T> entityClass, List<String> conflictColumns, List<String> updateColumns, boolean doNothing) {
        this(
                entityClass,
                conflictColumns.stream()
                        .map(column -> new ColumnSelection(column, null))
                        .toList(),
                updateColumns.stream()
                        .map(column -> new ColumnSelection(column, null))
                        .toList(),
                doNothing,
                true);
    }

    private UpsertSpec(
            Class<T> entityClass,
            List<ColumnSelection> conflictColumns,
            List<ColumnSelection> updateColumns,
            boolean doNothing,
            boolean ignored) {
        this.entityClass = entityClass;
        this.conflictSelections = List.copyOf(conflictColumns);
        this.updateSelections = List.copyOf(updateColumns);
        this.conflictColumns = this.conflictSelections.stream()
                .map(ColumnSelection::columnName)
                .toList();
        this.updateColumns =
                this.updateSelections.stream().map(ColumnSelection::columnName).toList();
        this.doNothing = doNothing;
    }

    static <T> UpsertSpec<T> fromSelections(
            Class<T> entityClass,
            List<ColumnSelection> conflictColumns,
            List<ColumnSelection> updateColumns,
            boolean doNothing) {
        return new UpsertSpec<>(entityClass, conflictColumns, updateColumns, doNothing, true);
    }

    UpsertSpec<T> resolve(EntityMeta meta) {
        if (conflictSelections.stream().noneMatch(selection -> selection.propertyName() != null)
                && updateSelections.stream().noneMatch(selection -> selection.propertyName() != null)) {
            return this;
        }
        return new UpsertSpec<>(
                entityClass,
                conflictSelections.stream()
                        .map(selection -> selection.resolve(meta))
                        .toList(),
                updateSelections.stream()
                        .map(selection -> selection.resolve(meta))
                        .toList(),
                doNothing);
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Returns conflict-target column names as resolved at build time (may be empty for MySQL).
     * An executor resolves typed selections using its own naming strategy.
     */
    public List<String> getConflictColumns() {
        return conflictColumns;
    }

    /**
     * Returns the explicit update columns, or an empty list when all non-conflict columns
     * should be updated (the default resolved by
     * {@link io.github.jsbxyyx.jdbcdsl.dialect.Dialect#resolveUpdateColumns}).
     * Typed selections are re-resolved by the executor's naming strategy.
     */
    public List<String> getUpdateColumns() {
        return updateColumns;
    }

    /**
     * Returns {@code true} when the conflict action is "do nothing" (skip duplicates silently).
     */
    public boolean isDoNothing() {
        return doNothing;
    }
}
