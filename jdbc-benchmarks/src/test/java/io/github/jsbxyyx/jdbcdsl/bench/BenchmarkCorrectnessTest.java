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
    void benchmarkInstancesUseDistinctTablesAndIndependentMyBatisMappings() throws Exception {
        BenchmarkDatabase second = new BenchmarkDatabase();
        try {
            second.setupLocal();
            assertNotEquals(db.rowsTable, second.rowsTable);
            assertNotEquals(db.writesTable, second.writesTable);
            assertEquals(1, db.mybatis.selectRows(1).size());
            assertEquals(1, second.mybatis.selectRows(1).size());
        } finally {
            second.tearDown();
        }
        assertEquals(1, db.mybatis.selectRows(1).size());
    }

    @Test
    void queryVariantsReturnIdenticalRows() throws Exception {
        for (int count : List.of(1, 100, 10_000)) {
            QueryBenchmark benchmark = new QueryBenchmark();
            benchmark.rowCount = count;
            benchmark.setup(db);
            List<BenchmarkRecord> expected = benchmark.jdbcRecord(db);
            assertEquals(count, expected.size());
            assertEquals(new BenchmarkRecord(1L, "name-1", 1), expected.get(0));
            assertEquals(expected, benchmark.springRecord(db));
            assertEquals(expected, benchmark.dslRawRecord(db));
            assertEquals(expected, records(benchmark.jdbcBean(db)));
            assertEquals(expected, records(benchmark.springBean(db)));
            assertEquals(expected, records(benchmark.springHandwrittenBean(db)));
            assertEquals(expected, records(benchmark.springNamedHandwrittenBean(db)));
            assertEquals(expected, records(benchmark.dslMappedBean(db)));
            assertEquals(expected, records(benchmark.mybatisBean(db)));
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
    void isolatedFrameworkExecutionVariantsReturnIdenticalRows() throws Exception {
        for (int count : List.of(1, 100, 10_000)) {
            FrameworkExecutionBenchmark benchmark = new FrameworkExecutionBenchmark();
            benchmark.rowCount = count;
            benchmark.setup();
            try {
                List<BenchmarkRecord> expected = benchmark.jdbcHandwritten().stream()
                        .map(row -> new BenchmarkRecord(row.getId(), row.getName(), row.getAmount()))
                        .toList();
                assertEquals(count, expected.size());
                assertEquals(expected, toRecords(benchmark.springJdbcHandwritten()));
                assertEquals(expected, toRecords(benchmark.springJdbcBeanProperty()));
                assertEquals(expected, toRecords(benchmark.springNamedJdbcHandwritten()));
                assertEquals(expected, toRecords(benchmark.jpaDslMapped()));
                assertEquals(expected, toRecords(benchmark.myBatisDefaultMapping()));
                assertEquals(expected, toRecords(benchmark.myBatisResultMap()));
                assertEquals(expected, jooqRecords(benchmark.jooqDsl()));
                assertEquals(expected, jooqRecords(benchmark.jooqDslPrebuilt()));
                assertEquals(expected, toRecords(benchmark.jpaDslBuildAndSelect()));
                assertEquals(expected, toRecords(benchmark.jpaDsl()));
                assertEquals(expected, toRecords(benchmark.jpaDslRawSql()));
            } finally {
                benchmark.tearDown();
            }
        }
    }

    @Test
    void jdbcResultSetMappingVariantsReturnIdenticalRows() throws Exception {
        JdbcMappingPhaseBenchmark benchmark = new JdbcMappingPhaseBenchmark();
        JdbcMappingPhaseBenchmark.MappingState state = new JdbcMappingPhaseBenchmark.MappingState();
        state.setupTrial();
        QueryBenchmark query = new QueryBenchmark();
        query.rowCount = 100;
        query.setup(db);

        List<BenchmarkRecord> expected = runMapping(state, query, benchmark::jdbcGetterMapping);
        assertEquals(expected, runMapping(state, query, benchmark::jdbcNullSafeMapping));
        assertEquals(expected, runMapping(state, query, benchmark::jdbcUtilsMapping));
        assertEquals(expected, runMapping(state, query, benchmark::jdbcGetObjectMapping));
        assertEquals(expected, runMapping(state, query, benchmark::jdbcGetObjectWithIdentityConversionMapping));
        assertEquals(expected, runMapping(state, query, benchmark::jpaDslMapping));
        assertEquals(expected, runMapping(state, query, benchmark::fusedRowMapperMapping));
        assertEquals(expected, runMapping(state, query, benchmark::composedMethodHandleMapping));
        assertEquals(expected, runMapping(state, query, benchmark::lambdaMetafactoryMapping));
        assertEquals(expected, runMapping(state, query, benchmark::columnMapperDispatchMapping));
        assertEquals(expected, runMapping(state, query, benchmark::columnMapperDispatchWithCatchMapping));
        assertEquals(expected, runMapping(state, query, benchmark::springHandwrittenRowMapper));
        assertEquals(expected, runMapping(state, query, benchmark::springHandwrittenSetterRowMapper));
        assertEquals(expected, runMapping(state, query, benchmark::springBeanPropertyRowMapper));
    }

    @Test
    void typedJdbcAndConverterMappingsPreserveDecimalAndTemporalValues() throws Exception {
        JdbcMappingPhaseBenchmark benchmark = new JdbcMappingPhaseBenchmark();
        JdbcMappingPhaseBenchmark.TypedValueState state = new JdbcMappingPhaseBenchmark.TypedValueState();
        QueryBenchmark query = new QueryBenchmark();
        query.rowCount = 100;
        state.setupTrial(db, query);
        try {
            JdbcMappingPhaseBenchmark.TypedValueRow[] expected =
                    runTypedMapping(state, db, benchmark::typedValueGetters);
            assertEquals(100, expected.length);
            assertEquals(new java.math.BigDecimal("123456789.12345678"), expected[0].amount());
            assertEquals(java.time.LocalDateTime.of(2024, 3, 15, 10, 30, 46), expected[0].createdAt());
            assertEquals(java.time.LocalDate.of(2024, 3, 16), expected[0].businessDate());
            assertNull(expected[9].amount());
            assertNull(expected[9].createdAt());
            assertNull(expected[9].businessDate());
            assertArrayEquals(expected, runTypedMapping(state, db, benchmark::jdbcObjectWithConverters));
            assertArrayEquals(expected, runTypedMapping(state, db, benchmark::jdbcLegacyTemporalGetters));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::dateTypedGetter),
                    runTypedMapping(state, db, benchmark::dateObjectWithConverter));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::dateTypedGetter),
                    runTypedMapping(state, db, benchmark::dateJdbcUtilsWithConverter));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::timestampTypedGetter),
                    runTypedMapping(state, db, benchmark::timestampObjectWithConverter));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::timestampTypedGetter),
                    runTypedMapping(state, db, benchmark::timestampJdbcUtilsWithConverter));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::decimalTypedGetter),
                    runTypedMapping(state, db, benchmark::decimalObjectGetter));
            assertArrayEquals(
                    runTypedMapping(state, db, benchmark::decimalTypedGetter),
                    runTypedMapping(state, db, benchmark::decimalJdbcUtilsGetter));
        } finally {
            state.tearDownTrial(db);
        }
    }

    private <T> T[] runTypedMapping(
            JdbcMappingPhaseBenchmark.TypedValueState state,
            BenchmarkDatabase database,
            TypedMappingFunction<T> mappingFunction)
            throws Exception {
        state.setupInvocation(database);
        try {
            return mappingFunction.map(state);
        } finally {
            state.tearDownInvocation();
        }
    }

    @FunctionalInterface
    private interface TypedMappingFunction<T> {
        T[] map(JdbcMappingPhaseBenchmark.TypedValueState state) throws Exception;
    }

    @Test
    void composedMethodHandleMappingPreservesSqlNulls() throws Exception {
        db.jdbc.update("UPDATE " + db.rowsTable + " SET name = NULL, amount = NULL WHERE id = 1");
        try {
            JdbcMappingPhaseBenchmark benchmark = new JdbcMappingPhaseBenchmark();
            JdbcMappingPhaseBenchmark.MappingState state = new JdbcMappingPhaseBenchmark.MappingState();
            state.setupTrial();
            QueryBenchmark query = new QueryBenchmark();
            query.rowCount = 1;
            query.setup(db);

            List<BenchmarkRecord> expected = runMapping(state, query, benchmark::jdbcNullSafeMapping);
            assertEquals(List.of(new BenchmarkRecord(1L, null, null)), expected);
            assertEquals(expected, runMapping(state, query, benchmark::composedMethodHandleMapping));
            assertEquals(expected, runMapping(state, query, benchmark::jdbcUtilsMapping));
            assertEquals(expected, runMapping(state, query, benchmark::jdbcGetObjectMapping));
            assertEquals(expected, runMapping(state, query, benchmark::jdbcGetObjectWithIdentityConversionMapping));
            assertEquals(expected, runMapping(state, query, benchmark::jpaDslMapping));
            assertEquals(expected, runMapping(state, query, benchmark::jdbcGetterMapping));
            assertEquals(expected, runMapping(state, query, benchmark::springHandwrittenRowMapper));
            assertEquals(expected, runMapping(state, query, benchmark::springHandwrittenSetterRowMapper));
        } finally {
            db.jdbc.update("UPDATE " + db.rowsTable + " SET name = ?, amount = ? WHERE id = 1", "name-1", 1);
        }
    }

    private List<BenchmarkRecord> runMapping(
            JdbcMappingPhaseBenchmark.MappingState state, QueryBenchmark query, MappingFunction mappingFunction)
            throws Exception {
        state.setupInvocation(db, query);
        try {
            return records(List.of(mappingFunction.map(state)));
        } finally {
            state.tearDownInvocation();
        }
    }

    @FunctionalInterface
    private interface MappingFunction {
        BenchmarkRow[] map(JdbcMappingPhaseBenchmark.MappingState state) throws Exception;
    }

    @Test
    void mappingVariantsReturnIdenticalRows() throws Exception {
        MappingBenchmark benchmark = new MappingBenchmark();
        benchmark.rowCount = 100;
        benchmark.setup(db);
        List<BenchmarkRecord> expected = benchmark.directRecord();
        assertEquals(100, expected.size());
        assertEquals(records(benchmark.directBean()), expected);
        assertEquals(expected, records(benchmark.springBeanProperty()));
        assertEquals(expected, records(benchmark.cachedJavaBeansSetter()));
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
        assertEquals(201, benchmark.mybatisBatch(db).length);
        assertEquals(expected, writes());
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
        assertEquals(201, benchmark.jdbcBatchTransactional(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.mybatisBatchTransactional(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.springBatchTransactional(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.dslBatchTransactional(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(201, benchmark.dslChunkedBatchTransactional(db).length);
        assertEquals(expected, writes());
        benchmark.clearWrites(db);
        assertEquals(
                1,
                db.jdbc.queryForObject("SELECT COUNT(*) FROM " + db.writesTable + " WHERE amount = -1", Integer.class));
    }

    @Test
    void batchPreparationRetainsSqlAndEveryParameter() {
        BatchPreparationBenchmark benchmark = new BatchPreparationBenchmark();
        for (int size : List.of(1, 100, 1000)) {
            benchmark.batchSize = size;
            benchmark.setup();
            assertEquals(size, benchmark.prepareBatch().length);
            String dslSql = benchmark.jdbc.sql;
            var dslParams = benchmark.jdbc.parameters;
            assertEquals(size, benchmark.prepareSpringBatch().length);
            assertEquals(dslSql, benchmark.jdbc.sql);
            assertEquals(size, dslParams.length);
            assertEquals(size, benchmark.jdbc.parameters.length);
            for (int i = 0; i < size; i++) {
                var params = dslParams[i];
                var springParams = benchmark.jdbc.parameters[i];
                assertArrayEquals(params.getParameterNames(), springParams.getParameterNames());
                assertEquals((long) i, params.getValue("id"));
                assertEquals("name-" + i, params.getValue("name"));
                assertEquals(i, params.getValue("amount"));
                for (String column : List.of("id", "name", "amount")) {
                    assertEquals(params.getValue(column), springParams.getValue(column));
                }
            }
        }
    }

    @Test
    void jooqAndJpaDslRenderingKeepValuesAsBindParameters() {
        SqlRenderingBenchmark benchmark = new SqlRenderingBenchmark();
        benchmark.setup();
        assertTrue(benchmark.renderPrebuiltSpec().getSql().contains(":"));
        assertFalse(benchmark.renderPrebuiltSpec().getSql().contains("42"));
        assertTrue(benchmark.renderJooqPrebuiltQuery().contains("?"));
        assertFalse(benchmark.renderJooqPrebuiltQuery().contains("42"));
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
                "SELECT id, name, amount FROM " + db.writesTable + " WHERE amount >= 0 ORDER BY id",
                (rs, rowNum) -> QueryBenchmark.readRecord(rs));
    }

    private static List<BenchmarkRecord> jooqRecords(List<JooqBenchmarkRow> rows) {
        return rows.stream()
                .map(row -> new BenchmarkRecord(row.getId(), row.getName(), row.getAmount()))
                .toList();
    }

    private static List<BenchmarkRecord> toRecords(List<BenchmarkRow> rows) {
        return rows.stream()
                .map(row -> new BenchmarkRecord(row.getId(), row.getName(), row.getAmount()))
                .toList();
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
