package io.github.jsbxyyx.jdbcdsl.bean;

import java.util.Locale;

/**
 * Signature of a ResultSet column for mapper cache key generation.
 *
 * <p>This record captures the essential characteristics of a column that
 * affect how it should be mapped to a Java property. Two columns with the
 * same signature can share the same mapping strategy.
 *
 * <p>The signature includes:
 * <ul>
 *   <li>Column name (case-insensitive, normalized to lowercase)</li>
 *   <li>JDBC type code (from {@link java.sql.Types})</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ColumnSignature sig = new ColumnSignature("user_id", Types.BIGINT);
 * }</pre>
 *
 * @param columnName the column name (normalized to lowercase)
 * @param jdbcType the JDBC type code
 * @since 2.1.0
 */
public record ColumnSignature(String columnName, int jdbcType) {

    /**
     * Creates a new ColumnSignature with normalized column name.
     *
     * @param columnName the column name (will be converted to lowercase)
     * @param jdbcType the JDBC type code
     */
    public ColumnSignature {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be null or blank");
        }
        // Normalize to lowercase for case-insensitive comparison
        columnName = columnName.toLowerCase(Locale.ROOT);
    }
}
