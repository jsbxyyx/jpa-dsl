package io.github.jsbxyyx.jdbcdsl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for jdbc-dsl, bound from the {@code jdbcdsl} prefix.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * # Allow UPDATE/DELETE without a WHERE clause (default: false)
 * jdbcdsl.allow-empty-where=true
 * </pre>
 */
@ConfigurationProperties(prefix = "jdbcdsl")
public class JdbcDslProperties {

    /**
     * Whether to allow UPDATE and DELETE statements without a WHERE clause.
     * Defaults to {@code false} (protective behaviour: missing WHERE throws
     * {@link IllegalStateException}). Set to {@code true} to disable the guard globally.
     */
    private boolean allowEmptyWhere = false;

    public boolean isAllowEmptyWhere() {
        return allowEmptyWhere;
    }

    public void setAllowEmptyWhere(boolean allowEmptyWhere) {
        this.allowEmptyWhere = allowEmptyWhere;
    }
}
