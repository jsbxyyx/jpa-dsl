package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMetaFactory;
import io.github.jsbxyyx.jdbcdsl.bean.DefaultConverterRegistry;
import io.github.jsbxyyx.jdbcdsl.bean.MapperCache;
import io.github.jsbxyyx.jdbcdsl.bean.ResultSetMapper;
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
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Compares row mapping strategies against the exact same MySQL ResultSet shape and fetch settings.
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

    @State(Scope.Thread)
    public static class MappingState {
        private Connection connection;
        private PreparedStatement statement;
        private ResultSet resultSet;
        private int expectedRows;
        private ResultSetMapper dslMapper;
        private BeanMappingMeta beanMeta;
        private MapperCache mapperCache;

        @Setup(Level.Trial)
        public void setupTrial() {
            beanMeta = new BeanMappingMetaFactory().create(BenchmarkRow.class);
            mapperCache = new MapperCache(
                    new JdbcDslCacheManager(),
                    new DefaultConverterRegistry(DefaultConversionService.getSharedInstance()));
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
    public long jdbcGetterMapping(MappingState state) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = QueryBenchmark.readBean(state.resultSet);
            checksum += checksum(row);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return checksum;
    }

    @Benchmark
    public long jpaDslMapping(MappingState state) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = (BenchmarkRow) state.dslMapper.mapRow(state.resultSet);
            checksum += checksum(row);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return checksum;
    }

    @Benchmark
    public long springHandwrittenRowMapper(MappingState state) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = HANDWRITTEN_SPRING.mapRow(state.resultSet, rows);
            checksum += checksum(row);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return checksum;
    }

    @Benchmark
    public long springBeanPropertyRowMapper(MappingState state) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = BEAN_PROPERTY_SPRING.mapRow(state.resultSet, rows);
            checksum += checksum(row);
            rows++;
        }
        verifyRows(rows, state.expectedRows);
        return checksum;
    }

    private static long checksum(BenchmarkRow row) {
        return row.getId() + row.getAmount() + row.getName().length();
    }

    private static void verifyRows(int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException("Expected " + expected + " rows but read " + actual);
        }
    }
}
