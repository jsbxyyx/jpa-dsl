package io.github.jsbxyyx.jdbcast.stmt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable dialect-neutral UPSERT/MERGE statement model. */
public final class UpsertStatement {
    private final String tableName;
    private final List<String> columns;
    private final Map<String, Object> values;
    private final List<String> conflictColumns;
    private final List<String> updateColumns;
    private final boolean doNothing;

    public UpsertStatement(
            String tableName,
            List<String> columns,
            Map<String, Object> values,
            List<String> conflictColumns,
            List<String> updateColumns,
            boolean doNothing) {
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.columns = List.copyOf(columns);
        this.values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.conflictColumns = List.copyOf(conflictColumns);
        this.updateColumns = List.copyOf(updateColumns);
        this.doNothing = doNothing;
        if (this.columns.isEmpty()) {
            throw new IllegalArgumentException("UPSERT requires at least one column");
        }
        if (!this.values.keySet().containsAll(this.columns)) {
            throw new IllegalArgumentException("UPSERT values must contain every insert column");
        }
        if (!doNothing && this.updateColumns.isEmpty()) {
            throw new IllegalArgumentException("UPSERT update columns must not be empty unless doNothing is true");
        }
    }

    public String tableName() {
        return tableName;
    }

    public List<String> columns() {
        return columns;
    }

    public Map<String, Object> values() {
        return values;
    }

    public List<String> conflictColumns() {
        return conflictColumns;
    }

    public List<String> updateColumns() {
        return updateColumns;
    }

    public boolean doNothing() {
        return doNothing;
    }
}
