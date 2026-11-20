package io.github.jsbxyyx.jdbcdsl.bench;

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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class JdbcPhaseBenchmark {
    private static String sql(BenchmarkDatabase db) {
        return "SELECT id, name, amount FROM " + db.rowsTable + " WHERE id <= ? ORDER BY id";
    }

    @State(Scope.Thread)
    public static class ExecuteState {
        private Connection connection;
        private PreparedStatement statement;
        private ResultSet resultSet;

        @Setup(Level.Invocation)
        public void setup(BenchmarkDatabase db, QueryBenchmark query) throws SQLException {
            connection = db.dataSource.getConnection();
            statement = connection.prepareStatement(sql(db));
            statement.setFetchSize(500);
            statement.setInt(1, query.rowCount);
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws SQLException {
            try {
                if (resultSet != null) {
                    resultSet.close();
                    resultSet = null;
                }
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                        statement = null;
                    }
                } finally {
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class FetchState {
        private Connection connection;
        private PreparedStatement statement;
        private int expectedRows;
        private ResultSet resultSet;

        @Setup(Level.Invocation)
        public void setup(BenchmarkDatabase db, QueryBenchmark query) throws SQLException {
            expectedRows = query.rowCount;
            connection = db.dataSource.getConnection();
            statement = connection.prepareStatement(sql(db));
            statement.setFetchSize(500);
            statement.setInt(1, query.rowCount);
            resultSet = statement.executeQuery();
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws SQLException {
            try {
                if (resultSet != null) {
                    resultSet.close();
                    resultSet = null;
                }
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                        statement = null;
                    }
                } finally {
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                }
            }
        }
    }

    @Benchmark
    public boolean executeQueryOnly(ExecuteState state) throws SQLException {
        state.resultSet = state.statement.executeQuery();
        return state.resultSet != null;
    }

    @Benchmark
    public int resultSetNextOnly(FetchState state) throws SQLException {
        int rows = 0;
        while (state.resultSet.next()) {
            rows++;
        }
        if (rows != state.expectedRows) {
            throw new IllegalStateException("Expected " + state.expectedRows + " rows but read " + rows);
        }
        return rows;
    }

    @Benchmark
    public long resultSetFetchAndReadOnly(FetchState state) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            long id = state.resultSet.getLong(1);
            String name = state.resultSet.getString(2);
            int amount = state.resultSet.getInt(3);
            checksum += id + amount + name.length();
            rows++;
        }
        if (rows != state.expectedRows) {
            throw new IllegalStateException("Expected " + state.expectedRows + " rows but read " + rows);
        }
        return checksum;
    }

    @Benchmark
    public long resultSetNextAndInstantiate(FetchState state, Blackhole blackhole) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow(0L, "", 0);
            blackhole.consume(row);
            checksum += row.getId() + row.getAmount() + row.getName().length();
            rows++;
        }
        if (rows != state.expectedRows) {
            throw new IllegalStateException("Expected " + state.expectedRows + " rows but read " + rows);
        }
        return checksum;
    }

    @Benchmark
    public long resultSetFetchAndMap(FetchState state, Blackhole blackhole) throws SQLException {
        long checksum = 0;
        int rows = 0;
        while (state.resultSet.next()) {
            BenchmarkRow row = new BenchmarkRow(
                    state.resultSet.getLong(1), state.resultSet.getString(2), state.resultSet.getInt(3));
            blackhole.consume(row);
            checksum += row.getId() + row.getAmount() + row.getName().length();
            rows++;
        }
        if (rows != state.expectedRows) {
            throw new IllegalStateException("Expected " + state.expectedRows + " rows but read " + rows);
        }
        return checksum;
    }
}
