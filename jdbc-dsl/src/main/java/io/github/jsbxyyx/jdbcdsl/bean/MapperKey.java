package io.github.jsbxyyx.jdbcdsl.bean;

import java.util.List;
import java.util.Objects;

/**
 * Cache key for ResultSet mappers.
 *
 * <p>This record uniquely identifies a mapper configuration based on:
 * <ul>
 *   <li>Target bean type</li>
 *   <li>Column signatures (name + JDBC type)</li>
 * </ul>
 *
 * <p>Two ResultSets with the same target type and column signatures can
 * share the same compiled mapper, avoiding repeated initialization overhead.
 *
 * <p>Example usage:
 * <pre>{@code
 * List<ColumnSignature> columns = List.of(
 *     new ColumnSignature("id", Types.BIGINT),
 *     new ColumnSignature("name", Types.VARCHAR)
 * );
 * MapperKey key = new MapperKey(User.class, columns);
 * }</pre>
 *
 * @param targetType the target bean class
 * @param columns the list of column signatures
 * @since 2.1.0
 */
public record MapperKey(Class<?> targetType, List<ColumnSignature> columns) {

    /**
     * Creates a new MapperKey with validation.
     *
     * @param targetType the target bean class
     * @param columns the list of column signatures
     */
    public MapperKey {
        Objects.requireNonNull(targetType, "Target type cannot be null");
        Objects.requireNonNull(columns, "Columns cannot be null");
        // Make defensive copy to ensure immutability
        columns = List.copyOf(columns);
    }
}
