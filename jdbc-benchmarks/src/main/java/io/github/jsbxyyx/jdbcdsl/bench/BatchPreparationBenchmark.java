package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.BatchInsertBuilder;
import io.github.jsbxyyx.jdbcdsl.BatchInsertSpec;
import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class BatchPreparationBenchmark {
    @Param({"1", "100", "1000"})
    public int batchSize;

    RecordingTemplate jdbc;
    private JdbcDslExecutor dsl;
    private BatchInsertSpec<BenchmarkWrite> spec;
    private List<BenchmarkWrite> rows;

    @Setup(Level.Trial)
    public void setup() {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        jdbc = new RecordingTemplate();
        dsl = new JdbcDslExecutor(jdbc, new H2Dialect());
        rows = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            rows.add(new BenchmarkWrite((long) i, "name-" + i, i));
        }
        spec = BatchInsertBuilder.of(BenchmarkWrite.class, rows).build();
    }

    @Benchmark
    public int[] prepareBatch() {
        return dsl.executeBatchInsert(spec);
    }

    @Benchmark
    public int[] prepareSpringBatch() {
        SqlParameterSource[] parameters = new SqlParameterSource[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            BenchmarkWrite row = rows.get(i);
            parameters[i] = new MapSqlParameterSource()
                    .addValue("id", row.getId())
                    .addValue("name", row.getName())
                    .addValue("amount", row.getAmount());
        }
        return jdbc.batchUpdate(
                "INSERT INTO jpa_dsl_bench_writes (id, name, amount) VALUES (:id, :name, :amount)", parameters);
    }

    static final class RecordingTemplate extends NamedParameterJdbcTemplate {
        String sql;
        SqlParameterSource[] parameters;

        RecordingTemplate() {
            super(new JdbcTemplate());
        }

        @Override
        public int[] batchUpdate(String sql, SqlParameterSource[] batchArgs) {
            this.sql = sql;
            this.parameters = batchArgs;
            return new int[batchArgs.length];
        }
    }
}
