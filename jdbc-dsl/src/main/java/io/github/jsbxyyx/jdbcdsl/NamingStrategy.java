package io.github.jsbxyyx.jdbcdsl;

/**
 * Strategy that converts Java identifiers to database identifiers when no explicit
 * {@link jakarta.persistence.Column} or {@link jakarta.persistence.Table} annotation is present.
 *
 * <p>Two built-in implementations are provided:
 * <ul>
 *   <li>{@link DefaultNamingStrategy} — returns the Java name unchanged (legacy behaviour).</li>
 *   <li>{@link SnakeCaseNamingStrategy} — converts {@code camelCase} to {@code snake_case}.</li>
 * </ul>
 *
 * <p>A custom implementation can be registered as a Spring bean; it will take priority over the
 * property-based selection. Alternatively set {@code jdbcdsl.naming-strategy=snake_case} in
 * {@code application.properties}.
 */
public interface NamingStrategy {

    /**
     * Converts a Java property name to a database column name.
     *
     * @param propertyName the Java field/property name (e.g. {@code userId})
     * @return the corresponding column name (e.g. {@code user_id})
     */
    String propertyToColumn(String propertyName);

    /**
     * Converts a Java class simple name to a database table name.
     *
     * @param className the Java class simple name (e.g. {@code UserInfo})
     * @return the corresponding table name (e.g. {@code user_info})
     */
    String classToTable(String className);
}
