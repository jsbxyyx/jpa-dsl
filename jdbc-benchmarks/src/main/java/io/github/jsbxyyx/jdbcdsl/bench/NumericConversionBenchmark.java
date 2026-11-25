package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.bean.ValueConverter;
import io.github.jsbxyyx.jdbcdsl.bean.ValueConverters;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class NumericConversionBenchmark {
    @Param({"double", "float"})
    public String source;

    private final ValueConverter converter = ValueConverters.numeric(Long.class);
    private Object[] values;
    private int index;

    @Setup(Level.Trial)
    public void setup() {
        values = switch (source) {
            case "double" -> new Object[] {128.0d, -129.0d, 65536.0d, 1234567.0d};
            case "float" -> new Object[] {128.0f, -129.0f, 65536.0f, 1234567.0f};
            default -> throw new IllegalArgumentException("Unknown source: " + source);};
    }

    @Benchmark
    public Object convertExactInteger() {
        Object value = values[index++ & 3];
        return converter.convert(value);
    }
}
