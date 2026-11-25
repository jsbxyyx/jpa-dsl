package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.BatchInsertBuilder;
import io.github.jsbxyyx.jdbcdsl.JPageable;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.UpsertBuilder;
import io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class ExternalDatabaseCorrectnessIT {
    private static BenchmarkDatabase db;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("JDBC_BENCH_URL");
        assumeTrue(
                url != null && (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:mysql:")),
                "Requires a dedicated PostgreSQL or MySQL database");
        db = new BenchmarkDatabase();
        db.setup();
    }

    @AfterAll
    static void tearDown() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Test
    void scalarBeanAndRecordMappingsHandleNullAndInvalidValues() {
        assertNull(db.dsl.queryOne("SELECT NULL", Map.of(), Long.class));
        assertEquals(42L, db.dsl.queryOne("SELECT CAST(42.0 AS DOUBLE PRECISION)", Map.of(), Long.class));
        for (String type : List.of("REAL", "DOUBLE PRECISION")) {
            String sql = "SELECT CAST(42.0 AS " + type + ") AS amount";
            assertEquals(42, db.dsl.queryOne(sql, Map.of(), BenchmarkRow.class).getAmount());
            assertEquals(new AmountRecord(42), db.dsl.queryOne(sql, Map.of(), AmountRecord.class));
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> db.dsl.queryOne("SELECT CAST(42.5 AS " + type + ") AS amount", Map.of(), AmountRecord.class));
            assertInstanceOf(ArithmeticException.class, failure.getCause());
        }
    }

    @Test
    void mysqlUnsignedBigIntAndDecimalMappingsPreserveExactValues() {
        assumeTrue(System.getenv("JDBC_BENCH_URL").startsWith("jdbc:mysql:"), "MySQL-specific numeric behavior");

        BigInteger unsignedMaximum = new BigInteger("18446744073709551615");
        UnsignedBigIntRow unsignedRow = db.dsl.queryOne(
                "SELECT CAST('18446744073709551615' AS UNSIGNED) AS id", Map.of(), UnsignedBigIntRow.class);
        assertEquals(unsignedMaximum, unsignedRow.getId());

        IllegalStateException overflow = assertThrows(
                IllegalStateException.class,
                () -> db.dsl.queryOne(
                        "SELECT CAST('18446744073709551615' AS UNSIGNED) AS id", Map.of(), SignedLongRow.class));
        assertInstanceOf(ArithmeticException.class, overflow.getCause());

        BigDecimal exactDecimal = new BigDecimal("12345678901234567890.12345678");
        DecimalRow decimalRow = db.dsl.queryOne(
                "SELECT CAST('12345678901234567890.12345678' AS DECIMAL(30,8)) AS amount", Map.of(), DecimalRow.class);
        assertEquals(exactDecimal, decimalRow.getAmount());

        IllegalStateException fractional = assertThrows(
                IllegalStateException.class,
                () -> db.dsl.queryOne("SELECT CAST('12.9' AS DECIMAL(10,1)) AS amount", Map.of(), BigIntegerRow.class));
        assertInstanceOf(ArithmeticException.class, fractional.getCause());

        // Repeat after the mapper cache has been populated; values and conversion failures must remain stable.
        assertEquals(
                unsignedMaximum,
                db.dsl
                        .queryOne(
                                "SELECT CAST('18446744073709551615' AS UNSIGNED) AS id",
                                Map.of(),
                                UnsignedBigIntRow.class)
                        .getId());
        assertEquals(
                exactDecimal,
                db.dsl
                        .queryOne(
                                "SELECT CAST('12345678901234567890.12345678' AS DECIMAL(30,8)) AS amount",
                                Map.of(),
                                DecimalRow.class)
                        .getAmount());
    }

    @Test
    void dateTypedGetterMapsSqlDateAndNullThroughDefaultExecutor() {
        DateRow value = db.dsl.queryOne("SELECT CAST('2026-11-20' AS DATE) AS businessDate", Map.of(), DateRow.class);
        assertEquals(LocalDate.of(2026, 11, 20), value.businessDate());

        DateRow nullValue = db.dsl.queryOne("SELECT CAST(NULL AS DATE) AS businessDate", Map.of(), DateRow.class);
        assertNull(nullValue.businessDate());
    }

    @Test
    void mysqlUpsertInsertsAndUpdatesOnUniqueKeyConflict() throws Exception {
        assumeTrue(System.getenv("JDBC_BENCH_URL").startsWith("jdbc:mysql:"), "MySQL-specific upsert behavior");

        String table = "jpa_dsl_external_upsert_it";
        String lockName = "jpa_dsl_external_upsert_it_lock";
        try (Connection lockConnection = db.dataSource.getConnection();
                Statement lockStatement = lockConnection.createStatement()) {
            try (ResultSet lockResult = lockStatement.executeQuery("SELECT GET_LOCK('" + lockName + "', 30)")) {
                assertTrue(lockResult.next() && lockResult.getInt(1) == 1, "Could not acquire MySQL UPSERT test lock");
            }
            boolean tableCreated = false;
            try {
                db.jdbc.execute("CREATE TABLE " + table + " ("
                        + "id BIGINT NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL, "
                        + "amount INTEGER NOT NULL, "
                        + "UNIQUE KEY uk_jpa_dsl_external_upsert_name (name))");
                tableCreated = true;
                var updateAll = UpsertBuilder.into(MySqlUpsertRow.class)
                        .onConflict(MySqlUpsertRow::getId)
                        .doUpdateAll()
                        .build();
                db.dsl.upsert(updateAll, new MySqlUpsertRow(1L, "first", 10));
                db.dsl.upsert(updateAll, new MySqlUpsertRow(1L, "first-updated", 20));

                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
                assertEquals(
                        new MySqlUpsertRow(1L, "first-updated", 20),
                        db.dsl.queryOne(
                                "SELECT id, name, amount FROM " + table + " WHERE id = 1",
                                Map.of(),
                                MySqlUpsertRow.class));

                var updateNameOnly = UpsertBuilder.into(MySqlUpsertRow.class)
                        .onConflict(MySqlUpsertRow::getId)
                        .doUpdate(MySqlUpsertRow::getName)
                        .build();
                db.dsl.upsert(updateNameOnly, new MySqlUpsertRow(1L, "name-only", 99));
                assertEquals(
                        new MySqlUpsertRow(1L, "name-only", 20),
                        db.dsl.queryOne(
                                "SELECT id, name, amount FROM " + table + " WHERE id = 1",
                                Map.of(),
                                MySqlUpsertRow.class));

                // MySQL detects conflicts from every unique index, not only the builder's conflict target.
                db.dsl.upsert(updateAll, new MySqlUpsertRow(2L, "name-only", 30));
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
                assertEquals(
                        new MySqlUpsertRow(1L, "name-only", 30),
                        db.dsl.queryOne(
                                "SELECT id, name, amount FROM " + table + " WHERE name = 'name-only'",
                                Map.of(),
                                MySqlUpsertRow.class));
            } finally {
                if (tableCreated) {
                    db.jdbc.execute("DROP TABLE " + table);
                }
                lockStatement.execute("SELECT RELEASE_LOCK('" + lockName + "')");
            }
        }
        assertEquals(0, db.dataSource.getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    void sliceUsesStableSortAndLogicalDeleteFilter() {
        SelectSpec<FilteredRow, FilteredRow> spec =
                SelectBuilder.fromCte(db.rowsTable, FilteredRow.class).mapToEntity();
        JPageable<FilteredRow> page = JPageable.of(0, 1, JSort.byAsc(FilteredRow::getId));
        var result = db.dsl.selectSlice(spec, page);
        assertEquals(
                List.of(1L),
                result.getContent().stream().map(FilteredRow::getId).toList());
        assertFalse(result.hasNext());
        assertTrue(db.dsl.selectSlice(spec, page.next()).isEmpty());
    }

    @Test
    void chunkFailureRollsBackEarlierChunks() {
        var spec = BatchInsertBuilder.of(
                        BenchmarkWrite.class,
                        List.of(new BenchmarkWrite(91L, "first", 1), new BenchmarkWrite(91L, "duplicate", 2)))
                .build();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource));
        assertThrows(
                DataAccessException.class,
                () -> transaction.executeWithoutResult(status -> db.dsl.executeBatchInsert(spec, 1)));
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + db.writesTable, Integer.class));
        assertEquals(0, db.dataSource.getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    void callbackFailureReleasesConnection() {
        assertThrows(
                IllegalStateException.class,
                () -> db.dsl.queryEach("SELECT id FROM " + db.rowsTable + " ORDER BY id", Map.of(), Long.class, id -> {
                    throw new IllegalStateException("stop");
                }));
        assertEquals(0, db.dataSource.getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    void concurrentQueriesDoNotMixRecordColumnLayouts() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                boolean reversed = i % 2 == 0;
                results.add(workers.submit(() -> {
                    start.await();
                    String sql = reversed
                            ? "SELECT amount, name, id FROM " + db.rowsTable + " WHERE id = :id"
                            : "SELECT id, name, amount FROM " + db.rowsTable + " WHERE id = :id";
                    for (int row = 1; row <= 50; row++) {
                        assertEquals(
                                new BenchmarkRecord((long) row, "name-" + row, row),
                                db.dsl.queryOne(sql, Map.of("id", row), BenchmarkRecord.class));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
        }
        assertEquals(0, db.dataSource.getHikariPoolMXBean().getActiveConnections());
    }

    @Table(name = "jpa_dsl_external_upsert_it")
    public static class MySqlUpsertRow {
        @Id
        private Long id;

        private String name;
        private Integer amount;

        public MySqlUpsertRow() {}

        MySqlUpsertRow(Long id, String name, Integer amount) {
            this.id = id;
            this.name = name;
            this.amount = amount;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAmount() {
            return amount;
        }

        public void setAmount(Integer amount) {
            this.amount = amount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MySqlUpsertRow that)) return false;
            return java.util.Objects.equals(id, that.id)
                    && java.util.Objects.equals(name, that.name)
                    && java.util.Objects.equals(amount, that.amount);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, name, amount);
        }
    }

    private record AmountRecord(int amount) {}

    public static class UnsignedBigIntRow {
        private BigInteger id;

        public BigInteger getId() {
            return id;
        }

        public void setId(BigInteger id) {
            this.id = id;
        }
    }

    public static class SignedLongRow {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class DecimalRow {
        private BigDecimal amount;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    record DateRow(LocalDate businessDate) {}

    public static class BigIntegerRow {
        private BigInteger amount;

        public BigInteger getAmount() {
            return amount;
        }

        public void setAmount(BigInteger amount) {
            this.amount = amount;
        }
    }

    public static class FilteredRow {
        @Id
        private Long id;

        private String name;

        @LogicalDelete(normalValue = "1", deletedValue = "2")
        private Integer amount;

        public FilteredRow() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAmount() {
            return amount;
        }

        public void setAmount(Integer amount) {
            this.amount = amount;
        }
    }
}
