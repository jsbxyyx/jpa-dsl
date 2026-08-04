package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.core.convert.ConversionService;

import java.sql.Types;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link ConverterRegistry} using Spring's ConversionService.
 *
 * <p>This registry provides optimized converter selection based on JDBC types and
 * Java types. It caches converters for frequently used type pairs to minimize
 * repeated lookups and validation.
 *
 * <p>Converter selection strategy:
 * <ol>
 *   <li>Identity converter for exact type matches (zero overhead)</li>
 *   <li>Pre-validated converter from ConversionService for known conversions</li>
 *   <li>Dynamic converter as fallback for unknown or complex cases</li>
 * </ol>
 *
 * <p>Thread-safe and suitable for concurrent use.
 *
 * @since 2.1.0
 */
public final class DefaultConverterRegistry implements ConverterRegistry {

    private final ConversionService conversionService;
    private final Map<ConverterKey, ValueConverter> converterCache;

    /**
     * Creates a new DefaultConverterRegistry.
     *
     * @param conversionService the conversion service to use for type conversions
     */
    public DefaultConverterRegistry(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.converterCache = new ConcurrentHashMap<>();
    }

    @Override
    public ValueConverter getConverter(int jdbcType, Class<?> targetType) {
        Class<?> sourceType = getJdbcJavaType(jdbcType);
        return getConverter(sourceType, targetType);
    }

    @Override
    public ValueConverter getConverter(Class<?> sourceType, Class<?> targetType) {
        // Use cache to avoid repeated converter creation
        ConverterKey key = new ConverterKey(sourceType, targetType);
        return converterCache.computeIfAbsent(key, k -> selectOptimalConverter(sourceType, targetType));
    }

    /**
     * Selects the most efficient ValueConverter for a source type to target type mapping.
     *
     * @param sourceType the source Java type (may be null if unknown)
     * @param targetType the target Java type
     * @return the optimal converter
     */
    private ValueConverter selectOptimalConverter(Class<?> sourceType, Class<?> targetType) {
        // Check for exact type matches (no conversion needed)
        if (sourceType != null && sourceType.equals(targetType)) {
            return ValueConverters.identity();
        }

        // Check if conversion is supported
        if (sourceType != null && conversionService.canConvert(sourceType, targetType)) {
            return ValueConverters.fromConversionService(conversionService, sourceType, targetType);
        }

        // Fallback to dynamic converter for unknown or complex cases
        return ValueConverters.dynamic(conversionService, targetType);
    }

    /**
     * Maps JDBC type codes to their corresponding Java types.
     *
     * @param jdbcType the JDBC type code
     * @return the Java type, or null if unknown
     */
    private Class<?> getJdbcJavaType(int jdbcType) {
        return switch (jdbcType) {
            case Types.TINYINT -> Byte.class;
            case Types.SMALLINT -> Short.class;
            case Types.INTEGER -> Integer.class;
            case Types.BIGINT -> Long.class;
            case Types.REAL, Types.FLOAT -> Float.class;
            case Types.DOUBLE -> Double.class;
            case Types.DECIMAL, Types.NUMERIC -> java.math.BigDecimal.class;
            case Types.BOOLEAN, Types.BIT -> Boolean.class;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> String
                    .class;
            case Types.DATE -> java.sql.Date.class;
            case Types.TIME -> java.sql.Time.class;
            case Types.TIMESTAMP -> java.sql.Timestamp.class;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> byte[].class;
            case Types.BLOB -> java.sql.Blob.class;
            case Types.CLOB, Types.NCLOB -> java.sql.Clob.class;
            default -> null; // Unknown type, will use dynamic converter
        };
    }

    /**
     * Cache key for converter lookup.
     */
    private record ConverterKey(Class<?> sourceType, Class<?> targetType) {}
}
