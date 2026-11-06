package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JPageable;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.LockMode;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class ExternalLockingIT {
    private static BenchmarkDatabase db;
    private static TransactionTemplate transaction;
    private static boolean mysql;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("JDBC_BENCH_URL");
        assumeTrue(
                url != null && (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:mysql:")),
                "Requires a dedicated PostgreSQL or MySQL database");
        mysql = url.startsWith("jdbc:mysql:");
        db = new BenchmarkDatabase();
        db.setup();
        transaction = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource));
    }

    @AfterAll
    static void tearDown() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Test
    void lockedPaginationWorksForAllExecutionPathsAndModes() {
        JPageable<BenchmarkRow> page = JPageable.of(0, 1, JSort.byAsc(BenchmarkRow::getId));
        for (LockMode mode : LockMode.values()) {
            transaction.executeWithoutResult(status -> {
                var spec = SelectBuilder.from(BenchmarkRow.class)
                        .where(w -> w.eq(BenchmarkRow::getId, 1L))
                        .forUpdate(mode)
                        .mapToEntity();
                assertEquals(1L, db.dsl.findOne(spec).getId());
                assertEquals(1L, db.dsl.findOne(spec, page).getId());
                assertEquals(1L, db.dsl.select(spec, page).get(0).getId());
                assertEquals(
                        1L, db.dsl.selectPage(spec, page).getContent().get(0).getId());
                assertEquals(
                        1L, db.dsl.selectSlice(spec, page).getContent().get(0).getId());
                List<Long> ids = new ArrayList<>();
                db.dsl.selectEach(spec, page, row -> ids.add(row.getId()));
                assertEquals(List.of(1L), ids);
            });
        }
    }

    @Test
    void paginatedFindOneActuallyHoldsRowLockUntilCommit() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                var spec = SelectBuilder.from(BenchmarkRow.class)
                        .where(w -> w.eq(BenchmarkRow::getId, 1L))
                        .forUpdate()
                        .mapToEntity();
                assertEquals(1L, db.dsl.findOne(spec).getId());
                assertLocked(worker, 1L);
            });
            Future<Boolean> afterCommit = worker.submit(() -> tryLock(1L));
            assertTrue(afterCommit.get(10, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void lockedSliceAlsoLocksExtraRowUsedForHasNext() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                var spec = SelectBuilder.from(BenchmarkRow.class)
                        .where(w -> w.lte(BenchmarkRow::getId, 3L))
                        .forUpdate(LockMode.UPDATE_SKIP_LOCKED)
                        .mapToEntity();
                var slice = db.dsl.selectSlice(spec, JPageable.of(0, 1, JSort.byAsc(BenchmarkRow::getId)));
                assertEquals(1L, slice.getContent().get(0).getId());
                assertTrue(slice.hasNext());
                assertLocked(worker, 1L);
                assertLocked(worker, 2L);
                try {
                    assertTrue(worker.submit(() -> tryLock(3L)).get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    throw new AssertionError("Failed to verify unlocked third row", e);
                }
            });
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void pageCountDoesNotSkipRowsLockedByAnotherTransaction() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                var first = SelectBuilder.from(BenchmarkRow.class)
                        .where(w -> w.eq(BenchmarkRow::getId, 1L))
                        .forUpdate()
                        .mapToEntity();
                assertEquals(1L, db.dsl.findOne(first).getId());
                Future<?> result = worker.submit(() -> transaction.executeWithoutResult(other -> {
                    var spec = SelectBuilder.from(BenchmarkRow.class)
                            .where(w -> w.lte(BenchmarkRow::getId, 2L))
                            .forUpdate(LockMode.UPDATE_SKIP_LOCKED)
                            .mapToEntity();
                    var page = db.dsl.selectPage(spec, JPageable.of(0, 1, JSort.byAsc(BenchmarkRow::getId)));
                    assertEquals(2L, page.getContent().get(0).getId());
                    assertEquals(2L, page.getTotalElements());
                }));
                try {
                    result.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("Failed to verify SKIP LOCKED page", e);
                }
            });
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void assertLocked(ExecutorService worker, long id) {
        try {
            assertFalse(worker.submit(() -> tryLock(id)).get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            throw new AssertionError("Failed to verify row lock for " + id, e);
        }
    }

    private static boolean tryLock(long id) throws SQLException {
        try (Connection connection = db.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM " + BenchmarkDatabase.ROWS + " WHERE id = ? FOR UPDATE NOWAIT")) {
                statement.setLong(1, id);
                try {
                    statement.executeQuery().close();
                    return true;
                } catch (SQLException failure) {
                    if (mysql) {
                        assertEquals(3572, failure.getErrorCode());
                    } else {
                        assertEquals("55P03", failure.getSQLState());
                    }
                    return false;
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
