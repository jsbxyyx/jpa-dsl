package io.github.jsbxyyx.jdbcdsl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for jdbc-dsl, bound from the {@code jdbcdsl} prefix.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * # Allow UPDATE/DELETE without a WHERE clause (default: false)
 * jdbcdsl.allow-empty-where=true
 *
 * # Automatically filter logically-deleted rows in SELECT (default: true)
 * jdbcdsl.logical-delete-auto-filter=false
 *
 * # Column/table naming strategy when no explicit @Column/@Table annotation is present
 * # Values: default (identity, legacy behaviour) | snake_case (camelCase → snake_case)
 * jdbcdsl.naming-strategy=snake_case
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

    /**
     * Whether SELECT queries automatically append {@code AND deleted_col = normalValue}
     * when the root entity has a field annotated with
     * {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete}.
     * Defaults to {@code true}.
     */
    private boolean logicalDeleteAutoFilter = true;

    /**
     * Naming strategy used to derive column and table names when no explicit JPA annotation
     * ({@code @Column}/{@code @Table}) is present.
     *
     * <ul>
     *   <li>{@code default} — returns Java names unchanged (legacy behaviour).</li>
     *   <li>{@code snake_case} — converts {@code camelCase} to {@code snake_case}.</li>
     * </ul>
     *
     * A user-provided {@link io.github.jsbxyyx.jdbcdsl.NamingStrategy} Spring bean always
     * takes priority over this property.
     */
    private String namingStrategy = "default";

    public boolean isAllowEmptyWhere() {
        return allowEmptyWhere;
    }

    public void setAllowEmptyWhere(boolean allowEmptyWhere) {
        this.allowEmptyWhere = allowEmptyWhere;
    }

    public boolean isLogicalDeleteAutoFilter() {
        return logicalDeleteAutoFilter;
    }

    public void setLogicalDeleteAutoFilter(boolean logicalDeleteAutoFilter) {
        this.logicalDeleteAutoFilter = logicalDeleteAutoFilter;
    }

    public String getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(String namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
}
