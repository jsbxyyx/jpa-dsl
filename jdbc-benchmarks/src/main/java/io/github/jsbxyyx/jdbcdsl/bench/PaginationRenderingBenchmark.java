package io.github.jsbxyyx.jdbcdsl.bench;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class PaginationRenderingBenchmark {
    @Param({"normal", "quoted", "collision"})
    public String scenario;

    private String sql;
    private final H2Dialect dialect = new H2Dialect();

    @Setup(Level.Trial)
    public void setup() {
        String expression =
                switch (scenario) {
                    case "normal" -> "id > :p1";
                    case "quoted" -> "id > :p1 AND name <> ':_limit :_offset'";
                    case "collision" -> "id > :p1 AND id <> :_limit AND id <> :_offset";
                    default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
                };
        sql = "SELECT id, name, amount FROM customer WHERE " + expression
                + " AND status = 'active' AND region = 'east' ORDER BY id";
    }

    @Benchmark
    public String renderPagination() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("p1", 1L);
        return dialect.applyPagination(sql, 10, 25, parameters);
    }
}
