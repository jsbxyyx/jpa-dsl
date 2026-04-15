package io.github.jsbxyyx.jdbcdsl;

import java.util.Collections;
import java.util.List;
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
    private final boolean idGeneratedByIdentity;

    // Logical-delete metadata (null when the entity has no @LogicalDelete field)
    private final String logicalDeletePropertyName;
    private final String logicalDeleteColumnName;
    private final String logicalDeletedValue;
    private final String logicalDeleteNormalValue;

    // Auto-fill metadata: property names annotated with @CreatedDate / @LastModifiedDate
    private final List<String> createdDatePropertyNames;
    private final List<String> lastModifiedDatePropertyNames;

    public EntityMeta(String tableName,
                      Map<String, String> propertyToColumn,
                      String idPropertyName,
                      String idColumnName,
                      boolean idGeneratedByIdentity) {
        this(tableName, propertyToColumn, idPropertyName, idColumnName, idGeneratedByIdentity,
                null, null, null, null, List.of(), List.of());
    }

    public EntityMeta(String tableName,
                      Map<String, String> propertyToColumn,
                      String idPropertyName,
                      String idColumnName,
                      boolean idGeneratedByIdentity,
                      String logicalDeletePropertyName,
                      String logicalDeleteColumnName,
                      String logicalDeletedValue,
                      String logicalDeleteNormalValue,
                      List<String> createdDatePropertyNames,
                      List<String> lastModifiedDatePropertyNames) {
        this.tableName = tableName;
        this.propertyToColumn = Collections.unmodifiableMap(propertyToColumn);
        this.idPropertyName = idPropertyName;
        this.idColumnName = idColumnName;
        this.idGeneratedByIdentity = idGeneratedByIdentity;
        this.logicalDeletePropertyName = logicalDeletePropertyName;
        this.logicalDeleteColumnName = logicalDeleteColumnName;
        this.logicalDeletedValue = logicalDeletedValue;
        this.logicalDeleteNormalValue = logicalDeleteNormalValue;
        this.createdDatePropertyNames = List.copyOf(createdDatePropertyNames);
        this.lastModifiedDatePropertyNames = List.copyOf(lastModifiedDatePropertyNames);
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

    /**
     * Returns {@code true} if the {@code @Id} field is annotated with
     * {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}, meaning the database
     * auto-generates the primary key and it should be excluded from INSERT statements.
     */
    public boolean isIdGeneratedByIdentity() {
        return idGeneratedByIdentity;
    }

    // -------------------------------------------------------------------------
    // Logical-delete accessors
    // -------------------------------------------------------------------------

    /**
     * The property name of the field annotated with
     * {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete}, or {@code null} if the
     * entity has no such field.
     */
    public String getLogicalDeletePropertyName() {
        return logicalDeletePropertyName;
    }

    /**
     * The database column name of the logical-delete field, or {@code null} if the entity has
     * no {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete} field.
     */
    public String getLogicalDeleteColumnName() {
        return logicalDeleteColumnName;
    }

    /**
     * The value written to the logical-delete column when a row is soft-deleted
     * (e.g. {@code "1"}), or {@code null} if the entity has no
     * {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete} field.
     */
    public String getLogicalDeletedValue() {
        return logicalDeletedValue;
    }

    /**
     * The value representing a non-deleted row (e.g. {@code "0"}), or {@code null} if the
     * entity has no {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete} field.
     */
    public String getLogicalDeleteNormalValue() {
        return logicalDeleteNormalValue;
    }

    // -------------------------------------------------------------------------
    // Auto-fill accessors
    // -------------------------------------------------------------------------

    /**
     * Property names of fields annotated with
     * {@link org.springframework.data.annotation.CreatedDate}.
     * These are auto-filled with the current timestamp on INSERT.
     */
    public List<String> getCreatedDatePropertyNames() {
        return createdDatePropertyNames;
    }

    /**
     * Property names of fields annotated with
     * {@link org.springframework.data.annotation.LastModifiedDate}.
     * These are auto-filled with the current timestamp on INSERT and UPDATE.
     */
    public List<String> getLastModifiedDatePropertyNames() {
        return lastModifiedDatePropertyNames;
    }
}
