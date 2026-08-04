package io.github.jsbxyyx.jdbcdsl.bean;

/**
 * Abstraction for accessing (reading/writing) a property on a Java object.
 *
 * <p>Implementations use MethodHandle for performance, avoiding repeated reflection.
 */
public interface PropertyAccessor {

    /**
     * Returns the property name.
     *
     * @return the property name
     */
    String getName();

    /**
     * Returns the property type.
     *
     * @return the property type
     */
    Class<?> getType();

    /**
     * Gets the property value from the target object.
     *
     * @param target the target object
     * @return the property value
     * @throws RuntimeException if access fails
     */
    Object get(Object target);

    /**
     * Sets the property value on the target object.
     *
     * @param target the target object
     * @param value the value to set
     * @throws RuntimeException if access fails
     */
    void set(Object target, Object value);

    /**
     * Writes the property value directly without type conversion.
     *
     * <p>This method assumes the value is already of the correct type
     * and performs no conversion. Use this for optimized mapping scenarios
     * where conversion is handled externally.
     *
     * @param target the target object
     * @param value the value to write (must be compatible with property type)
     * @throws RuntimeException if access fails
     * @since 2.1.0
     */
    void write(Object target, Object value);
}
