package io.github.jsbxyyx.jdbcdsl.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.jsbxyyx.jdbcdsl.PropertyRef;
import org.springframework.jdbc.core.RowMapper;

import java.lang.invoke.SerializedLambda;

/**
 * Central cache manager for jdbc-dsl runtime caches.
 */
public class JdbcDslCacheManager {

    private final Cache<String, PropertyRef> propertyRefCache;
    private final Cache<Class<?>, RowMapper<?>> rowMapperCache;
    private final Cache<String, SerializedLambda> serializedLambdaCache;

    public static final int DEFAULT_PROPERTY_REF_CACHE_MAX_SIZE = 50_000;
    public static final int DEFAULT_ROW_MAPPER_CACHE_MAX_SIZE = 10_000;
    public static final int DEFAULT_SERIALIZED_LAMBDA_CACHE_MAX_SIZE = 10_000;

    public JdbcDslCacheManager() {
        this(
                DEFAULT_PROPERTY_REF_CACHE_MAX_SIZE,
                DEFAULT_ROW_MAPPER_CACHE_MAX_SIZE,
                DEFAULT_SERIALIZED_LAMBDA_CACHE_MAX_SIZE);
    }

    public JdbcDslCacheManager(long propertyRefMaxSize, long rowMapperMaxSize, long serializedLambdaMaxSize) {
        this.propertyRefCache = Caffeine.newBuilder()
                .maximumSize(Math.max(500, propertyRefMaxSize))
                .build();
        this.rowMapperCache = Caffeine.newBuilder()
                .maximumSize(Math.max(100, rowMapperMaxSize))
                .build();
        this.serializedLambdaCache = Caffeine.newBuilder()
                .maximumSize(Math.max(100, serializedLambdaMaxSize))
                .build();
    }

    public Cache<String, PropertyRef> getPropertyRefCache() {
        return propertyRefCache;
    }

    public Cache<Class<?>, RowMapper<?>> getRowMapperCache() {
        return rowMapperCache;
    }

    public Cache<String, SerializedLambda> getSerializedLambdaCache() {
        return serializedLambdaCache;
    }

    public void clearAll() {
        propertyRefCache.invalidateAll();
        serializedLambdaCache.invalidateAll();
        rowMapperCache.invalidateAll();
    }
}
