package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.bean.MethodHandleAccessor;
import io.github.jsbxyyx.jdbcdsl.bean.PropertyAccessor;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * Isolates setter invocation cost from ResultSet access, row creation and collection allocation.
 * Each benchmark updates the same preallocated bean instances with equivalent values.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class SetterInvocationBenchmark {
    @Param({"100", "10000"})
    public int rowCount;

    private BenchmarkRow[] rows;
    private Long[] ids;
    private Integer[] amounts;
    private String[] names;
    private MethodHandle typedId;
    private MethodHandle typedName;
    private MethodHandle typedAmount;
    private MethodHandle genericId;
    private MethodHandle genericName;
    private MethodHandle genericAmount;
    private PropertyAccessor accessorId;
    private PropertyAccessor accessorName;
    private PropertyAccessor accessorAmount;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        rows = new BenchmarkRow[rowCount];
        ids = new Long[rowCount];
        amounts = new Integer[rowCount];
        names = new String[rowCount];
        for (int i = 0; i < rowCount; i++) {
            rows[i] = new BenchmarkRow();
            ids[i] = Long.valueOf(i);
            amounts[i] = Integer.valueOf(i);
            names[i] = "name";
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        typedId = lookup.findVirtual(BenchmarkRow.class, "setId", MethodType.methodType(void.class, Long.class));
        typedName = lookup.findVirtual(BenchmarkRow.class, "setName", MethodType.methodType(void.class, String.class));
        typedAmount =
                lookup.findVirtual(BenchmarkRow.class, "setAmount", MethodType.methodType(void.class, Integer.class));

        genericId = typedId.asType(MethodType.methodType(void.class, Object.class, Object.class));
        genericName = typedName.asType(MethodType.methodType(void.class, Object.class, Object.class));
        genericAmount = typedAmount.asType(MethodType.methodType(void.class, Object.class, Object.class));

        accessorId = new MethodHandleAccessor("id", Long.class, null, typedId);
        accessorName = new MethodHandleAccessor("name", String.class, null, typedName);
        accessorAmount = new MethodHandleAccessor("amount", Integer.class, null, typedAmount);
    }

    @Benchmark
    public BenchmarkRow typedInvokeExact() throws Throwable {
        for (int i = 0; i < rowCount; i++) {
            BenchmarkRow row = rows[i];
            typedId.invokeExact(row, ids[i]);
            typedName.invokeExact(row, names[i]);
            typedAmount.invokeExact(row, amounts[i]);
        }
        return rows[rowCount - 1];
    }

    @Benchmark
    public BenchmarkRow genericInvokeExact() throws Throwable {
        for (int i = 0; i < rowCount; i++) {
            Object row = rows[i];
            genericId.invokeExact(row, (Object) ids[i]);
            genericName.invokeExact(row, (Object) names[i]);
            genericAmount.invokeExact(row, (Object) amounts[i]);
        }
        return rows[rowCount - 1];
    }

    @Benchmark
    public BenchmarkRow propertyAccessorWrite() {
        for (int i = 0; i < rowCount; i++) {
            Object row = rows[i];
            accessorId.write(row, ids[i]);
            accessorName.write(row, names[i]);
            accessorAmount.write(row, amounts[i]);
        }
        return rows[rowCount - 1];
    }
}
