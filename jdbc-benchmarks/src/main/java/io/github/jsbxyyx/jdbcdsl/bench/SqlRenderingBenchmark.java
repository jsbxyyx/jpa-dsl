package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.SqlRenderer;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Separates query-spec construction from SQL rendering cost. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
@State(Scope.Thread)
public class SqlRenderingBenchmark {

    private SelectSpec<BenchmarkRow, BenchmarkRow> spec;

    @Setup(Level.Trial)
    public void setup() {
        spec = buildSpec();
    }

    @Benchmark
    public RenderedSql renderPrebuiltSpec() {
        return SqlRenderer.renderSelect(spec);
    }

    @Benchmark
    public SelectSpec<BenchmarkRow, BenchmarkRow> buildSpecOnly() {
        return buildSpec();
    }

    @Benchmark
    public RenderedSql buildAndRender() {
        return SqlRenderer.renderSelect(buildSpec());
    }

    private SelectSpec<BenchmarkRow, BenchmarkRow> buildSpec() {
        return SelectBuilder.from(BenchmarkRow.class)
                .where(w -> w.eq(BenchmarkRow::getId, 42L))
                .orderBy(JSort.by(JOrder.asc(BenchmarkRow::getId)))
                .mapToEntity();
    }
}
