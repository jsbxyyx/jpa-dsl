package io.github.jsbxyyx.jdbcdsl.bench;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class ExternalBatchConcurrencyIT {
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
    void adjacentThreadRangesRemainIndependentDuringCleanupAndInserts() throws Exception {
        int threadCount = 32;
        int batchSize = 100;
        List<BatchBenchmark> benchmarks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            BatchBenchmark benchmark = new BatchBenchmark();
            benchmark.batchSize = batchSize;
            benchmark.setup(db);
            benchmarks.add(benchmark);
        }

        ExecutorService workers = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (BatchBenchmark benchmark : benchmarks) {
                results.add(workers.submit(() -> {
                    start.await();
                    benchmark.clearWrites(db);
                    assertEquals(batchSize, benchmark.jdbcBatch(db).length);
                    benchmark.clearWrites(db);
                    assertEquals(batchSize, benchmark.dslChunkedBatch(db).length);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(120, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(120, TimeUnit.SECONDS));
        }
        assertEquals(
                threadCount,
                db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + BenchmarkDatabase.WRITES + " WHERE amount = -1", Integer.class));
        assertEquals(
                threadCount * batchSize,
                db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + BenchmarkDatabase.WRITES + " WHERE amount >= 0", Integer.class));
    }
}
