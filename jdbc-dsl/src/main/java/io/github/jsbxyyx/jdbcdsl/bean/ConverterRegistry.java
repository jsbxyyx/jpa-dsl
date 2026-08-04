package io.github.jsbxyyx.jdbcdsl.bean;

/**
 * Registry for managing type converters between JDBC types and Java types.
 *
 * <p>This interface provides a pluggable mechanism for selecting the optimal
 * converter based on source and target types. Implementations can use different
 * strategies such as:
 * <ul>
 *   <li>Identity conversion for exact type matches</li>
 *   <li>Pre-validated conversions from ConversionService</li>
 *   <li>Custom converters for specific type pairs</li>
 *   <li>Dynamic fallback converters</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ConverterRegistry registry = new DefaultConverterRegistry(conversionService);
 * ValueConverter converter = registry.getConverter(Types.TIMESTAMP, LocalDateTime.class);
 * Object converted = converter.convert(resultSetValue);
 * }</pre>
 *
 * @since 2.1.0
 */
public interface ConverterRegistry {

    /**
     * Finds the optimal converter for a JDBC type to Java type mapping.
     *
     * <p>The registry should return the most efficient converter available:
     * <ol>
     *   <li>Identity converter if types match exactly (zero overhead)</li>
     *   <li>Pre-validated converter if conversion is known to be supported</li>
     *   <li>Dynamic converter as fallback</li>
     * </ol>
     *
     * @param jdbcType the JDBC type code from {@link java.sql.Types}
     * @param targetType the target Java type
     * @return a converter that can convert from JDBC type to target type
     * @throws IllegalArgumentException if no suitable converter can be found
     */
    ValueConverter getConverter(int jdbcType, Class<?> targetType);

    /**
     * Finds the optimal converter for a source type to target type mapping.
     *
     * <p>This is a more general version that works with any source type,
     * not just JDBC types.
     *
     * @param sourceType the source Java type (may be null if unknown)
     * @param targetType the target Java type
     * @return a converter that can convert from source type to target type
     * @throws IllegalArgumentException if no suitable converter can be found
     */
    ValueConverter getConverter(Class<?> sourceType, Class<?> targetType);
}
