package io.github.jsbxyyx.jdbcdsl.bean;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe cache for compiled ResultSet mappers.
 *
 * <p>This cache eliminates repeated mapper initialization overhead by storing
 * pre-compiled mappers keyed by target type and column structure. Multiple
 * queries with the same structure can share the same mapper instance.
 *
 * <p>Cache characteristics:
 * <ul>
 *   <li>Thread-safe: Uses synchronized Map for concurrent access</li>
 *   <li>Configurable eviction: Optional LRU eviction for dynamic query scenarios</li>
 *   <li>Immutable mappers: Cached mappers are thread-safe and reusable</li>
 * </ul>
 *
 * <p>Example usage (unbounded cache):
 * <pre>{@code
 * MapperCache cache = new MapperCache(converterRegistry);
 * ResultSetMapper mapper = cache.getMapper(resultSet, meta);
 * while (resultSet.next()) {
 *     Object bean = mapper.mapRow(resultSet);
 * }
 * }</pre>
 *
 * <p>Example usage (bounded cache with LRU eviction):
 * <pre>{@code
 * MapperCache cache = new MapperCache(converterRegistry, 1000);
 * // Automatically evicts least recently used mappers when size > 1000
 * }</pre>
 *
 * @since 2.1.0
 */
public final class MapperCache {

    private final Map<MapperKey, ResultSetMapper> cache;
    private final ConverterRegistry converterRegistry;

    /**
     * Creates a new MapperCache with unbounded cache size.
     *
     * <p>Suitable for typical applications with fixed entity types and query patterns.
     *
     * @param converterRegistry the converter registry to use for mapper creation
     */
    public MapperCache(ConverterRegistry converterRegistry) {
        this(converterRegistry, -1);
    }

    /**
     * Creates a new MapperCache with configurable maximum size.
     *
     * <p>When maxSize > 0, uses LRU eviction to prevent unbounded growth.
     * Suitable for dynamic query scenarios (e.g., report systems, multi-tenant).
     *
     * @param converterRegistry the converter registry to use for mapper creation
     * @param maxSize maximum cache size; -1 for unbounded, > 0 for LRU eviction
     */
    public MapperCache(ConverterRegistry converterRegistry, int maxSize) {
        this.converterRegistry = converterRegistry;
        if (maxSize > 0) {
            this.cache = java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<MapperKey, ResultSetMapper>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<MapperKey, ResultSetMapper> eldest) {
                            return size() > maxSize;
                        }
                    });
        } else {
            this.cache = new java.util.concurrent.ConcurrentHashMap<>();
        }
    }

    /**
     * Gets or creates a mapper for the given ResultSet and target type.
     *
     * <p>If a mapper for this combination already exists in the cache, it is
     * returned immediately. Otherwise, a new mapper is created, cached, and returned.
     *
     * @param rs the ResultSet (used only for metadata extraction)
     * @param meta the bean mapping metadata
     * @return a compiled mapper for this ResultSet structure and target type
     * @throws SQLException if ResultSet metadata extraction fails
     */
    public ResultSetMapper getMapper(ResultSet rs, BeanMappingMeta meta) throws SQLException {
        MapperKey key = createKey(rs, meta.getType());
        return cache.computeIfAbsent(key, k -> {
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
        cache.clear();
    }

    /**
     * Returns the number of cached mappers.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }
}
