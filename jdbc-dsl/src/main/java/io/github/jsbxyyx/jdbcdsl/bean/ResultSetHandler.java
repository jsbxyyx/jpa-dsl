package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Optimized ResultSet to Bean mapper that preprocesses column metadata.
 *
 * <p>This mapper eliminates repeated Map lookups and type checks by caching
 * optimized ColumnMapping for each column during initialization. Each mapping
 * contains a pre-selected ValueConverter based on JDBC type and property type.
 *
 * <p>Performance characteristics:
 * <ul>
 *   <li>Initialization: O(n) where n = column count</li>
 *   <li>Per-row mapping: O(n) with direct calls, zero Map lookups, minimal type checks</li>
 *   <li>Memory: O(n) for ColumnMapping array</li>
 * </ul>
 *
 * <p>Recommended usage via {@link MapperCache} for optimal performance:
 * <pre>{@code
 * MapperCache mapperCache = new MapperCache(converterRegistry);
 * BeanMappingMeta meta = beanMappingMetaFactory.create(User.class);
 * ResultSetMapper mapper = mapperCache.getMapper(resultSet, meta);
 *
 * while (resultSet.next()) {
 *     User user = (User) mapper.mapRow(resultSet);
 * }
 * }</pre>
 *
 * <p>Direct instantiation is also supported but bypasses caching:
 * <pre>{@code
 * ResultSetHandler handler = new ResultSetHandler(resultSet, meta, converterRegistry);
 * }</pre>
 */
public final class ResultSetHandler implements ResultSetMapper {

    private static final ConverterRegistry DEFAULT_CONVERTER_REGISTRY =
            new DefaultConverterRegistry(DefaultConversionService.getSharedInstance());

    private final BeanMappingMeta meta;
    private final ColumnMapping[] columnMappings;
    private final int columnCount;
    private final ConverterRegistry converterRegistry;

    /**
     * Internal structure that caches optimized converter for each column.
     *
     * <p>The converter is selected based on JDBC type and property type,
     * avoiding runtime type checks in most cases. Uses PropertyAccessor.write()
     * to bypass double conversion.
     */
    private static final class ColumnMapping {
        final PropertyAccessor accessor;
        final ValueConverter converter;

        ColumnMapping(PropertyAccessor accessor, ValueConverter converter) {
            this.accessor = accessor;
            this.converter = converter;
        }

        /**
         * Sets the property value with optimized single conversion.
         *
         * <p>Converts once using the pre-selected converter, then uses
         * accessor.write() to set the value without re-conversion.
         */
        void set(Object target, Object value) {
            Object converted = converter.convert(value);
            accessor.write(target, converted);
        }
    }

    /**
     * Creates a new ResultSetHandler by preprocessing ResultSet metadata.
     *
     * <p>Caches optimized ColumnMapping for each column, selecting the most
     * efficient ValueConverter based on JDBC type and property type combination.
     *
     * @param rs the ResultSet to map (used only for metadata extraction)
     * @param meta the BeanMappingMeta for the target bean type
     * @throws SQLException if metadata extraction fails
     */
    public ResultSetHandler(ResultSet rs, BeanMappingMeta meta) throws SQLException {
        this(rs, meta, DEFAULT_CONVERTER_REGISTRY);
    }

    /**
     * Creates a new ResultSetHandler with a custom converter registry.
     *
     * @param rs the ResultSet to map (used only for metadata extraction)
     * @param meta the BeanMappingMeta for the target bean type
     * @param converterRegistry the converter registry to use
     * @throws SQLException if metadata extraction fails
     */
    public ResultSetHandler(ResultSet rs, BeanMappingMeta meta, ConverterRegistry converterRegistry)
            throws SQLException {
        this.meta = meta;
        this.converterRegistry = converterRegistry;
        ResultSetMetaData rsMetaData = rs.getMetaData();
        this.columnCount = rsMetaData.getColumnCount();
        this.columnMappings = new ColumnMapping[columnCount];

        // Preprocess: build optimized column mappings
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

            // Get PropertyAccessor
            PropertyAccessor accessor = meta.getPropertyAccessor(propertyName);
            if (accessor == null) {
                continue;
            }

            // Select optimized converter based on JDBC type and property type
            int jdbcType = rsMetaData.getColumnType(i);
            Class<?> propertyType = accessor.getType();
            ValueConverter converter = converterRegistry.getConverter(jdbcType, propertyType);

            columnMappings[i - 1] = new ColumnMapping(accessor, converter);
        }
    }

    /**
     * Maps a single ResultSet row to a bean instance.
     *
     * <p>This method uses cached ColumnMapping with optimized converters,
     * eliminating all HashMap lookups and minimizing type checks. Most common
     * type combinations use identity or pre-validated converters.
     *
     * <p>This is a pure data mapping operation that reads values from the
     * ResultSet and sets them on the bean. Business logic like audit field
     * auto-fill and logical delete handling occur during write operations
     * (INSERT/UPDATE/DELETE), not during ResultSet mapping.
     *
     * @param rs the ResultSet positioned at the row to map
     * @return a new bean instance populated with row data
     * @throws SQLException if ResultSet access fails
     */
    public Object mapRow(ResultSet rs) throws SQLException {
        Object instance = meta.newInstance();

        // Optimized path: direct calls with pre-selected converters
        for (int i = 0; i < columnCount; i++) {
            ColumnMapping mapping = columnMappings[i];
            if (mapping == null) {
                continue; // Column not mapped to any property
            }

            Object value = JdbcUtils.getResultSetValue(rs, i + 1);

            // Optimized conversion and property setting:
            // - Type conversion via pre-selected optimal converter
            // - Direct property setting bypassing double conversion
            try {
                mapping.set(instance, value);
            } catch (Exception e) {
                // Silently ignore unmappable values (consistent with old behavior)
                // In production, consider using a logger here
            }
        }

        return instance;
    }

    @Override
    public Class<?> getTargetType() {
        return meta.getType();
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    /**
     * Returns the cached ColumnMapping for a specific column index.
     *
     * @param columnIndex the 0-based column index
     * @return the ColumnMapping, or null if column is not mapped
     */
    ColumnMapping getColumnMapping(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= columnCount) {
            return null;
        }
        return columnMappings[columnIndex];
    }

    /**
     * Returns the cached PropertyAccessor for a specific column index.
     *
     * @param columnIndex the 0-based column index
     * @return the PropertyAccessor, or null if column is not mapped
     */
    public PropertyAccessor getPropertyAccessor(int columnIndex) {
        ColumnMapping mapping = getColumnMapping(columnIndex);
        return mapping != null ? mapping.accessor : null;
    }
}
