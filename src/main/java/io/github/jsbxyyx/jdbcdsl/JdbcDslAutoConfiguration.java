package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Spring Boot auto-configuration for jdbc-dsl.
 *
 * <p>Activated by default; disable with {@code jdbcdsl.enabled=false}.
 *
 * <p>Automatically detects the database {@link Dialect} from the {@link DataSource} metadata:
 * <ul>
 *   <li>MySQL or MariaDB → {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect}</li>
 *   <li>Unknown product  → {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect} (fallback, with WARN log)</li>
 * </ul>
 *
 * <p>To override the auto-detected dialect, expose a {@link Dialect} bean in your application context.
 *
 * <p>This auto-configuration is completely independent of the {@code jpadsl} auto-configurations;
 * both can be active simultaneously and do not interfere with each other.
 */
@AutoConfiguration
@ConditionalOnClass({NamedParameterJdbcTemplate.class, JdbcDslExecutor.class})
@ConditionalOnProperty(prefix = "jdbcdsl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcDslAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JdbcDslAutoConfiguration.class);

    /**
     * Creates a {@link Dialect} bean by inspecting the {@link DataSource}'s JDBC metadata.
     *
     * <p>Skipped if the application already provides a {@link Dialect} bean.
     */
    @Bean
    @ConditionalOnMissingBean(Dialect.class)
    public Dialect jdbcDslDialect(DataSource dataSource) {
        String productName = detectProductName(dataSource);
        Dialect dialect = selectDialect(productName);
        log.debug("jdbc-dsl: using dialect {} (detected database product: '{}')",
                dialect.getClass().getSimpleName(), productName);
        return dialect;
    }

    /**
     * Creates the {@link JdbcDslExecutor} bean wired with the auto-detected (or user-provided)
     * {@link Dialect}.
     */
    @Bean
    @ConditionalOnMissingBean(JdbcDslExecutor.class)
    public JdbcDslExecutor jdbcDslExecutor(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                           Dialect jdbcDslDialect) {
        return new JdbcDslExecutor(namedParameterJdbcTemplate, jdbcDslDialect);
    }

    // ------------------------------------------------------------------ //
    //  Dialect detection helpers
    // ------------------------------------------------------------------ //

    private static String detectProductName(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName();
        } catch (Exception e) {
            log.warn("jdbc-dsl: failed to read DataSource metadata for dialect detection ({}). "
                    + "Falling back to MySqlDialect.", e.getMessage());
            return "";
        }
    }

    private static Dialect selectDialect(String productName) {
        if (productName != null) {
            String upper = productName.toUpperCase();
            if (upper.contains("MYSQL") || upper.contains("MARIADB")) {
                return new MySqlDialect();
            }
        }
        // Unknown product — default to MySQL dialect and warn
        log.warn("jdbc-dsl: unrecognized database product '{}'. Defaulting to MySqlDialect. "
                + "Override by exposing a Dialect bean.", productName);
        return new MySqlDialect();
    }
}
