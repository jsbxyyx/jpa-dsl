package io.github.jsbxyyx.jdbcdsl.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Detects the appropriate {@link Dialect} from a {@link DataSource} by inspecting the JDBC
 * database product name reported by {@link DatabaseMetaData#getDatabaseProductName()}.
 *
 * <p>Detection rules:
 * <ul>
 *   <li>MySQL / MariaDB → {@link MySqlDialect}</li>
 *   <li>PostgreSQL → {@link PostgresDialect}</li>
 *   <li>Microsoft SQL Server → {@link SqlServerDialect}</li>
 *   <li>H2 → {@link H2Dialect}</li>
 *   <li>Oracle → {@link OracleDialect} (12c+)</li>
 *   <li>unrecognised or {@code null} DataSource → {@link Sql2008Dialect} (with a {@code WARN} log)</li>
 * </ul>
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
