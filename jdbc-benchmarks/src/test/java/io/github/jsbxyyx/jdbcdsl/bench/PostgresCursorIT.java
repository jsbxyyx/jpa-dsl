package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class PostgresCursorIT {
    private BenchmarkDatabase db;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("JDBC_BENCH_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Requires a dedicated PostgreSQL database");
        db = new BenchmarkDatabase();
        db.setup();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Test
    void processesRawRowsWithAnOpenServerCursorInsideTransaction() {
        assertOpenCursor(consumer -> db.dsl.queryEach(
                "SELECT id FROM " + BenchmarkDatabase.ROWS + " ORDER BY id", Map.of(), Long.class, consumer));
    }

    @Test
    void processesDslRowsWithAnOpenServerCursorInsideTransaction() {
        SelectSpec<BenchmarkRow, BenchmarkRow> spec =
                SelectBuilder.from(BenchmarkRow.class).mapToEntity();
        assertOpenCursor(consumer -> db.dsl.selectEach(spec, row -> consumer.accept(row.getId())));
    }

    private void assertOpenCursor(Consumer<Consumer<Long>> readRows) {
        db.jdbc.setFetchSize(64);
        JdbcTemplate probe = new JdbcTemplate(db.dataSource);
        probe.setFetchSize(0);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource));
        AtomicInteger rows = new AtomicInteger();
        transaction.executeWithoutResult(status -> readRows.accept(id -> {
            if (rows.getAndIncrement() == 0) {
                Integer cursors =
                        probe.queryForObject("SELECT COUNT(*) FROM pg_cursors WHERE name <> ''", Integer.class);
                assertTrue(cursors != null && cursors > 0, "Expected an open server-side cursor");
            }
        }));
        assertEquals(BenchmarkDatabase.DATASET_SIZE, rows.get());
        assertEquals(0, db.dataSource.getHikariPoolMXBean().getActiveConnections());
    }
}
