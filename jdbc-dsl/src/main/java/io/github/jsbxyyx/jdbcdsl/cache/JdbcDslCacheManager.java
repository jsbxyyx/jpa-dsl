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
    private final Cache<Class<?>, SerializedLambda> serializedLambdaCache;

    public JdbcDslCacheManager() {
        this(10_000, 10_000, 10_000);
    }

    public JdbcDslCacheManager(long propertyRefMaxSize, long rowMapperMaxSize) {
        this(propertyRefMaxSize, rowMapperMaxSize, 10_000);
    }

    public JdbcDslCacheManager(long propertyRefMaxSize, long rowMapperMaxSize, long serializedLambdaMaxSize) {
        this.propertyRefCache =
                Caffeine.newBuilder().maximumSize(propertyRefMaxSize).build();
        this.rowMapperCache =
                Caffeine.newBuilder().maximumSize(rowMapperMaxSize).build();
        this.serializedLambdaCache =
                Caffeine.newBuilder().maximumSize(serializedLambdaMaxSize).build();
    }

    public Cache<String, PropertyRef> getPropertyRefCache() {
        return propertyRefCache;
    }

    public Cache<Class<?>, RowMapper<?>> getRowMapperCache() {
        return rowMapperCache;
    }

    public Cache<Class<?>, SerializedLambda> getSerializedLambdaCache() {
        return serializedLambdaCache;
    }
}
