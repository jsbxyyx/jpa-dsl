package io.github.jsbxyyx.jdbcdsl;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime configuration holder for jdbc-dsl.
 *
 * <p>Populated by {@link JdbcDslAutoConfiguration} on startup from {@link JdbcDslProperties}.
 * Builders ({@link UpdateBuilder}, {@link DeleteBuilder}) read from this class so that they
 * do not need a Spring dependency injection context.
 */
public final class JdbcDslConfig {

    private static final AtomicBoolean allowEmptyWhere = new AtomicBoolean(false);

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
}
