package io.github.jsbxyyx.jdbcdsl.bean;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thread-safe cache adapter for compiled ResultSet mappers.
 *
 * <p>This cache eliminates repeated mapper initialization overhead by storing
 * pre-compiled mappers keyed by target type and column structure. Multiple
 * queries with the same structure can share the same mapper instance.
 *
 * <p>Cache is managed by {@link JdbcDslCacheManager} for unified lifecycle
 * and configuration across all jdbc-dsl caches.
 *
 * <p>Example usage:
 * <pre>{@code
 * JdbcDslCacheManager cacheManager = new JdbcDslCacheManager();
 * MapperCache cache = new MapperCache(cacheManager, converterRegistry);
 * ResultSetMapper mapper = cache.getMapper(resultSet, meta);
 * while (resultSet.next()) {
 *     Object bean = mapper.mapRow(resultSet);
 * }
 * }</pre>
 *
 * @since 2.1.0
 */
public final class MapperCache {

    private final JdbcDslCacheManager cacheManager;
    private final ConverterRegistry converterRegistry;

    /**
     * Creates a new MapperCache that uses the cache instance from JdbcDslCacheManager.
     *
     * <p>Cache configuration (size, expiration, statistics) is managed by
     * {@link JdbcDslCacheManager} for consistency across all jdbc-dsl caches.
     *
     * @param cacheManager the central cache manager
     * @param converterRegistry the converter registry to use for mapper creation
     */
    public MapperCache(JdbcDslCacheManager cacheManager, ConverterRegistry converterRegistry) {
        this.cacheManager = cacheManager;
        this.converterRegistry = converterRegistry;
    }

    /**
     * Gets or creates a mapper for the given ResultSet and target type.
     *
     * <p>If a mapper for this combination already exists in the cache, it is
     * returned immediately. Otherwise, a new mapper is created, cached, and returned.
     *
     * <p>This method is thread-safe and optimized for high-concurrency scenarios.
     *
     * @param rs the ResultSet (used only for metadata extraction)
     * @param meta the bean mapping metadata
     * @return a compiled mapper for this ResultSet structure and target type
     * @throws SQLException if ResultSet metadata extraction fails
     */
    public ResultSetMapper getMapper(ResultSet rs, BeanMappingMeta meta) throws SQLException {
        MapperKey key = createKey(rs, meta.getType());
        return cacheManager.getMapperCache().get(key, k -> {
            try {
                return new ResultSetHandler(rs, meta, converterRegistry);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create mapper for " + meta.getType(), e);
            }
        });
    }

    /**
     * Creates a cache key from ResultSet metadata and target type.
     *
     * <p>The key preserves column order as it appears in the ResultSet, since
     * different column orders require different mappers (column positions matter).
     *
     * @param rs the ResultSet
     * @param targetType the target bean type
     * @return a cache key
     * @throws SQLException if metadata extraction fails
     */
    private MapperKey createKey(ResultSet rs, Class<?> targetType) throws SQLException {
        ResultSetMetaData rsMetaData = rs.getMetaData();
        int columnCount = rsMetaData.getColumnCount();
        List<ColumnSignature> columns = new ArrayList<>(columnCount);

        for (int i = 1; i <= columnCount; i++) {
            String label = rsMetaData.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = rsMetaData.getColumnName(i);
            }
            if (label == null || label.isBlank()) {
                continue;
            }

            String columnName = label.toLowerCase(Locale.ROOT);
            int jdbcType = rsMetaData.getColumnType(i);
            columns.add(new ColumnSignature(columnName, jdbcType));
        }

        return new MapperKey(targetType, columns);
    }

    /**
     * Clears all cached mappers.
     *
     * <p>This method is useful for testing or when the mapping configuration
     * has changed and cached mappers need to be invalidated.
     */
    public void clear() {
        cacheManager.getMapperCache().invalidateAll();
    }

    /**
     * Returns the approximate number of cached mappers.
     *
     * <p>Note: This is an estimate and may not reflect the exact size due to
     * concurrent modifications and asynchronous cleanup.
     *
     * @return the approximate cache size
     */
    public long size() {
        return cacheManager.getMapperCache().estimatedSize();
    }

    /**
     * Returns cache statistics if recording is enabled.
     *
     * <p>Statistics include:
     * <ul>
     *   <li>Hit rate: Percentage of cache hits</li>
     *   <li>Miss rate: Percentage of cache misses</li>
     *   <li>Load count: Number of times new mappers were created</li>
     *   <li>Eviction count: Number of mappers evicted</li>
     * </ul>
     *
     * @return cache statistics, or empty stats if recording is disabled
     * @throws IllegalStateException if statistics recording was not enabled
     */
    public CacheStats getStats() {
        return cacheManager.getMapperCache().stats();
    }

    /**
     * Performs any pending maintenance operations (e.g., eviction, expiration).
     *
     * <p>Caffeine performs maintenance asynchronously, but this method can be
     * called to trigger immediate cleanup. Useful for testing or memory-sensitive
     * scenarios.
     */
    public void cleanUp() {
        cacheManager.getMapperCache().cleanUp();
    }
}
