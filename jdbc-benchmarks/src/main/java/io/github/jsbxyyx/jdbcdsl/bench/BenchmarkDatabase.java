package io.github.jsbxyyx.jdbcdsl.bench;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
public class BenchmarkDatabase {
    static final int DATASET_SIZE = 10_000;
    static final String DEFAULT_TABLE_PREFIX = "jpa_dsl_bench";

    String rowsTable;
    String writesTable;
    String insertWriteSql;
    HikariDataSource dataSource;
    JdbcTemplate jdbc;
    JdbcDslExecutor dsl;
    MyBatisBenchmarkSupport mybatis;
    TransactionTemplate transactionTemplate;
    private boolean rowsCreated;
    private boolean writesCreated;
    private final AtomicLong nextRange = new AtomicLong();

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String url = System.getenv("JDBC_BENCH_URL");
        boolean integrationDefaults =
                url == null || url.isBlank() || (url.startsWith("jdbc:mysql:") && url.contains("mysql-svc"));
        if (url == null || url.isBlank()) {
            url = System.getenv("MYSQL_IT_URL");
            if (url == null || url.isBlank()) {
                String host = System.getenv().getOrDefault("MYSQL_IT_HOST", "mysql-svc");
                String port = System.getenv().getOrDefault("MYSQL_IT_PORT", "3306");
                String database = System.getenv().getOrDefault("MYSQL_IT_DATABASE", "test");
                url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true";
            }
        }
        setupDatabase(url, integrationDefaults);
    }

    void setupLocal() throws Exception {
        setupDatabase(null, false);
    }

    private void setupDatabase(String url, boolean integrationDefaults) throws Exception {
        boolean local = url == null || url.isBlank() || url.startsWith("jdbc:h2:");
        if (local) {
            url = "jdbc:h2:mem:bench_" + UUID.randomUUID().toString().replace("-", "");
        } else if (!"true".equals(System.getenv("JDBC_BENCH_ALLOW_EXTERNAL"))) {
            throw new IllegalStateException(
                    "Set JDBC_BENCH_ALLOW_EXTERNAL=true only for a dedicated benchmark database");
        }
        String configuredPrefix = System.getenv().getOrDefault("JDBC_BENCH_TABLE_PREFIX", DEFAULT_TABLE_PREFIX);
        if (!configuredPrefix.matches("[A-Za-z][A-Za-z0-9_]{0,30}")) {
            throw new IllegalArgumentException(
                    "JDBC_BENCH_TABLE_PREFIX must be a SQL identifier prefix (1-31 letters, digits or underscores)");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        rowsTable = configuredPrefix + "_rows_" + suffix;
        writesTable = configuredPrefix + "_writes_" + suffix;
        insertWriteSql = "INSERT INTO " + writesTable + " (id, name, amount) VALUES (?, ?, ?)";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        String username = System.getenv("JDBC_BENCH_USER");
        String password = System.getenv("JDBC_BENCH_PASSWORD");
        if (integrationDefaults) {
            if (username == null || username.isBlank()) {
                username = System.getenv().getOrDefault("MYSQL_IT_USERNAME", "root");
            }
            if (password == null) {
                password = System.getenv().getOrDefault("MYSQL_IT_PASSWORD", "root");
            }
        }
        config.setUsername(local ? "sa" : username == null ? "" : username);
        config.setPassword(local ? "" : password == null ? "" : password);
        if ("true".equalsIgnoreCase(System.getenv("JDBC_BENCH_USE_CURSOR_FETCH"))) {
            config.addDataSourceProperty("useCursorFetch", "true");
        }
        config.setMaximumPoolSize(32);
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE " + rowsTable + " (id BIGINT PRIMARY KEY, name VARCHAR(100), amount INTEGER)");
            rowsCreated = true;
            jdbc.execute("CREATE TABLE " + writesTable + " (id BIGINT PRIMARY KEY, name VARCHAR(100), amount INTEGER)");
            writesCreated = true;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO " + rowsTable + " (id, name, amount) VALUES (?, ?, ?)")) {
                connection.setAutoCommit(false);
                for (int i = 1; i <= DATASET_SIZE; i++) {
                    statement.setLong(1, i);
                    statement.setString(2, "name-" + i);
                    statement.setInt(3, i);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            }
            dsl = new JdbcDslExecutor(new NamedParameterJdbcTemplate(jdbc));
            mybatis = new MyBatisBenchmarkSupport(dataSource, rowsTable, writesTable);
            transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        } catch (Exception e) {
            try {
                tearDown();
            } catch (Exception cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    long reserveRange(int size) {
        return nextRange.getAndUpdate(start -> Math.addExact(start, Math.addExact(size, 1)));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        try {
            if (writesCreated) {
                jdbc.execute("DROP TABLE " + writesTable);
                writesCreated = false;
            }
            if (rowsCreated) {
                jdbc.execute("DROP TABLE " + rowsTable);
                rowsCreated = false;
            }
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }
}
