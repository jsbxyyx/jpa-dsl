package io.github.jsbxyyx.jdbcdsl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime configuration holder for jdbc-dsl.
 *
 * <p>Populated by {@link JdbcDslAutoConfiguration} on startup from {@link JdbcDslProperties}.
 * Builders ({@link UpdateBuilder}, {@link DeleteBuilder}) and {@link EntityMetaReader} read from
 * this class so that they do not need a Spring dependency injection context.
 */
public final class JdbcDslConfig {

    private static final AtomicBoolean allowEmptyWhere = new AtomicBoolean(false);
    private static final AtomicBoolean logicalDeleteAutoFilter = new AtomicBoolean(true);
    private static final AtomicReference<NamingStrategy> namingStrategy =
            new AtomicReference<>(DefaultNamingStrategy.INSTANCE);

    private JdbcDslConfig() {
    }

    /**
     * Returns {@code true} when UPDATE/DELETE without a WHERE clause is permitted globally.
     * Defaults to {@code false}; set via {@code jdbcdsl.allow-empty-where=true}.
     */
    public static boolean isAllowEmptyWhere() {
        return allowEmptyWhere.get();
    }

    /**
     * Called by {@link JdbcDslAutoConfiguration} to apply the value read from
     * {@link JdbcDslProperties}. Not intended for direct application use.
     */
    static void setAllowEmptyWhere(boolean value) {
        allowEmptyWhere.set(value);
    }

    /**
     * Returns {@code true} when SELECT queries automatically filter logically-deleted rows
     * (entities with a {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete} field).
     * Defaults to {@code true}; set via {@code jdbcdsl.logical-delete-auto-filter=false}.
     */
    public static boolean isLogicalDeleteAutoFilter() {
        return logicalDeleteAutoFilter.get();
    }

    /**
     * Called by {@link JdbcDslAutoConfiguration} to apply the value read from
     * {@link JdbcDslProperties}. Not intended for direct application use.
     */
    static void setLogicalDeleteAutoFilter(boolean value) {
        logicalDeleteAutoFilter.set(value);
    }

    /**
     * Returns the active {@link NamingStrategy} used by {@link EntityMetaReader} to derive
     * column and table names when no explicit JPA annotation is present.
     * Defaults to {@link DefaultNamingStrategy} (identity).
     */
    public static NamingStrategy getNamingStrategy() {
        return namingStrategy.get();
    }

    /**
     * Sets the active {@link NamingStrategy}.
     * Called by {@link JdbcDslAutoConfiguration}; may also be called directly when
     * running without Spring (e.g. {@code JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE)}).
     */
    public static void setNamingStrategy(NamingStrategy strategy) {
        namingStrategy.set(strategy != null ? strategy : DefaultNamingStrategy.INSTANCE);
        EntityMetaReader.clearCache();
    }
}
