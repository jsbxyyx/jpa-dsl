package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMetaFactory;
import io.github.jsbxyyx.jdbcdsl.bean.DefaultConverterRegistry;
import io.github.jsbxyyx.jdbcdsl.bean.MapperCache;
import io.github.jsbxyyx.jdbcdsl.bean.ResultSetMapper;
import io.github.jsbxyyx.jdbcdsl.bean.ScalarRowMapper;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class MappingBenchmark {
    @Param({"1", "100", "10000"})
    public int rowCount;

    private CachedRowSet beans;
    private CachedRowSet scalars;
    private ResultSetMapper beanMapper;
    private ResultSetMapper recordMapper;
    private MapperCache mapperCache;
    private ScalarRowMapper<Long> scalarMapper;
    private BeanMappingMeta beanMeta;
    private DefaultConverterRegistry registry;
    private BeanPropertyRowMapper<BenchmarkRow> springMapper;
    private Method setId;
    private Method setName;
    private Method setAmount;

    @Setup(Level.Trial)
    public void setup(BenchmarkDatabase db) throws SQLException {
        if (rowCount < 1 || rowCount > BenchmarkDatabase.DATASET_SIZE) {
            throw new IllegalArgumentException("rowCount must be between 1 and " + BenchmarkDatabase.DATASET_SIZE);
        }
        beans = rowSet(db, QueryBenchmark.beanSql(db));
        scalars = rowSet(db, QueryBenchmark.scalarSql(db));
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        beanMeta = factory.create(BenchmarkRow.class);
        registry = new DefaultConverterRegistry(DefaultConversionService.getSharedInstance());
        mapperCache = new MapperCache(new JdbcDslCacheManager(), registry);
        beanMapper = mapperCache.getMapper(beans, beanMeta);
        recordMapper = mapperCache.getMapper(beans, factory.create(BenchmarkRecord.class));
        scalarMapper = new ScalarRowMapper<>(Long.class, registry);
        springMapper = new BeanPropertyRowMapper<>(BenchmarkRow.class);
        try {
            setId = BenchmarkRow.class.getMethod("setId", Long.class);
            setName = BenchmarkRow.class.getMethod("setName", String.class);
            setAmount = BenchmarkRow.class.getMethod("setAmount", Integer.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("BenchmarkRow setter metadata is incomplete", e);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (beans != null) {
            beans.close();
        }
        if (scalars != null) {
            scalars.close();
        }
    }

    private CachedRowSet rowSet(BenchmarkDatabase db, String sql) throws SQLException {
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        try (Connection connection = db.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rowCount);
            try (ResultSet rs = statement.executeQuery()) {
                rows.populate(rs);
            }
        }
        return rows;
    }

    @Benchmark
    public List<BenchmarkRow> directBean() throws SQLException {
        beans.beforeFirst();
        List<BenchmarkRow> result = new ArrayList<>(rowCount);
        while (beans.next()) {
            result.add(QueryBenchmark.readBean(beans));
        }
        return result;
    }

    @Benchmark
    public List<BenchmarkRecord> directRecord() throws SQLException {
        beans.beforeFirst();
        List<BenchmarkRecord> result = new ArrayList<>(rowCount);
        while (beans.next()) {
            result.add(QueryBenchmark.readRecord(beans));
        }
        return result;
    }

    @Benchmark
    public List<Long> directScalar() throws SQLException {
        scalars.beforeFirst();
        List<Long> result = new ArrayList<>(rowCount);
        while (scalars.next()) {
            result.add(scalars.getLong(1));
        }
        return result;
    }

    @Benchmark
    public List<Object> dslBean() throws SQLException {
        return mapRows(beanMapper);
    }

    @Benchmark
    public List<BenchmarkRow> springBeanProperty() throws SQLException {
        beans.beforeFirst();
        List<BenchmarkRow> result = new ArrayList<>(rowCount);
        int rowNum = 0;
        while (beans.next()) {
            result.add(springMapper.mapRow(beans, rowNum++));
        }
        return result;
    }

    @Benchmark
    public List<BenchmarkRow> cachedJavaBeansSetter() throws SQLException {
        beans.beforeFirst();
        List<BenchmarkRow> result = new ArrayList<>(rowCount);
        while (beans.next()) {
            BenchmarkRow row = new BenchmarkRow();
            try {
                setId.invoke(row, beans.getLong(1));
                setName.invoke(row, beans.getString(2));
                setAmount.invoke(row, beans.getInt(3));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to populate BenchmarkRow", e);
            }
            result.add(row);
        }
        return result;
    }

    @Benchmark
    public List<Object> dslRecord() throws SQLException {
        return mapRows(recordMapper);
    }

    @Benchmark
    public List<Long> dslScalar() throws SQLException {
        scalars.beforeFirst();
        List<Long> result = new ArrayList<>(rowCount);
        int index = 0;
        while (scalars.next()) {
            result.add(scalarMapper.mapRow(scalars, index++));
        }
        return result;
    }

    /** Measures the framework's mapper-cache hit path, including ResultSet metadata key creation. */
    @Benchmark
    public ResultSetMapper dslMapperCacheLookup() throws SQLException {
        return mapperCache.getMapper(beans, beanMeta);
    }

    @Benchmark
    public List<Object> dslColdBean() throws SQLException {
        MapperCache cold = new MapperCache(new JdbcDslCacheManager(), registry);
        return mapRows(cold.getMapper(beans, beanMeta));
    }

    private List<Object> mapRows(ResultSetMapper mapper) throws SQLException {
        beans.beforeFirst();
        List<Object> result = new ArrayList<>(rowCount);
        while (beans.next()) {
            result.add(mapper.mapRow(beans));
        }
        return result;
    }
}
