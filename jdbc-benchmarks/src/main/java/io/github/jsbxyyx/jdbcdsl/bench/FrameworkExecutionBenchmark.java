package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.Table;
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
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Compares framework query and mapping overhead on one shared H2 in-memory JDBC driver.
 * No external database server or network is involved in measured operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 8, time = 2)
@Fork(2)
@State(Scope.Benchmark)
public class FrameworkExecutionBenchmark {
    private static final RowMapper<BenchmarkRow> HANDWRITTEN_MAPPER = (rs, rowNum) -> QueryBenchmark.readBean(rs);
    private static final RowMapper<BenchmarkRow> BEAN_PROPERTY_MAPPER = new BeanPropertyRowMapper<>(BenchmarkRow.class);

    @Param({"1", "100", "10000"})
    public int rowCount;

    private BenchmarkDatabase db;
    private DSLContext jooq;
    private Table<?> jooqTable;
    private Field<Long> jooqId;
    private Field<String> jooqName;
    private Field<Integer> jooqAmount;
    private ResultQuery<?> jooqQuery;
    private SelectSpec<BenchmarkRow, BenchmarkRow> dslSpec;
    private String sql;
    private Map<String, Object> namedParams;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        db = new BenchmarkDatabase();
        db.setupLocal();
        jooq = org.jooq.impl.DSL.using(db.dataSource, SQLDialect.H2);
        jooqTable = table(db.rowsTable);
        jooqId = field("id", Long.class);
        jooqName = field("name", String.class);
        jooqAmount = field("amount", Integer.class);
        sql = QueryBenchmark.beanSql(db);
        namedParams = Map.of("max", rowCount);
        jooqQuery = buildJooqQuery();
        dslSpec = buildDslSpec();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Benchmark
    public List<BenchmarkRow> jdbcHandwritten() throws SQLException {
        try (Connection connection = db.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rowCount);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BenchmarkRow> rows = new ArrayList<>(rowCount);
                while (resultSet.next()) {
                    rows.add(QueryBenchmark.readBean(resultSet));
                }
                return rows;
            }
        }
    }

    @Benchmark
    public List<BenchmarkRow> springJdbcHandwritten() {
        return db.jdbc.query(sql, HANDWRITTEN_MAPPER, rowCount);
    }

    @Benchmark
    public List<BenchmarkRow> springJdbcBeanProperty() {
        return db.jdbc.query(sql, BEAN_PROPERTY_MAPPER, rowCount);
    }

    /** Uses the same named-parameter path as jpa-dsl, with a handwritten mapper. */
    @Benchmark
    public List<BenchmarkRow> springNamedJdbcHandwritten() {
        return db.namedJdbc.query(QueryBenchmark.namedBeanSql(db), namedParams, HANDWRITTEN_MAPPER);
    }

    /** Isolates jpa-dsl executor overhead from its built-in bean mapper. */
    @Benchmark
    public List<BenchmarkRow> jpaDslMapped() {
        return db.dsl.queryMapped(QueryBenchmark.namedBeanSql(db), namedParams, HANDWRITTEN_MAPPER);
    }

    @Benchmark
    public List<BenchmarkRow> myBatisResultMap() {
        return db.mybatis.selectRowsResultMap(rowCount);
    }

    /** Includes jOOQ query-model construction, SQL rendering, JDBC execution, and record mapping. */
    @Benchmark
    public List<JooqBenchmarkRow> jooqDsl() {
        return buildJooqQuery().fetchInto(JooqBenchmarkRow.class);
    }

    /** Excludes jOOQ query-model construction but retains rendering, execution, and mapping. */
    @Benchmark
    public List<JooqBenchmarkRow> jooqDslPrebuilt() {
        return jooqQuery.fetchInto(JooqBenchmarkRow.class);
    }

    /** Includes jpa-dsl query-spec construction, SQL rendering, JDBC execution, and mapping. */
    @Benchmark
    public List<BenchmarkRow> jpaDslBuildAndSelect() {
        return db.dsl.select(buildDslSpec());
    }

    /** Excludes jpa-dsl query-spec construction but retains rendering, execution, and mapping. */
    @Benchmark
    public List<BenchmarkRow> jpaDsl() {
        return db.dsl.select(dslSpec);
    }

    @Benchmark
    public List<BenchmarkRow> jpaDslRawSql() {
        return db.dsl.query(sql.replace("?", ":max"), namedParams, BenchmarkRow.class);
    }

    private ResultQuery<?> buildJooqQuery() {
        return jooq.select(jooqId, jooqName, jooqAmount)
                .from(jooqTable)
                .where(jooqId.le((long) rowCount))
                .orderBy(jooqId.asc());
    }

    private SelectSpec<BenchmarkRow, BenchmarkRow> buildDslSpec() {
        return SelectBuilder.fromCte(db.rowsTable, BenchmarkRow.class)
                .where(where -> where.lte(BenchmarkRow::getId, (long) rowCount))
                .orderBy(JSort.by(JOrder.asc(BenchmarkRow::getId)))
                .mapToEntity();
    }
}
