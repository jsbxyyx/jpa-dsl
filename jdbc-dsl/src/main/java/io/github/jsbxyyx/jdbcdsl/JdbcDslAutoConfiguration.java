package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.DialectDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for the jdbc-dsl.
 *
 * <p>Activated when {@code jdbcdsl.enabled} is {@code true} (the default). Disable with
 * {@code jdbcdsl.enabled=false} in {@code application.properties}.
 *
 * <p>If no {@link Dialect} bean is present the dialect is auto-detected from the DataSource's
 * JDBC metadata via {@link DialectDetector#detect(DataSource)}:
 * <ul>
 *   <li>MySQL / MariaDB → {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect}</li>
 *   <li>PostgreSQL → {@link io.github.jsbxyyx.jdbcdsl.dialect.PostgresDialect}</li>
 *   <li>Microsoft SQL Server → {@link io.github.jsbxyyx.jdbcdsl.dialect.SqlServerDialect}</li>
 *   <li>H2 → {@link io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect}</li>
 *   <li>unrecognised → {@link io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect} (with a {@code WARN} log)</li>
 * </ul>
 *
 * <p>A user-provided {@link Dialect} bean always takes priority over auto-detection.
 * Similarly, a user-provided {@link JdbcDslExecutor} bean suppresses this auto-config executor.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass({NamedParameterJdbcTemplate.class, JdbcDslExecutor.class})
@ConditionalOnProperty(prefix = "jdbcdsl", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JdbcDslProperties.class)
public class JdbcDslAutoConfiguration {

    /**
     * Applies {@link JdbcDslProperties} values to the static {@link JdbcDslConfig} holder so
     * that {@link UpdateBuilder} and {@link DeleteBuilder} can read them without requiring
     * Spring injection.
     */
    @Bean
    public JdbcDslProperties jdbcDslProperties(JdbcDslProperties properties) {
        JdbcDslConfig.setAllowEmptyWhere(properties.isAllowEmptyWhere());
        return properties;
    }

    /**
     * Creates a {@link Dialect} bean by auto-detecting the database product from the DataSource's
     * JDBC metadata. Delegates to {@link DialectDetector#detect(DataSource)}.
     */
    @Bean
    @ConditionalOnMissingBean(Dialect.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public Dialect jdbcDslDialect(DataSource dataSource) {
        return DialectDetector.detect(dataSource);
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
