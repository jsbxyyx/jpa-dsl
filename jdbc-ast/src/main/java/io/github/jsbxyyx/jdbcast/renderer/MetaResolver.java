package io.github.jsbxyyx.jdbcast.renderer;

import io.github.jsbxyyx.jdbcast.SFunction;

/**
 * Strategy for resolving entity metadata at render time.
 *
 * <p>The renderer calls this to convert:
 * <ul>
 *   <li>an entity {@code Class<?>} → the database table name</li>
 *   <li>a getter {@code SFunction<?,?>} → the database column name</li>
 * </ul>
 *
 * <p>The default implementation ({@link io.github.jsbxyyx.jdbcast.meta.JpaMetaResolver})
 * reads {@code @Table} and {@code @Column} JPA annotations.
 */
public interface MetaResolver {

    /**
     * Returns the database table name for the given entity class.
     * Must never return {@code null}.
     */
    String tableName(Class<?> entityClass);

    /**
     * Returns the database column name for the property identified by the getter.
     * Must never return {@code null}.
     */
    String columnName(SFunction<?, ?> getter);

    /**
     * Returns the database column name for a property identified by its name string.
     * Used when no method reference is available (e.g., entity-based bulk insert).
     *
     * <p>Default implementation delegates to the class name + property name cache of
     * the concrete resolver. Override for custom resolution.
     */
    default String columnName(Class<?> entityClass, String propertyName) {
        return propertyName;   // safe fallback; concrete resolvers override this
    }
}
