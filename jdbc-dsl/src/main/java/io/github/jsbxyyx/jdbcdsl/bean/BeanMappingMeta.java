package io.github.jsbxyyx.jdbcdsl.bean;

/**
 * Metadata for mapping JDBC ResultSet rows to Java objects.
 *
 * <p>This interface abstracts the object creation and property setting logic,
 * allowing different implementations for JavaBeans, Records, and other types.
 *
 * <p>Implementations are cached to avoid repeated reflection overhead.
 */
public interface BeanMappingMeta {

    /**
     * Creates a new instance of the target type.
     *
     * @return a new object instance
     * @throws RuntimeException if instantiation fails
     */
    Object newInstance();

    /**
     * Gets a property value from the target object.
     *
     * @param target the target object
     * @param propertyName the property name (case-insensitive)
     * @return the property value
     * @throws RuntimeException if property reading fails
     */
    Object getProperty(Object target, String propertyName);

    /**
     * Sets a property value on the target object.
     *
     * @param target the target object
     * @param propertyName the property name (case-insensitive)
     * @param value the value to set
     * @throws RuntimeException if property setting fails
     */
    void setProperty(Object target, String propertyName, Object value);

    /**
     * Returns the target class type.
     *
     * @return the class type
     */
    Class<?> getType();

    /**
     * Checks if this meta supports the given property name.
     *
     * @param propertyName the property name to check
     * @return {@code true} if the property is supported
     */
    boolean hasProperty(String propertyName);

    /**
     * Gets the property type for a given property name.
     *
     * @param propertyName the property name (case-insensitive)
     * @return the property type, or null if not found
     */
    default Class<?> getPropertyType(String propertyName) {
        return null;
    }

    /**
     * Gets the PropertyAccessor for a given property name.
     *
     * @param propertyName the property name (case-insensitive)
     * @return the PropertyAccessor, or null if not found
     */
    default PropertyAccessor getPropertyAccessor(String propertyName) {
        return null;
    }
}
