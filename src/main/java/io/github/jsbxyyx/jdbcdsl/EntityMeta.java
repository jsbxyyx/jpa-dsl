package io.github.jsbxyyx.jdbcdsl;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable metadata extracted from a JPA-annotated entity class.
 *
 * <p>Column names default to the Java property name when {@code @Column(name)} is not specified.
 * No automatic snake_case conversion is performed.
 */
public final class EntityMeta {

    private final String tableName;
    private final Map<String, String> propertyToColumn;
    private final String idPropertyName;
    private final String idColumnName;

    public EntityMeta(String tableName,
                      Map<String, String> propertyToColumn,
                      String idPropertyName,
                      String idColumnName) {
        this.tableName = tableName;
        this.propertyToColumn = Collections.unmodifiableMap(propertyToColumn);
        this.idPropertyName = idPropertyName;
        this.idColumnName = idColumnName;
    }

    /** The database table name (from {@code @Table(name)} or entity class simple name). */
    public String getTableName() {
        return tableName;
    }

    /** Returns the column name for the given property name, or {@code null} if not found. */
    public String getColumnName(String propertyName) {
        return propertyToColumn.get(propertyName);
    }

    /** All property-to-column mappings. */
    public Map<String, String> getPropertyToColumn() {
        return propertyToColumn;
    }

    /** The ID property name (from {@code @Id}). */
    public String getIdPropertyName() {
        return idPropertyName;
    }

    /** The ID column name. */
    public String getIdColumnName() {
        return idColumnName;
    }
}
