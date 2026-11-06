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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
public class BenchmarkDatabase {
    static final int DATASET_SIZE = 10_000;
    static final String ROWS = "jpa_dsl_bench_rows";
    static final String WRITES = "jpa_dsl_bench_writes";
    static final String INSERT_WRITE = "INSERT INTO " + WRITES + " (id, name, amount) VALUES (?, ?, ?)";

    HikariDataSource dataSource;
    JdbcTemplate jdbc;
    JdbcDslExecutor dsl;
    private boolean rowsCreated;
    private boolean writesCreated;
    private final AtomicLong nextRange = new AtomicLong();

    @Setup(Level.Trial)
    public void setup() throws Exception {
        setupDatabase(System.getenv("JDBC_BENCH_URL"));
    }

    void setupLocal() throws Exception {
        setupDatabase(null);
    }

    private void setupDatabase(String url) throws Exception {
        boolean local = url == null || url.isBlank();
        if (local) {
            url = "jdbc:h2:mem:bench_" + UUID.randomUUID().toString().replace("-", "");
        } else if (!"true".equals(System.getenv("JDBC_BENCH_ALLOW_EXTERNAL"))) {
            throw new IllegalStateException(
                    "Set JDBC_BENCH_ALLOW_EXTERNAL=true only for a dedicated benchmark database");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(local ? "sa" : System.getenv().getOrDefault("JDBC_BENCH_USER", ""));
        config.setPassword(local ? "" : System.getenv().getOrDefault("JDBC_BENCH_PASSWORD", ""));
        config.setMaximumPoolSize(32);
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE " + ROWS + " (id BIGINT PRIMARY KEY, name VARCHAR(100), amount INTEGER)");
            rowsCreated = true;
            jdbc.execute("CREATE TABLE " + WRITES + " (id BIGINT PRIMARY KEY, name VARCHAR(100), amount INTEGER)");
            writesCreated = true;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO " + ROWS + " (id, name, amount) VALUES (?, ?, ?)")) {
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
                jdbc.execute("DROP TABLE " + WRITES);
                writesCreated = false;
            }
            if (rowsCreated) {
                jdbc.execute("DROP TABLE " + ROWS);
                rowsCreated = false;
            }
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }
}
