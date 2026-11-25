package io.github.jsbxyyx.jdbcdsl;

import java.util.List;

/**
 * Immutable specification for a JDBC INSERT statement.
 *
 * <p>If {@link #getColumnNames()} is empty, all entity columns are used (with the
 * {@code @GeneratedValue(strategy=IDENTITY)} primary-key column excluded automatically).
 * If column names are supplied explicitly, exactly those columns are inserted and the
 * caller is responsible for deciding whether to include the primary key.
 * A spec built with {@link InsertBuilder#of(Object)} holds a reference to the entity;
 * its current values are read when the insert is executed.
 *
 * @param <T> the root entity type
 */
public final class InsertSpec<T> {

    private final Class<T> entityClass;
    private final List<String> columnNames;
    private final List<ColumnSelection> selections;
    private final boolean ignore;
    private final T entity;

    private InsertSpec(Class<T> entityClass, List<ColumnSelection> selections, boolean ignore, T entity) {
        this.entityClass = entityClass;
        this.selections = List.copyOf(selections);
        this.ignore = ignore;
        this.entity = entity;
        this.columnNames =
                this.selections.stream().map(ColumnSelection::columnName).toList();
    }

    /**
     * Creates a spec that inserts all entity columns (skipping IDENTITY-generated pk).
     */
    public static <T> InsertSpec<T> of(Class<T> entityClass) {
        return new InsertSpec<>(entityClass, List.of(), false, null);
    }

    /**
     * Creates a spec that inserts exactly the given column names.
     */
    public static <T> InsertSpec<T> of(Class<T> entityClass, List<String> columnNames) {
        return new InsertSpec<>(
                entityClass,
                columnNames.stream()
                        .map(column -> new ColumnSelection(column, null))
                        .toList(),
                false,
                null);
    }

    static <T> InsertSpec<T> fromSelections(Class<T> entityClass, List<ColumnSelection> selections) {
        return fromSelections(entityClass, selections, false, null);
    }

    static <T> InsertSpec<T> fromSelections(
            Class<T> entityClass, List<ColumnSelection> selections, boolean ignore, T entity) {
        return new InsertSpec<>(entityClass, selections, ignore, entity);
    }

    InsertSpec<T> resolve(EntityMeta meta) {
        if (selections.stream().noneMatch(selection -> selection.propertyName() != null)) {
            return this;
        }
        return new InsertSpec<>(
                entityClass,
                selections.stream()
                        .map(selection -> new ColumnSelection(selection.resolve(meta), null))
                        .toList(),
                ignore,
                entity);
    }

    /** The entity class. */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /** The bound entity, or {@code null} for a reusable, unbound specification. */
    public T getEntity() {
        return entity;
    }

    /** Whether to use the database's native INSERT IGNORE semantics. */
    public boolean isIgnore() {
        return ignore;
    }

    /**
     * The explicit column names to insert, or an empty list meaning "use all entity columns
     * (minus IDENTITY-generated pk)".
     * For typed builder selections, these reflect the legacy naming strategy at build time;
     * an executor resolves the selected properties using its own naming strategy.
     */
    public List<String> getColumnNames() {
        return columnNames;
    }
}
