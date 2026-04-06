package io.github.jsbxyyx.jdbcdsl;

/**
 * Holds the resolved owner class and property name from an {@link SFunction} method reference.
 *
 * @param ownerClass   the entity class that declares the property
 * @param propertyName the Java property name (e.g., {@code "userName"})
 */
public record PropertyRef(Class<?> ownerClass, String propertyName) {
}
