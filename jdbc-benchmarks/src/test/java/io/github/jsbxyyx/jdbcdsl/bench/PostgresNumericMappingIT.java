package io.github.jsbxyyx.jdbcdsl.bench;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class PostgresNumericMappingIT {
    private BenchmarkDatabase db;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("JDBC_BENCH_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Requires a dedicated PostgreSQL database");
        db = new BenchmarkDatabase();
        db.setup();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.tearDown();
        }
    }

    @Test
    void floatingPointValuesMapExactlyToIntegers() {
        for (String jdbcType : List.of("REAL", "DOUBLE PRECISION")) {
            String exact = "SELECT CAST(42.0 AS " + jdbcType + ") AS amount";
            assertEquals(42, db.dsl.queryOne(exact, Map.of(), Integer.class));
            assertEquals(
                    42, db.dsl.queryOne(exact, Map.of(), BenchmarkRow.class).getAmount());
            assertEquals(new AmountRecord(42), db.dsl.queryOne(exact, Map.of(), AmountRecord.class));

            for (String input : List.of("42.5", "2147483648")) {
                String invalid = "SELECT CAST(" + input + " AS " + jdbcType + ") AS amount";
                for (Class<?> type : List.of(Integer.class, BenchmarkRow.class, AmountRecord.class)) {
                    IllegalStateException failure =
                            assertThrows(IllegalStateException.class, () -> db.dsl.queryOne(invalid, Map.of(), type));
                    assertInstanceOf(ArithmeticException.class, failure.getCause());
                }
            }
        }
    }

    private record AmountRecord(int amount) {}
}
