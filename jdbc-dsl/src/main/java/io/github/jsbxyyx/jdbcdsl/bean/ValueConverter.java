package io.github.jsbxyyx.jdbcdsl.bean;

/**
 * Converts a value from one type to another.
 *
 * <p>Implementations are cached per property to avoid repeated type checking
 * and conversion service lookups during result set mapping.
 *
 * <p>This interface is designed for high-performance scenarios where the same
 * conversion is applied millions of times (e.g., mapping 100k rows × 20 columns).
 */
@FunctionalInterface
public interface ValueConverter {

    /**
     * Converts the given value to the target type.
     *
     * @param value the value to convert (may be null)
     * @return the converted value
     * @throws RuntimeException if conversion fails
     */
    Object convert(Object value);

    /**
     * Returns a no-op converter that returns the value as-is.
     *
     * <p>Used when the source type already matches the target type.
     *
     * @return an identity converter
     */
    static ValueConverter identity() {
        return value -> value;
    }

    /**
     * Returns a converter that handles null values.
     *
     * <p>Returns null immediately if the input is null, otherwise delegates
     * to the provided converter.
     *
     * @param delegate the converter to use for non-null values
     * @return a null-safe converter
     */
    static ValueConverter nullSafe(ValueConverter delegate) {
        return value -> value == null ? null : delegate.convert(value);
    }
}
