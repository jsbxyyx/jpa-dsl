package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.JPageable;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class QueryBenchmark {
    static final String BEAN_SQL =
            "SELECT id, name, amount FROM " + BenchmarkDatabase.ROWS + " WHERE id <= ? ORDER BY id";
    static final String SCALAR_SQL = "SELECT id FROM " + BenchmarkDatabase.ROWS + " WHERE id <= ? ORDER BY id";
    static final String NAMED_BEAN_SQL =
            "SELECT id, name, amount FROM " + BenchmarkDatabase.ROWS + " WHERE id <= :max ORDER BY id";
    static final String NAMED_SCALAR_SQL = "SELECT id FROM " + BenchmarkDatabase.ROWS + " WHERE id <= :max ORDER BY id";

    @Param({"1", "100", "10000"})
    public int rowCount;

    private SelectSpec<BenchmarkRow, BenchmarkRow> spec;
    private SelectSpec<BenchmarkRow, BenchmarkRow> pageSpec;
    private JPageable<BenchmarkRow> pageable;
    private Map<String, Object> params;
    private static final RowMapper<BenchmarkRow> BEAN_MAPPER = (rs, rowNum) -> readBean(rs);
    private static final RowMapper<BenchmarkRecord> RECORD_MAPPER = (rs, rowNum) -> readRecord(rs);
    private static final RowMapper<Long> SCALAR_MAPPER = (rs, rowNum) -> rs.getLong(1);

    @Setup(Level.Trial)
    public void setup() {
        if (rowCount < 1 || rowCount > BenchmarkDatabase.DATASET_SIZE) {
            throw new IllegalArgumentException("rowCount must be between 1 and " + BenchmarkDatabase.DATASET_SIZE);
        }
        params = Map.of("max", rowCount);
        spec = buildSpec();
        pageSpec = buildSpec(Math.min(rowCount + 1, BenchmarkDatabase.DATASET_SIZE));
        pageable = JPageable.ofSize(rowCount);
    }

    private SelectSpec<BenchmarkRow, BenchmarkRow> buildSpec() {
        return buildSpec(rowCount);
    }

    private SelectSpec<BenchmarkRow, BenchmarkRow> buildSpec(int max) {
        return SelectBuilder.from(BenchmarkRow.class)
                .where(w -> w.lte(BenchmarkRow::getId, (long) max))
                .orderBy(JSort.by(JOrder.asc(BenchmarkRow::getId)))
                .mapToEntity();
    }

    @Benchmark
    public List<BenchmarkRow> jdbcBean(BenchmarkDatabase db) throws SQLException {
        return jdbc(db, BEAN_SQL, QueryBenchmark::readBean);
    }

    @Benchmark
    public List<BenchmarkRecord> jdbcRecord(BenchmarkDatabase db) throws SQLException {
        return jdbc(db, BEAN_SQL, QueryBenchmark::readRecord);
    }

    @Benchmark
    public List<Long> jdbcScalar(BenchmarkDatabase db) throws SQLException {
        return jdbc(db, SCALAR_SQL, rs -> rs.getLong(1));
    }

    @Benchmark
    public List<BenchmarkRow> springBean(BenchmarkDatabase db) {
        return db.jdbc.query(BEAN_SQL, BEAN_MAPPER, rowCount);
    }

    @Benchmark
    public List<BenchmarkRecord> springRecord(BenchmarkDatabase db) {
        return db.jdbc.query(BEAN_SQL, RECORD_MAPPER, rowCount);
    }

    @Benchmark
    public List<Long> springScalar(BenchmarkDatabase db) {
        return db.jdbc.query(SCALAR_SQL, SCALAR_MAPPER, rowCount);
    }

    @Benchmark
    public List<BenchmarkRow> dslRawBean(BenchmarkDatabase db) {
        return db.dsl.query(NAMED_BEAN_SQL, params, BenchmarkRow.class);
    }

    @Benchmark
    public List<BenchmarkRecord> dslRawRecord(BenchmarkDatabase db) {
        return db.dsl.query(NAMED_BEAN_SQL, params, BenchmarkRecord.class);
    }

    @Benchmark
    public List<Long> dslRawScalar(BenchmarkDatabase db) {
        return db.dsl.query(NAMED_SCALAR_SQL, params, Long.class);
    }

    @Benchmark
    public List<BenchmarkRow> dslSelectBean(BenchmarkDatabase db) {
        return db.dsl.select(spec);
    }

    @Benchmark
    public Page<BenchmarkRow> dslPageBean(BenchmarkDatabase db) {
        return db.dsl.selectPage(pageSpec, pageable);
    }

    @Benchmark
    public Slice<BenchmarkRow> dslSliceBean(BenchmarkDatabase db) {
        return db.dsl.selectSlice(pageSpec, pageable);
    }

    @Benchmark
    public void dslSelectListConsumeBean(BenchmarkDatabase db, Blackhole blackhole) {
        for (BenchmarkRow row : db.dsl.select(spec)) {
            blackhole.consume(row);
        }
    }

    @Benchmark
    public void dslSelectEachBean(BenchmarkDatabase db, Blackhole blackhole) {
        db.dsl.selectEach(spec, blackhole::consume);
    }

    @Benchmark
    public List<BenchmarkRow> dslBuildAndSelectBean(BenchmarkDatabase db) {
        return db.dsl.select(buildSpec());
    }

    private <T> List<T> jdbc(BenchmarkDatabase db, String sql, JdbcRowReader<T> reader) throws SQLException {
        try (Connection connection = db.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rowCount);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(reader.read(rs));
                }
                return rows;
            }
        }
    }

    static BenchmarkRow readBean(ResultSet rs) throws SQLException {
        return new BenchmarkRow(rs.getLong(1), rs.getString(2), rs.getInt(3));
    }

    static BenchmarkRecord readRecord(ResultSet rs) throws SQLException {
        return new BenchmarkRecord(rs.getLong(1), rs.getString(2), rs.getInt(3));
    }

    @FunctionalInterface
    private interface JdbcRowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }
}
