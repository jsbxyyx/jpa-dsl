package io.github.jsbxyyx.jdbcdsl.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
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
 * </ol>
 * If detection fails, callers must explicitly configure a dialect.
 */
public final class DialectDetector {

    private static final Logger log = LoggerFactory.getLogger(DialectDetector.class);

    private DialectDetector() {}

    /**
     * Detects the {@link Dialect} from the given {@link DataSource}.
     *
     * @param dataSource the DataSource to probe
     * @return a non-null {@link Dialect} appropriate for the database
     * @throws IllegalStateException if the database cannot be reached or its dialect is unknown
     */
    public static Dialect detect(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalStateException(
                    "Cannot detect SQL dialect without a DataSource; configure a Dialect explicitly");
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (meta == null) {
                throw new IllegalStateException(
                        "Cannot detect SQL dialect: database metadata is null; configure a Dialect explicitly");
            }
            String productName = meta.getDatabaseProductName();
            if (productName == null || productName.isBlank()) {
                throw new IllegalStateException(
                        "Cannot detect SQL dialect: database product name is empty; configure a Dialect explicitly");
            }

            // 1. User SPI providers (highest priority)
            for (DialectProvider provider : ServiceLoader.load(DialectProvider.class)) {
                if (provider.supports(productName)) {
                    log.debug(
                            "jdbc-dsl: using SPI dialect provider {} for '{}'",
                            provider.getClass().getName(),
                            productName);
                    Dialect dialect = provider.create();
                    if (dialect == null) {
                        throw new IllegalStateException(
                                "Dialect provider " + provider.getClass().getName() + " returned null");
                    }
                    return dialect;
                }
            }

            // 2. Built-in detection
            String lower = productName.toLowerCase(Locale.ROOT);
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
            throw new IllegalStateException(
                    "Unrecognized database product '" + productName + "'; configure a Dialect explicitly");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect SQL dialect; configure a Dialect explicitly", e);
        }
    }
}
