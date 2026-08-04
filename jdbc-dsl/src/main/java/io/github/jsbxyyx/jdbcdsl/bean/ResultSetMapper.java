package io.github.jsbxyyx.jdbcdsl.bean;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Compiled mapper for efficiently mapping ResultSet rows to bean instances.
 *
 * <p>A ResultSetMapper is a pre-compiled mapping strategy that has been
 * optimized for a specific ResultSet structure and target bean type. It
 * eliminates repeated initialization overhead by caching:
 * <ul>
 *   <li>Column to property mappings</li>
 *   <li>Optimized type converters</li>
 *   <li>Property accessors</li>
 * </ul>
 *
 * <p>Mappers are immutable and thread-safe, suitable for caching and reuse
 * across multiple queries with the same structure.
 *
 * <p>Example usage:
 * <pre>{@code
 * ResultSetMapper mapper = mapperCache.getMapper(resultSet, User.class);
 * while (resultSet.next()) {
 *     User user = (User) mapper.mapRow(resultSet);
 * }
 * }</pre>
 *
 * @since 2.1.0
 */
public interface ResultSetMapper {

    /**
     * Maps a single ResultSet row to a bean instance.
     *
     * <p>This method assumes the ResultSet is positioned at a valid row
     * (i.e., after a successful call to {@code next()}).
     *
     * @param rs the ResultSet positioned at the row to map
     * @return a new bean instance populated with row data
     * @throws SQLException if ResultSet access fails
     */
    Object mapRow(ResultSet rs) throws SQLException;

    /**
     * Returns the target bean type for this mapper.
     *
     * @return the target bean class
     */
    Class<?> getTargetType();

    /**
     * Returns the number of columns this mapper handles.
     *
     * @return the column count
     */
    int getColumnCount();
}
