package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.BatchInsertBuilder;
import io.github.jsbxyyx.jdbcdsl.BatchInsertSpec;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
public class BatchBenchmark {
    @Param({"1", "100", "1000"})
    public int batchSize;

    private long rangeStart;
    private List<BenchmarkWrite> rows;
    private BatchInsertSpec<BenchmarkWrite> spec;

    @Setup(Level.Trial)
    public void setup(BenchmarkDatabase db) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        rangeStart = db.reserveRange(batchSize);
        // A permanent boundary row separates adjacent ranges for InnoDB next-key locks.
        db.jdbc.update(BenchmarkDatabase.INSERT_WRITE, rangeStart + batchSize, "range-guard", -1);
        rows = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            rows.add(new BenchmarkWrite(rangeStart + i, "name-" + i, i));
        }
        spec = BatchInsertBuilder.of(BenchmarkWrite.class, rows).build();
    }

    @Setup(Level.Invocation)
    public void clearWrites(BenchmarkDatabase db) {
        db.jdbc.update(
                "DELETE FROM " + BenchmarkDatabase.WRITES + " WHERE id >= ? AND id < ?",
                rangeStart,
                rangeStart + batchSize);
    }

    @Benchmark
    public int[] jdbcBatch(BenchmarkDatabase db) throws SQLException {
        try (Connection connection = db.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(BenchmarkDatabase.INSERT_WRITE)) {
            for (BenchmarkWrite row : rows) {
                bind(statement, row);
                statement.addBatch();
            }
            return statement.executeBatch();
        }
    }

    @Benchmark
    public int[] springBatch(BenchmarkDatabase db) {
        return db.jdbc.batchUpdate(BenchmarkDatabase.INSERT_WRITE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                bind(statement, rows.get(index));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Benchmark
    public int[] dslBatch(BenchmarkDatabase db) {
        return db.dsl.executeBatchInsert(spec);
    }

    @Benchmark
    public int[] dslChunkedBatch(BenchmarkDatabase db) {
        return db.dsl.executeBatchInsert(spec, 100);
    }

    private static void bind(PreparedStatement statement, BenchmarkWrite row) throws SQLException {
        statement.setLong(1, row.getId());
        statement.setString(2, row.getName());
        statement.setInt(3, row.getAmount());
    }
}
