package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.UpsertBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class PostgresBatchIT {
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
    void batchAndChunkedBatchPersistTheSameOrderedValues() {
        BatchBenchmark benchmark = new BatchBenchmark();
        benchmark.batchSize = 201;
        benchmark.setup(db);
        List<BenchmarkRecord> expected = IntStream.range(0, 201)
                .mapToObj(i -> new BenchmarkRecord((long) i, "name-" + i, i))
                .toList();

        assertEquals(201, benchmark.dslBatch(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.dslChunkedBatch(db).length);
        assertEquals(expected, writes());
    }

    @Test
    void postgresDoNothingSkipsDuplicatesButRejectsInvalidData() {
        var spec = UpsertBuilder.into(BenchmarkWrite.class)
                .onConflict(BenchmarkWrite::getId)
                .doNothing()
                .build();
        db.dsl.upsert(spec, new BenchmarkWrite(1L, "original", 1));
        db.dsl.upsert(spec, new BenchmarkWrite(1L, "changed", 2));
        assertThrows(DataAccessException.class, () -> db.dsl.upsert(spec, new BenchmarkWrite(2L, "x".repeat(101), 3)));
        assertEquals(List.of(new BenchmarkRecord(1L, "original", 1)), writes());
    }

    private List<BenchmarkRecord> writes() {
        return db.jdbc.query(
                "SELECT id, name, amount FROM " + BenchmarkDatabase.WRITES + " WHERE amount >= 0 ORDER BY id",
                (rs, rowNum) -> QueryBenchmark.readRecord(rs));
    }
}
