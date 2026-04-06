package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.PostgresDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.SqlServerDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Spring Boot auto-configuration for the jdbc-dsl.
 *
 * <p>Activated when {@code jdbcdsl.enabled} is {@code true} (the default). Disable with
 * {@code jdbcdsl.enabled=false} in {@code application.properties}.
 *
 * <p>If no {@link Dialect} bean is present the dialect is auto-detected from the DataSource's
 * JDBC metadata:
 * <ul>
 *   <li>MySQL / MariaDB → {@link MySqlDialect}</li>
 *   <li>PostgreSQL → {@link PostgresDialect}</li>
 *   <li>Microsoft SQL Server → {@link SqlServerDialect}</li>
 *   <li>H2 → {@link H2Dialect}</li>
 *   <li>unrecognised → {@link MySqlDialect} (with a {@code WARN} log)</li>
 * </ul>
 *
 * <p>A user-provided {@link Dialect} bean always takes priority over auto-detection.
 * Similarly, a user-provided {@link JdbcDslExecutor} bean suppresses this auto-config executor.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass({NamedParameterJdbcTemplate.class, JdbcDslExecutor.class})
@ConditionalOnProperty(prefix = "jdbcdsl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcDslAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JdbcDslAutoConfiguration.class);

    /**
     * Creates a {@link Dialect} bean by auto-detecting the database product from the DataSource's
     * JDBC metadata. Falls back to {@link MySqlDialect} when detection fails, and logs a warning.
     */
    @Bean
    @ConditionalOnMissingBean(Dialect.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public Dialect jdbcDslDialect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            if (productName != null) {
                String lower = productName.toLowerCase();
                if (lower.contains("mysql") || lower.contains("mariadb")) {
                    return new MySqlDialect();
                }
                if (lower.contains("postgres")) {
                    return new PostgresDialect();
                }
                if (lower.contains("sql server")) {
                    return new SqlServerDialect();
                }
                if (lower.contains("h2")) {
                    return new H2Dialect();
                }
                log.warn("jdbc-dsl: unrecognized database product '{}', falling back to MySqlDialect", productName);
            } else {
                log.warn("jdbc-dsl: database product name is null, falling back to MySqlDialect");
            }
        } catch (Exception e) {
            log.warn("jdbc-dsl: failed to detect database dialect ({}), falling back to MySqlDialect",
                    e.getMessage());
        }
        return new MySqlDialect();
    }

    /**
     * Creates a {@link JdbcDslExecutor} bean wired with the auto-detected (or user-provided)
     * {@link Dialect}.
     */
    @Bean
    @ConditionalOnMissingBean(JdbcDslExecutor.class)
    @ConditionalOnBean({NamedParameterJdbcTemplate.class, Dialect.class})
    public JdbcDslExecutor jdbcDslExecutor(NamedParameterJdbcTemplate jdbc, Dialect dialect) {
        return new JdbcDslExecutor(jdbc, dialect);
    }
}
