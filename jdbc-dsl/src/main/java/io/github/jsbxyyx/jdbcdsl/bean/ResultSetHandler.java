package io.github.jsbxyyx.jdbcdsl.bean;

import io.github.jsbxyyx.jdbcdsl.JdbcDslConfig;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
    private final MappingMode mappingMode;
    private final BooleanSupplier allowNullToPrimitiveDefault;
    private final boolean strictFullyMappedReferenceProperties;

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
        final Class<?> propertyType;
        final int jdbcType;
        final boolean numericReadSafe;
        final boolean stringReadSafe;

        ColumnMapping(
                PropertyAccessor accessor,
                ValueConverter converter,
                Class<?> propertyType,
                int jdbcType,
                boolean numericReadSafe,
                boolean stringReadSafe) {
            this.accessor = accessor;
            this.converter = converter;
            this.propertyType = propertyType;
            this.jdbcType = jdbcType;
            this.numericReadSafe = numericReadSafe;
            this.stringReadSafe = stringReadSafe;
        }

        /**
         * Sets the property value with optimized single conversion.
         *
         * <p>Converts once using the pre-selected converter, then uses
         * accessor.write() to set the value without re-conversion.
         */
        void set(Object target, Object value) {
            if (numericReadSafe || stringReadSafe) {
                // The typed JDBC getter already returns the exact property type.
                // Skip the identity-converter dispatch on this hot path.
                accessor.write(target, value);
                return;
            }
            accessor.write(target, converter.convert(value));
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
        this(rs, meta, converterRegistry, UnknownColumnBehavior.NONE);
    }

    public ResultSetHandler(
            ResultSet rs,
            BeanMappingMeta meta,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns)
            throws SQLException {
        this(rs, meta, converterRegistry, unknownColumns, MappingMode.STRICT);
    }

    public ResultSetHandler(
            ResultSet rs,
            BeanMappingMeta meta,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode)
            throws SQLException {
        this(rs, meta, converterRegistry, unknownColumns, mappingMode, JdbcDslConfig::isAllowNullToPrimitiveDefault);
    }

    public ResultSetHandler(
            ResultSet rs,
            BeanMappingMeta meta,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode,
            BooleanSupplier allowNullToPrimitiveDefault)
            throws SQLException {
        this.meta = meta;
        this.converterRegistry = converterRegistry;
        this.mappingMode = java.util.Objects.requireNonNull(mappingMode, "mappingMode");
        this.allowNullToPrimitiveDefault =
                java.util.Objects.requireNonNull(allowNullToPrimitiveDefault, "allowNullToPrimitiveDefault");
        ResultSetMetaData rsMetaData = rs.getMetaData();
        this.columnCount = rsMetaData.getColumnCount();
        this.columnMappings = new ColumnMapping[columnCount];

        Set<String> mappedProperties = new HashSet<>();
        boolean builtInConverterRegistry = converterRegistry instanceof DefaultConverterRegistry;
        boolean fullyMapped = true;
        boolean hasPrimitiveProperty = false;
        // Preprocess: build optimized column mappings
        for (int i = 1; i <= columnCount; i++) {
            String label = rsMetaData.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = rsMetaData.getColumnName(i);
            }
            if (label == null || label.isBlank()) {
                fullyMapped = false;
                continue;
            }

            // Convert to lowercase once during initialization
            String propertyName = label.toLowerCase(Locale.ROOT);

            // Get PropertyAccessor
            PropertyAccessor accessor = meta.getPropertyAccessor(propertyName);
            if (accessor == null) {
                fullyMapped = false;
                unknownColumns.handle(label, meta.getType());
                continue;
            }
            if (!mappedProperties.add(accessor.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Multiple columns match property " + accessor.getName() + " of "
                        + meta.getType().getName());
            }

            // Select optimized converter based on target property type.
            // JDBC drivers may return different Java values for the same SQL type
            // (for example SMALLINT may be returned as Integer), therefore the
            // entity property type is the source of truth for conversion.
            Class<?> propertyType = accessor.getType();
            hasPrimitiveProperty |= propertyType.isPrimitive();
            int jdbcType = rsMetaData.getColumnType(i);
            // Typed ResultSet getters can change the runtime value supplied to a custom
            // ConverterRegistry. Keep the original getObject-based contract for custom
            // registries; only the built-in registry has converters designed for these reads.
            boolean numericReadSafe = builtInConverterRegistry
                    && canReadSignedIntegerDirectly(jdbcType, propertyType, rsMetaData.isSigned(i));
            boolean stringReadSafe =
                    builtInConverterRegistry && propertyType == String.class && isCharacterType(jdbcType);

            // Safe typed getters already produce the exact target Java type. Avoid running
            // the numeric converter's repeated runtime type/range checks for every row.
            ValueConverter converter = numericReadSafe || stringReadSafe
                    ? ValueConverters.identity()
                    : converterRegistry.getConverter(jdbcType, propertyType);

            columnMappings[i - 1] =
                    new ColumnMapping(accessor, converter, propertyType, jdbcType, numericReadSafe, stringReadSafe);
        }
        this.strictFullyMappedReferenceProperties =
                mappingMode == MappingMode.STRICT && fullyMapped && !hasPrimitiveProperty;
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
     * @throws IllegalStateException if conversion or property assignment fails
     */
    public Object mapRow(ResultSet rs) throws SQLException {
        Object instance = meta.newInstance();

        if (strictFullyMappedReferenceProperties) {
            for (int i = 0; i < columnCount; i++) {
                ColumnMapping mapping = columnMappings[i];
                Object value = readValue(
                        rs,
                        i + 1,
                        mapping.propertyType,
                        mapping.jdbcType,
                        mappingMode,
                        mapping.numericReadSafe,
                        mapping.stringReadSafe);
                try {
                    mapping.set(instance, value);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "Failed to map ResultSet column " + (i + 1)
                                    + " to property " + mapping.accessor.getName()
                                    + " of " + meta.getType().getName()
                                    + ", value type="
                                    + (value == null ? "null" : value.getClass().getName()),
                            e);
                }
            }
            return instance;
        }

        // Optimized path: direct calls with pre-selected converters
        for (int i = 0; i < columnCount; i++) {
            ColumnMapping mapping = columnMappings[i];
            if (mapping == null) {
                continue; // Column not mapped to any property
            }

            Object value = readValue(
                    rs,
                    i + 1,
                    mapping.propertyType,
                    mapping.jdbcType,
                    mappingMode,
                    mapping.numericReadSafe,
                    mapping.stringReadSafe);

            // Optimized conversion and property setting:
            // - Type conversion via pre-selected optimal converter
            // - Direct property setting bypassing double conversion
            try {
                if (value == null && mappingMode == MappingMode.MYBATIS) {
                    continue;
                }
                if (value == null && mapping.propertyType.isPrimitive()) {
                    if (!allowNullToPrimitiveDefault.getAsBoolean()) {
                        throw new IllegalStateException("SQL NULL cannot be mapped to primitive property "
                                + mapping.accessor.getName() + " of "
                                + meta.getType().getName()
                                + "; enable jdbcdsl.allow-null-to-primitive-default to use the Java default value");
                    }
                    mapping.accessor.write(instance, PrimitiveDefaults.valueOf(mapping.propertyType));
                    continue;
                }
                mapping.set(instance, value);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Failed to map ResultSet column " + (i + 1)
                                + " to property " + mapping.accessor.getName()
                                + " of " + meta.getType().getName()
                                + ", value type="
                                + (value == null ? "null" : value.getClass().getName()),
                        e);
            }
        }

        return instance;
    }

    static Object readValue(ResultSet rs, int columnIndex, Class<?> targetType, int jdbcType) throws SQLException {
        return readValue(rs, columnIndex, targetType, jdbcType, MappingMode.STRICT);
    }

    static Object readValue(ResultSet rs, int columnIndex, Class<?> targetType, int jdbcType, MappingMode mode)
            throws SQLException {
        return readValue(rs, columnIndex, targetType, jdbcType, mode, false, false);
    }

    static Object readValue(
            ResultSet rs, int columnIndex, Class<?> targetType, int jdbcType, MappingMode mode, boolean numericReadSafe)
            throws SQLException {
        return readValue(rs, columnIndex, targetType, jdbcType, mode, numericReadSafe, false);
    }

    static Object readValue(
            ResultSet rs,
            int columnIndex,
            Class<?> targetType,
            int jdbcType,
            MappingMode mode,
            boolean numericReadSafe,
            boolean stringReadSafe)
            throws SQLException {
        if (mode == MappingMode.MYBATIS) {
            Object value = null;
            if (targetType == byte.class || targetType == Byte.class) {
                value = rs.getByte(columnIndex);
            } else if (targetType == short.class || targetType == Short.class) {
                value = rs.getShort(columnIndex);
            } else if (targetType == int.class || targetType == Integer.class) {
                value = rs.getInt(columnIndex);
            } else if (targetType == long.class || targetType == Long.class) {
                value = rs.getLong(columnIndex);
            }
            if (value != null) {
                return rs.wasNull() ? null : value;
            }
        }
        if (stringReadSafe) {
            return rs.getString(columnIndex);
        }
        if (numericReadSafe) {
            return readSignedInteger(rs, columnIndex, targetType);
        }
        if (targetType == LocalDateTime.class && jdbcType == Types.TIMESTAMP) {
            return rs.getObject(columnIndex, LocalDateTime.class);
        }
        return JdbcUtils.getResultSetValue(rs, columnIndex);
    }

    private static Object readSignedInteger(ResultSet rs, int columnIndex, Class<?> targetType) throws SQLException {
        if (targetType == byte.class || targetType == Byte.class) {
            byte value = rs.getByte(columnIndex);
            return rs.wasNull() ? null : value;
        }
        if (targetType == short.class || targetType == Short.class) {
            short value = rs.getShort(columnIndex);
            return rs.wasNull() ? null : value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : value;
        }
        long value = rs.getLong(columnIndex);
        return rs.wasNull() ? null : value;
    }

    private static boolean isCharacterType(int jdbcType) {
        return jdbcType == Types.CHAR
                || jdbcType == Types.VARCHAR
                || jdbcType == Types.LONGVARCHAR
                || jdbcType == Types.NCHAR
                || jdbcType == Types.NVARCHAR
                || jdbcType == Types.LONGNVARCHAR;
    }

    private static boolean canReadSignedIntegerDirectly(int jdbcType, Class<?> targetType, boolean signed) {
        if (!signed) {
            return false;
        }
        int sourceBits =
                switch (jdbcType) {
                    case Types.TINYINT -> 8;
                    case Types.SMALLINT -> 16;
                    case Types.INTEGER -> 32;
                    case Types.BIGINT -> 64;
                    default -> 0;
                };
        int targetBits;
        if (targetType == Byte.class || targetType == byte.class) {
            targetBits = 8;
        } else if (targetType == Short.class || targetType == short.class) {
            targetBits = 16;
        } else if (targetType == Integer.class || targetType == int.class) {
            targetBits = 32;
        } else if (targetType == Long.class || targetType == long.class) {
            targetBits = 64;
        } else {
            targetBits = 0;
        }
        return sourceBits > 0 && targetBits >= sourceBits;
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
