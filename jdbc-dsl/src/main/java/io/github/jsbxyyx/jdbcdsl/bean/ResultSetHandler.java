package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Optimized ResultSet to Bean mapper that preprocesses column metadata.
 *
 * <p>This mapper eliminates repeated Map lookups and String.toLowerCase() calls
 * by building a column index to property name array during initialization.
 * It preserves all business logic by delegating to BeanMappingMeta.setProperty().
 *
 * <p>Performance characteristics:
 * <ul>
 *   <li>Initialization: O(n) where n = column count</li>
 *   <li>Per-row mapping: O(n) with cached property names (no Map lookups or toLowerCase)</li>
 *   <li>Memory: O(n) for property name array</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * BeanMappingMeta meta = beanMappingMetaFactory.create(User.class);
 * ResultSetMapper mapper = new ResultSetMapper(resultSet, meta);
 *
 * while (resultSet.next()) {
 *     User user = (User) mapper.mapRow(resultSet);
 * }
 * }</pre>
 */
public final class ResultSetHandler {

    private final BeanMappingMeta meta;
    private final String[] columnPropertyNames;
    private final int columnCount;

    /**
     * Creates a new ResultSetMapper by preprocessing ResultSet metadata.
     *
     * @param rs the ResultSet to map (used only for metadata extraction)
     * @param meta the BeanMappingMeta for the target bean type
     * @throws SQLException if metadata extraction fails
     */
    public ResultSetHandler(ResultSet rs, BeanMappingMeta meta) throws SQLException {
        this.meta = meta;
        ResultSetMetaData rsMetaData = rs.getMetaData();
        this.columnCount = rsMetaData.getColumnCount();
        this.columnPropertyNames = new String[columnCount];

        // Preprocess: build column index -> property name mapping
        for (int i = 1; i <= columnCount; i++) {
            String label = rsMetaData.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = rsMetaData.getColumnName(i);
            }
            if (label == null || label.isBlank()) {
                continue;
            }

            // Convert to lowercase once during initialization
            String propertyName = label.toLowerCase(Locale.ROOT);

            // Store property name if it exists in the bean
            if (meta.hasProperty(propertyName)) {
                columnPropertyNames[i - 1] = propertyName;
            }
        }
    }

    /**
     * Maps a single ResultSet row to a bean instance.
     *
     * <p>This method uses the preprocessed column-to-property mapping,
     * avoiding repeated Map lookups and String conversions. It delegates
     * to BeanMappingMeta.setProperty() to preserve all business logic
     * including type conversion, audit field handling, and validation.
     *
     * @param rs the ResultSet positioned at the row to map
     * @return a new bean instance populated with row data
     * @throws SQLException if ResultSet access fails
     */
    public Object mapRow(ResultSet rs) throws SQLException {
        Object instance = meta.newInstance();

        // Fast path: direct array access, no Map lookups or toLowerCase
        for (int i = 0; i < columnCount; i++) {
            String propertyName = columnPropertyNames[i];
            if (propertyName == null) {
                continue; // Column not mapped to any property
            }

            Object value = JdbcUtils.getResultSetValue(rs, i + 1);

            // Delegate to BeanMappingMeta to preserve business logic:
            // - Type conversion via ConversionService
            // - Audit field auto-fill (@CreatedAt, @UpdatedAt)
            // - Logical delete handling
            // - Custom property setters
            try {
                meta.setProperty(instance, propertyName, value);
            } catch (Exception e) {
                // Silently ignore unmappable values (consistent with old behavior)
                // In production, consider using a logger here
            }
        }

        return instance;
    }

    /**
     * Returns the number of columns in the ResultSet.
     *
     * @return the column count
     */
    public int getColumnCount() {
        return columnCount;
    }

    /**
     * Returns the cached property name for a specific column index.
     *
     * @param columnIndex the 0-based column index
     * @return the property name, or null if column is not mapped
     */
    public String getPropertyName(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= columnCount) {
            return null;
        }
        return columnPropertyNames[columnIndex];
    }
}
