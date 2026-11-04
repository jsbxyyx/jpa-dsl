package io.github.jsbxyyx.jdbcdsl;

import java.util.List;

/**
 * Immutable specification for a JDBC INSERT statement.
 *
 * <p>If {@link #getColumnNames()} is empty, all entity columns are used (with the
 * {@code @GeneratedValue(strategy=IDENTITY)} primary-key column excluded automatically).
 * If column names are supplied explicitly, exactly those columns are inserted and the
 * caller is responsible for deciding whether to include the primary key.
 *
 * @param <T> the root entity type
 */
public final class InsertSpec<T> {

    private final Class<T> entityClass;
    private final List<String> columnNames;
    private final List<ColumnSelection> selections;

    private InsertSpec(Class<T> entityClass, List<ColumnSelection> selections) {
        this.entityClass = entityClass;
        this.selections = List.copyOf(selections);
        this.columnNames =
                this.selections.stream().map(ColumnSelection::columnName).toList();
    }

    /**
     * Creates a spec that inserts all entity columns (skipping IDENTITY-generated pk).
     */
    public static <T> InsertSpec<T> of(Class<T> entityClass) {
        return new InsertSpec<>(entityClass, List.of());
    }

    /**
     * Creates a spec that inserts exactly the given column names.
     */
    public static <T> InsertSpec<T> of(Class<T> entityClass, List<String> columnNames) {
        return new InsertSpec<>(
                entityClass,
                columnNames.stream()
                        .map(column -> new ColumnSelection(column, null))
                        .toList());
    }

    static <T> InsertSpec<T> fromSelections(Class<T> entityClass, List<ColumnSelection> selections) {
        return new InsertSpec<>(entityClass, selections);
    }

    InsertSpec<T> resolve(EntityMeta meta) {
        if (selections.stream().noneMatch(selection -> selection.propertyName() != null)) {
            return this;
        }
        return of(
                entityClass,
                selections.stream().map(selection -> selection.resolve(meta)).toList());
    }

    /** The entity class. */
    public Class<T> getEntityClass() {
        return entityClass;
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
