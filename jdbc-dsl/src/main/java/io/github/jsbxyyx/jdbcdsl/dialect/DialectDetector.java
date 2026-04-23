package io.github.jsbxyyx.jdbcdsl.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ServiceLoader;

/**
 * Detects the appropriate {@link Dialect} from a {@link DataSource} by inspecting the JDBC
 * database product name reported by {@link DatabaseMetaData#getDatabaseProductName()}.
 *
 * <p>Detection order:
 * <ol>
 *   <li>User-registered {@link DialectProvider} instances discovered via {@link ServiceLoader}
 *       (highest priority — consulted first, in iteration order).</li>
 *   <li>Built-in rules:
 *     <ul>
 *       <li>MySQL / MariaDB → {@link MySqlDialect}</li>
 *       <li>PostgreSQL → {@link PostgresDialect}</li>
 *       <li>Microsoft SQL Server → {@link SqlServerDialect}</li>
 *       <li>H2 → {@link H2Dialect}</li>
 *       <li>Oracle → {@link OracleDialect} (12c+)</li>
 *       <li>DM (达梦) → {@link DmDialect}</li>
 *       <li>KingbaseES (人大金仓) → {@link KingbaseDialect}</li>
 *     </ul>
 *   </li>
 *   <li>Fallback: {@link Sql2008Dialect} (with a {@code WARN} log).</li>
 * </ol>
 */
public final class DialectDetector {

    private static final Logger log = LoggerFactory.getLogger(DialectDetector.class);

    private DialectDetector() {
    }

    /**
     * Detects the {@link Dialect} from the given {@link DataSource}.
     *
     * <p>Falls back to {@link Sql2008Dialect} when the DataSource is {@code null}, when the
     * database product name is unrecognised, or when an exception occurs while obtaining a
     * connection.
     *
     * @param dataSource the DataSource to probe (may be {@code null})
     * @return a non-null {@link Dialect} appropriate for the database
     */
    public static Dialect detect(DataSource dataSource) {
        if (dataSource == null) {
            log.warn("jdbc-dsl: DataSource is null, falling back to Sql2008Dialect");
            return new Sql2008Dialect();
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();

            // 1. User SPI providers (highest priority)
            for (DialectProvider provider : ServiceLoader.load(DialectProvider.class)) {
                if (provider.supports(productName)) {
                    log.debug("jdbc-dsl: using SPI dialect provider {} for '{}'",
                            provider.getClass().getName(), productName);
                    return provider.create();
                }
            }

            // 2. Built-in detection
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
                if (lower.contains("oracle")) {
                    return new OracleDialect();
                }
                if (lower.contains("dm dbms") || lower.startsWith("dm")) {
                    return new DmDialect();
                }
                if (lower.contains("kingbase")) {
                    return new KingbaseDialect();
                }
                log.warn("jdbc-dsl: unrecognized database product '{}', falling back to Sql2008Dialect",
                        productName);
            } else {
                log.warn("jdbc-dsl: database product name is null, falling back to Sql2008Dialect");
            }
        } catch (Exception e) {
            log.warn("jdbc-dsl: failed to detect database dialect ({}), falling back to Sql2008Dialect",
                    e.getMessage());
        }
        return new Sql2008Dialect();
    }
}
