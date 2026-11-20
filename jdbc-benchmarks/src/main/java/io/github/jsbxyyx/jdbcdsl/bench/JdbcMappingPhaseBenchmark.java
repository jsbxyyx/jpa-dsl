package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMetaFactory;
import io.github.jsbxyyx.jdbcdsl.bean.DefaultConverterRegistry;
import io.github.jsbxyyx.jdbcdsl.bean.MapperCache;
import io.github.jsbxyyx.jdbcdsl.bean.ResultSetMapper;
import io.github.jsbxyyx.jdbcdsl.bean.ValueConverter;
import io.github.jsbxyyx.jdbcdsl.bean.ValueConverters;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Compares row mapping strategies against the exact same JDBC ResultSet shape and fetch settings.
 * Connection acquisition, statement preparation, executeQuery, and mapper initialization are excluded.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class JdbcMappingPhaseBenchmark {
    private static final int FETCH_SIZE = 500;
    private static final RowMapper<BenchmarkRow> HANDWRITTEN_SPRING = (rs, rowNum) -> QueryBenchmark.readBean(rs);
    private static final BeanPropertyRowMapper<BenchmarkRow> BEAN_PROPERTY_SPRING =
            new BeanPropertyRowMapper<>(BenchmarkRow.class);
    private static final ValueConverter IDENTITY_CONVERTER = ValueConverters.identity();
    private static final ValueConverter TIMESTAMP_CONVERTER = ValueConverters.timestampToLocalDateTime();
    private static final ValueConverter DATE_CONVERTER = ValueConverters.sqlDateToLocalDate();

    @State(Scope.Thread)
    public static class TypedValueState {
        private String tableName;
        private Connection connection;
        private PreparedStatement statement;
        private ResultSet resultSet;
        private int expectedRows;

        @Setup(Level.Trial)
        public void setupTrial(BenchmarkDatabase db, QueryBenchmark query) throws SQLException {
            expectedRows = query.rowCount;
            tableName = "typed_values_" + UUID.randomUUID().toString().replace("-", "");
            db.jdbc.execute("CREATE TABLE " + tableName
                    + " (id BIGINT PRIMARY KEY, amount DECIMAL(24, 8), created_at TIMESTAMP, business_date DATE)");
            try (Connection insertConnection = db.dataSource.getConnection();
                    PreparedStatement insert = insertConnection.prepareStatement("INSERT INTO " + tableName
                            + " (id, amount, created_at, business_date) VALUES (?, ?, ?, ?)")) {
                insertConnection.setAutoCommit(false);
                for (int i = 1; i <= expectedRows; i++) {
                    insert.setLong(1, i);
                    insert.setBigDecimal(2, i % 10 == 0 ? null : new BigDecimal("123456789.12345678"));
                    insert.setTimestamp(
                            3,
                            i % 10 == 0
                                    ? null
                                    : Timestamp.valueOf(LocalDateTime.of(2024, 3, 15, 10, 30, 45)
                                            .plusSeconds(i)));
                    insert.setDate(
                            4,
                            i % 10 == 0
                                    ? null
                                    : Date.valueOf(LocalDate.of(2024, 3, 15).plusDays(i % 365)));
                    insert.addBatch();
                }
                insert.executeBatch();
                insertConnection.commit();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation(BenchmarkDatabase db) throws SQLException {
            connection = db.dataSource.getConnection();
            statement = connection.prepareStatement(
                    "SELECT amount, created_at, business_date FROM " + tableName + " ORDER BY id");
            statement.setFetchSize(FETCH_SIZE);
            resultSet = statement.executeQuery();
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() throws SQLException {
            try {
                if (resultSet != null) {
                    resultSet.close();
                    resultSet = null;
                }
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                        statement = null;
                    }
                } finally {
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTrial(BenchmarkDatabase db) {
            db.jdbc.execute("DROP TABLE " + tableName);
        }
    }

    record TypedValueRow(BigDecimal amount, LocalDateTime createdAt, LocalDate businessDate) {}

    @State(Scope.Thread)
    public static class MappingState {
        private Connection connection;
        private PreparedStatement statement;
        private ResultSet resultSet;
        private int expectedRows;
        private ResultSetMapper dslMapper;
        private BeanMappingMeta beanMeta;
        private MapperCache mapperCache;
        private BiConsumer<Object, Object> lambdaId;
        private BiConsumer<Object, Object> lambdaName;
        private BiConsumer<Object, Object> lambdaAmount;
        private ColumnReader[] columnReaders;
        private FusedRowMapper fusedRowMapper;
        private MethodHandle composedRowMapping;

        @Setup(Level.Trial)
        public void setupTrial() {
            beanMeta = new BeanMappingMetaFactory().create(BenchmarkRow.class);
            mapperCache = new MapperCache(
                    new JdbcDslCacheManager(),
                    new DefaultConverterRegistry(DefaultConversionService.getSharedInstance()));
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                lambdaId = createLambdaWriter(lookup, "setId", Long.class);
                lambdaName = createLambdaWriter(lookup, "setName", String.class);
                lambdaAmount = createLambdaWriter(lookup, "setAmount", Integer.class);
                BiConsumer<Object, Object> idWriter = lambdaId;
                BiConsumer<Object, Object> nameWriter = lambdaName;
                BiConsumer<Object, Object> amountWriter = lambdaAmount;
                columnReaders = new ColumnReader[] {
                    (rs, row) -> {
                        long value = rs.getLong(1);
                        idWriter.accept(row, rs.wasNull() ? null : value);
                    },
                    (rs, row) -> nameWriter.accept(row, rs.getString(2)),
                    (rs, row) -> {
                        int value = rs.getInt(3);
                        amountWriter.accept(row, rs.wasNull() ? null : value);
                    }
                };
                fusedRowMapper = rs -> {
                    BenchmarkRow row = new BenchmarkRow();
                    long idValue = rs.getLong(1);
                    idWriter.accept(row, rs.wasNull() ? null : idValue);
                    nameWriter.accept(row, rs.getString(2));
                    int amountValue = rs.getInt(3);
                    amountWriter.accept(row, rs.wasNull() ? null : amountValue);
                    return row;
                };
                MethodType actionType = MethodType.methodType(void.class, ResultSet.class, BenchmarkRow.class);
                MethodHandle mapId = lookup.findStatic(JdbcMappingPhaseBenchmark.class, "mapId", actionType);
                MethodHandle mapName = lookup.findStatic(JdbcMappingPhaseBenchmark.class, "mapName", actionType);
                MethodHandle mapAmount = lookup.findStatic(JdbcMappingPhaseBenchmark.class, "mapAmount", actionType);
                composedRowMapping =
                        MethodHandles.foldArguments(MethodHandles.foldArguments(mapAmount, mapName), mapId);
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to initialize benchmark lambda writers", e);
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation(BenchmarkDatabase db, QueryBenchmark query) throws SQLException {
            expectedRows = query.rowCount;
            connection = db.dataSource.getConnection();
            statement = connection.prepareStatement(QueryBenchmark.beanSql(db));
            statement.setFetchSize(FETCH_SIZE);
            statement.setInt(1, expectedRows);
            resultSet = statement.executeQuery();
            dslMapper = mapperCache.getMapper(resultSet, beanMeta);
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() throws SQLException {
            try {
                if (resultSet != null) {
                    resultSet.close();
                    resultSet = null;
                }
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                        statement = null;
                    }
                } finally {
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                }
            }
        }
    }

    @Benchmark
    public void directInstantiationOnly(MappingState state, Blackhole blackhole) {
        for (int i = 0; i < state.expectedRows; i++) {
            blackhole.consume(new BenchmarkRow());
        }
    }

    @Benchmark
    public void jpaDslInstantiationOnly(MappingState state, Blackhole blackhole) {
        for (int i = 0; i < state.expectedRows; i++) {
            blackhole.consume(state.beanMeta.newInstance());
        }
    }

    @Benchmark
    public TypedValueRow[] typedValueGetters(TypedValueState state) throws SQLException {
        TypedValueRow[] rows = new TypedValueRow[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            rows[index++] = new TypedValueRow(
                    state.resultSet.getBigDecimal(1),
                    state.resultSet.getObject(2, LocalDateTime.class),
                    state.resultSet.getObject(3, LocalDate.class));
        }
        verifyRows(index, state.expectedRows);
        return rows;
    }

    @Benchmark
    public TypedValueRow[] jdbcObjectWithConverters(TypedValueState state) throws SQLException {
        TypedValueRow[] rows = new TypedValueRow[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            BigDecimal amount = (BigDecimal) IDENTITY_CONVERTER.convert(state.resultSet.getObject(1));
            LocalDateTime createdAt = (LocalDateTime) TIMESTAMP_CONVERTER.convert(state.resultSet.getObject(2));
            LocalDate businessDate = (LocalDate) DATE_CONVERTER.convert(state.resultSet.getObject(3));
            rows[index++] = new TypedValueRow(amount, createdAt, businessDate);
        }
        verifyRows(index, state.expectedRows);
        return rows;
    }

    @Benchmark
    public TypedValueRow[] jdbcLegacyTemporalGetters(TypedValueState state) throws SQLException {
        TypedValueRow[] rows = new TypedValueRow[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            Timestamp timestamp = state.resultSet.getTimestamp(2);
            Date date = state.resultSet.getDate(3);
            rows[index++] = new TypedValueRow(
                    state.resultSet.getBigDecimal(1),
                    timestamp == null ? null : timestamp.toLocalDateTime(),
                    date == null ? null : date.toLocalDate());
        }
        verifyRows(index, state.expectedRows);
        return rows;
    }

    @Benchmark
    public LocalDate[] dateTypedGetter(TypedValueState state) throws SQLException {
        LocalDate[] values = new LocalDate[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = state.resultSet.getObject(3, LocalDate.class);
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public LocalDate[] dateObjectWithConverter(TypedValueState state) throws SQLException {
        LocalDate[] values = new LocalDate[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = (LocalDate) DATE_CONVERTER.convert(state.resultSet.getObject(3));
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public LocalDate[] dateJdbcUtilsWithConverter(TypedValueState state) throws SQLException {
        LocalDate[] values = new LocalDate[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = (LocalDate) DATE_CONVERTER.convert(JdbcUtils.getResultSetValue(state.resultSet, 3));
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public LocalDateTime[] timestampTypedGetter(TypedValueState state) throws SQLException {
        LocalDateTime[] values = new LocalDateTime[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = state.resultSet.getObject(2, LocalDateTime.class);
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public LocalDateTime[] timestampObjectWithConverter(TypedValueState state) throws SQLException {
        LocalDateTime[] values = new LocalDateTime[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = (LocalDateTime) TIMESTAMP_CONVERTER.convert(state.resultSet.getObject(2));
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public LocalDateTime[] timestampJdbcUtilsWithConverter(TypedValueState state) throws SQLException {
        LocalDateTime[] values = new LocalDateTime[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] =
                    (LocalDateTime) TIMESTAMP_CONVERTER.convert(JdbcUtils.getResultSetValue(state.resultSet, 2));
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public BigDecimal[] decimalTypedGetter(TypedValueState state) throws SQLException {
        BigDecimal[] values = new BigDecimal[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = state.resultSet.getBigDecimal(1);
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public BigDecimal[] decimalObjectGetter(TypedValueState state) throws SQLException {
        BigDecimal[] values = new BigDecimal[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = (BigDecimal) state.resultSet.getObject(1);
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public BigDecimal[] decimalJdbcUtilsGetter(TypedValueState state) throws SQLException {
        BigDecimal[] values = new BigDecimal[state.expectedRows];
        int index = 0;
        while (state.resultSet.next()) {
            values[index++] = (BigDecimal) JdbcUtils.getResultSetValue(state.resultSet, 1);
        }
        verifyRows(index, state.expectedRows);
        return values;
    }

    @Benchmark
    public BenchmarkRow[] jdbcGetterMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            mappedRows[rows++] = QueryBenchmark.readBean(state.resultSet);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] jpaDslMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            mappedRows[rows++] = (BenchmarkRow) state.dslMapper.mapRow(state.resultSet);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] jdbcUtilsMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            Long id = (Long) JdbcUtils.getResultSetValue(state.resultSet, 1);
            String name = (String) JdbcUtils.getResultSetValue(state.resultSet, 2);
            Integer amount = (Integer) JdbcUtils.getResultSetValue(state.resultSet, 3);
            mappedRows[rows++] = new BenchmarkRow(id, name, amount);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] jdbcGetObjectMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            Long id = (Long) state.resultSet.getObject(1);
            String name = (String) state.resultSet.getObject(2);
            Integer amount = (Integer) state.resultSet.getObject(3);
            mappedRows[rows++] = new BenchmarkRow(id, name, amount);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] jdbcGetObjectWithIdentityConversionMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            Object id = IDENTITY_CONVERTER.convert(state.resultSet.getObject(1));
            Object name = IDENTITY_CONVERTER.convert(state.resultSet.getObject(2));
            Object amount = IDENTITY_CONVERTER.convert(state.resultSet.getObject(3));
            mappedRows[rows++] = new BenchmarkRow((Long) id, (String) name, (Integer) amount);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] jdbcNullSafeMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            long idValue = state.resultSet.getLong(1);
            Long id = state.resultSet.wasNull() ? null : idValue;
            String name = state.resultSet.getString(2);
            int amountValue = state.resultSet.getInt(3);
            Integer amount = state.resultSet.wasNull() ? null : amountValue;
            mappedRows[rows++] = new BenchmarkRow(id, name, amount);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] fusedRowMapperMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            mappedRows[rows++] = state.fusedRowMapper.map(state.resultSet);
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @FunctionalInterface
    private interface FusedRowMapper {
        BenchmarkRow map(ResultSet resultSet) throws SQLException;
    }

    @Benchmark
    public BenchmarkRow[] composedMethodHandleMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow();
            try {
                state.composedRowMapping.invokeExact(state.resultSet, row);
            } catch (SQLException e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Composed benchmark mapper failed", e);
            }
            mappedRows[rows++] = row;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    private static void mapId(ResultSet resultSet, BenchmarkRow row) throws SQLException {
        long value = resultSet.getLong(1);
        row.setId(resultSet.wasNull() ? null : value);
    }

    private static void mapName(ResultSet resultSet, BenchmarkRow row) throws SQLException {
        row.setName(resultSet.getString(2));
    }

    private static void mapAmount(ResultSet resultSet, BenchmarkRow row) throws SQLException {
        int value = resultSet.getInt(3);
        row.setAmount(resultSet.wasNull() ? null : value);
    }

    @Benchmark
    public BenchmarkRow[] columnMapperDispatchMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow();
            for (ColumnReader columnReader : state.columnReaders) {
                columnReader.map(state.resultSet, row);
            }
            mappedRows[rows++] = row;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] columnMapperDispatchWithCatchMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow();
            for (int i = 0; i < state.columnReaders.length; i++) {
                try {
                    state.columnReaders[i].map(state.resultSet, row);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to map benchmark column " + (i + 1), e);
                }
            }
            mappedRows[rows++] = row;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @FunctionalInterface
    private interface ColumnReader {
        void map(ResultSet resultSet, Object target) throws SQLException;
    }

    @Benchmark
    public BenchmarkRow[] lambdaMetafactoryMapping(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow();
            long idValue = state.resultSet.getLong(1);
            state.lambdaId.accept(row, state.resultSet.wasNull() ? null : idValue);
            state.lambdaName.accept(row, state.resultSet.getString(2));
            int amountValue = state.resultSet.getInt(3);
            state.lambdaAmount.accept(row, state.resultSet.wasNull() ? null : amountValue);
            mappedRows[rows++] = row;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    private static BiConsumer<Object, Object> createLambdaWriter(
            MethodHandles.Lookup lookup, String methodName, Class<?> valueType) throws Throwable {
        MethodHandle setter =
                lookup.findVirtual(BenchmarkRow.class, methodName, MethodType.methodType(void.class, valueType));
        return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                        lookup,
                        "accept",
                        MethodType.methodType(BiConsumer.class),
                        MethodType.methodType(void.class, Object.class, Object.class),
                        setter,
                        MethodType.methodType(void.class, BenchmarkRow.class, valueType))
                .getTarget()
                .invokeExact();
    }

    @Benchmark
    public BenchmarkRow[] springHandwrittenRowMapper(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            mappedRows[rows] = HANDWRITTEN_SPRING.mapRow(state.resultSet, rows);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    @Benchmark
    public BenchmarkRow[] springBeanPropertyRowMapper(MappingState state) throws SQLException {
        BenchmarkRow[] mappedRows = new BenchmarkRow[state.expectedRows];
        int rows = 0;
        while (state.resultSet.next()) {
            mappedRows[rows] = BEAN_PROPERTY_SPRING.mapRow(state.resultSet, rows);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return mappedRows;
    }

    private static void verifyRows(int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException("Expected " + expected + " rows but read " + actual);
        }
    }
}
