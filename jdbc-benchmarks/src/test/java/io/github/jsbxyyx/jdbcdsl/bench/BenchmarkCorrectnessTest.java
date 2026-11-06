package io.github.jsbxyyx.jdbcdsl.bench;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCorrectnessTest {
    private static BenchmarkDatabase db;

    @BeforeAll
    static void startDatabase() throws Exception {
        db = new BenchmarkDatabase();
        db.setupLocal();
    }

    @AfterAll
    static void closeDatabase() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Test
    void queryVariantsReturnIdenticalRows() throws Exception {
        for (int count : List.of(1, 100, 10_000)) {
            QueryBenchmark benchmark = new QueryBenchmark();
            benchmark.rowCount = count;
            benchmark.setup();
            List<BenchmarkRecord> expected = benchmark.jdbcRecord(db);
            assertEquals(count, expected.size());
            assertEquals(new BenchmarkRecord(1L, "name-1", 1), expected.get(0));
            assertEquals(expected, benchmark.springRecord(db));
            assertEquals(expected, benchmark.dslRawRecord(db));
            assertEquals(expected, records(benchmark.jdbcBean(db)));
            assertEquals(expected, records(benchmark.springBean(db)));
            assertEquals(expected, records(benchmark.dslRawBean(db)));
            assertEquals(expected, records(benchmark.dslSelectBean(db)));
            assertEquals(expected, records(benchmark.dslBuildAndSelectBean(db)));
            assertEquals(expected, records(benchmark.dslPageBean(db).getContent()));
            assertEquals(expected, records(benchmark.dslSliceBean(db).getContent()));
            assertEquals(
                    count < BenchmarkDatabase.DATASET_SIZE,
                    benchmark.dslSliceBean(db).hasNext());
            assertEquals(
                    Math.min(count + 1, BenchmarkDatabase.DATASET_SIZE),
                    benchmark.dslPageBean(db).getTotalElements());
            List<Long> ids = expected.stream().map(BenchmarkRecord::id).toList();
            assertEquals(ids, benchmark.jdbcScalar(db));
            assertEquals(ids, benchmark.springScalar(db));
            assertEquals(ids, benchmark.dslRawScalar(db));
        }
    }

    @Test
    void mappingVariantsReturnIdenticalRows() throws Exception {
        MappingBenchmark benchmark = new MappingBenchmark();
        benchmark.rowCount = 100;
        benchmark.setup(db);
        List<BenchmarkRecord> expected = benchmark.directRecord();
        assertEquals(100, expected.size());
        assertEquals(records(benchmark.directBean()), expected);
        assertEquals(expected, recordsObjects(benchmark.dslBean()));
        assertEquals(expected, recordsObjects(benchmark.dslColdBean()));
        assertEquals(expected, benchmark.dslRecord());
        assertEquals(expected.stream().map(BenchmarkRecord::id).toList(), benchmark.directScalar());
        assertEquals(benchmark.directScalar(), benchmark.dslScalar());
        benchmark.tearDown();
    }

    @Test
    void batchVariantsInsertIdenticalRows() throws Exception {
        BatchBenchmark benchmark = new BatchBenchmark();
        benchmark.batchSize = 201;
        benchmark.setup(db);
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.jdbcBatch(db).length);
        List<BenchmarkRecord> expected = writes();
        assertEquals(201, expected.size());
        assertEquals(new BenchmarkRecord(0L, "name-0", 0), expected.get(0));
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.springBatch(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.dslBatch(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.dslChunkedBatch(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(
                1,
                db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + BenchmarkDatabase.WRITES + " WHERE amount = -1", Integer.class));
    }

    @Test
    void batchPreparationRetainsSqlAndEveryParameter() {
        BatchPreparationBenchmark benchmark = new BatchPreparationBenchmark();
        benchmark.batchSize = 100;
        benchmark.setup();
        assertEquals(100, benchmark.prepareBatch().length);
        assertTrue(benchmark.jdbc.sql.contains("jpa_dsl_bench_writes"));
        assertEquals(100, benchmark.jdbc.parameters.length);
        for (int i = 0; i < 100; i++) {
            var params = benchmark.jdbc.parameters[i];
            assertEquals((long) i, params.getValue("id"));
            assertEquals("name-" + i, params.getValue("name"));
            assertEquals(i, params.getValue("amount"));
        }
    }

    @Test
    void paginationRenderingScenariosPreserveParameterNames() {
        PaginationRenderingBenchmark benchmark = new PaginationRenderingBenchmark();
        benchmark.scenario = "normal";
        benchmark.setup();
        assertTrue(benchmark.renderPagination().endsWith("LIMIT :_limit OFFSET :_offset"));
        benchmark.scenario = "quoted";
        benchmark.setup();
        assertTrue(benchmark.renderPagination().endsWith("LIMIT :_limit OFFSET :_offset"));
        benchmark.scenario = "collision";
        benchmark.setup();
        assertTrue(benchmark.renderPagination().endsWith("LIMIT :_limit_1 OFFSET :_offset_1"));
    }

    @Test
    void numericConversionBenchmarkProducesEquivalentValues() {
        NumericConversionBenchmark benchmark = new NumericConversionBenchmark();
        for (String source : List.of("double", "float")) {
            benchmark.source = source;
            benchmark.setup();
            assertEquals(128L, benchmark.convertExactInteger());
            assertEquals(-129L, benchmark.convertExactInteger());
            assertEquals(65536L, benchmark.convertExactInteger());
            assertEquals(1234567L, benchmark.convertExactInteger());
        }
    }

    private static List<BenchmarkRecord> writes() {
        return db.jdbc.query(
                "SELECT id, name, amount FROM " + BenchmarkDatabase.WRITES + " WHERE amount >= 0 ORDER BY id",
                (rs, rowNum) -> QueryBenchmark.readRecord(rs));
    }

    private static List<BenchmarkRecord> records(List<BenchmarkRow> rows) {
        return rows.stream()
                .map(row -> new BenchmarkRecord(row.getId(), row.getName(), row.getAmount()))
                .toList();
    }

    private static List<BenchmarkRecord> recordsObjects(List<Object> rows) {
        return rows.stream()
                .map(BenchmarkRow.class::cast)
                .map(row -> new BenchmarkRecord(row.getId(), row.getName(), row.getAmount()))
                .toList();
    }
}
