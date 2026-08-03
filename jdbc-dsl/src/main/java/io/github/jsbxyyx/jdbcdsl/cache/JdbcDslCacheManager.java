package io.github.jsbxyyx.jdbcdsl.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.jsbxyyx.jdbcdsl.PropertyRef;
import org.springframework.jdbc.core.RowMapper;

/**
 * Central cache manager for jdbc-dsl runtime caches.
 */
public class JdbcDslCacheManager {

    private final Cache<String, PropertyRef> propertyRefCache;
    private final Cache<Class<?>, RowMapper<?>> rowMapperCache;

    public JdbcDslCacheManager() {
        this(10_000, 10_000);
    }

    public JdbcDslCacheManager(long propertyRefMaxSize, long rowMapperMaxSize) {
        this.propertyRefCache = Caffeine.newBuilder()
                .maximumSize(propertyRefMaxSize)
                .build();
        this.rowMapperCache = Caffeine.newBuilder()
                .maximumSize(rowMapperMaxSize)
                .build();
    }

    public Cache<String, PropertyRef> getPropertyRefCache() {
        return propertyRefCache;
    }

    public Cache<Class<?>, RowMapper<?>> getRowMapperCache() {
        return rowMapperCache;
    }
}
