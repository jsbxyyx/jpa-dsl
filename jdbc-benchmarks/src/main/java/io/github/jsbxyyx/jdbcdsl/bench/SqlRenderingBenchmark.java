package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.SqlRenderer;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.Table;
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

import static org.jooq.conf.ParamType.INDEXED;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.using;

/** Separates query-spec construction from SQL rendering cost. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
@State(Scope.Thread)
public class SqlRenderingBenchmark {

    private SelectSpec<BenchmarkRow, BenchmarkRow> spec;
    private DSLContext jooq;
    private Table<?> jooqTable;
    private Field<Long> jooqId;
    private Field<String> jooqName;
    private Field<Integer> jooqAmount;
    private Query jooqQuery;

    @Setup(Level.Trial)
    public void setup() {
        spec = buildSpec();
        jooq = using(SQLDialect.H2);
        jooqTable = table("jpa_dsl_bench_rows");
        jooqId = field("id", Long.class);
        jooqName = field("name", String.class);
        jooqAmount = field("amount", Integer.class);
        jooqQuery = buildJooqQuery();
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

    @Benchmark
    public String renderJooqPrebuiltQuery() {
        return jooqQuery.getSQL(INDEXED);
    }

    @Benchmark
    public Query buildJooqQueryOnly() {
        return buildJooqQuery();
    }

    @Benchmark
    public String buildAndRenderJooq() {
        return buildJooqQuery().getSQL(INDEXED);
    }

    private Query buildJooqQuery() {
        return jooq.select(jooqId, jooqName, jooqAmount)
                .from(jooqTable)
                .where(jooqId.eq(42L))
                .orderBy(jooqId.asc());
    }

    private SelectSpec<BenchmarkRow, BenchmarkRow> buildSpec() {
        return SelectBuilder.from(BenchmarkRow.class)
                .where(w -> w.eq(BenchmarkRow::getId, 42L))
                .orderBy(JSort.by(JOrder.asc(BenchmarkRow::getId)))
                .mapToEntity();
    }
}
