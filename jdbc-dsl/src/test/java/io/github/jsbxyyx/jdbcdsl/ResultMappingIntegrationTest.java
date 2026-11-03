package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultMappingIntegrationTest {

    private static final String FORWARD = "SELECT 'Alice' AS firstName, 'Smith' AS lastName";
    private static final String REVERSED = "SELECT 'Smith' AS lastName, 'Alice' AS firstName";

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        JdbcDslConfig.setAllowNullToPrimitiveDefault(false);
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:mapping;DB_CLOSE_DELAY=-1", "sa", "");
        executor = new JdbcDslExecutor(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void distinctProjectionsAndQueryMethodsUseTheirOwnColumnLayout() {
        assertName(executor.query(FORWARD, Map.of(), Person.class).get(0));
        assertName(executor.queryOne(REVERSED, Map.of(), Person.class));

        Person partial = executor.queryOne("SELECT 'Smith' AS lastName", Map.of(), Person.class);
        assertThat(partial.firstName).isNull();
        assertThat(partial.lastName).isEqualTo("Smith");

        assertName(executor.queryOne(
                "SELECT 'ignored' AS extra, 'Alice' AS firstName, 'Smith' AS lastName", Map.of(), Person.class));
        OtherPerson other = executor.queryOne(FORWARD, Map.of(), OtherPerson.class);
        assertThat(other.firstName).isEqualTo("Alice");
        assertThat(other.lastName).isEqualTo("Smith");
        assertName(executor.queryOne(FORWARD, Map.of(), Person.class));
    }

    @Test
    void concurrentQueriesDoNotShareTheirRowMapper() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String sql = i % 2 == 0 ? FORWARD : REVERSED;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 50; iteration++) {
                        assertName(executor.queryOne(sql, Map.of(), Person.class));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void primitiveNullMappingIsDisabledByDefaultAndCanBeEnabled() {
        String sql = "SELECT CAST(123 AS INTEGER) AS converted, CAST(NULL AS INTEGER) AS nullable, "
                + "CAST(NULL AS INTEGER) AS primitiveValue";
        assertThat(JdbcDslConfig.isAllowNullToPrimitiveDefault()).isFalse();
        assertThatThrownBy(() -> executor.queryOne(sql, Map.of(), NumericDto.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primitiveValue")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> executor.queryOne("SELECT NULL AS count", Map.of(), PrimitiveRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count");

        JdbcDslConfig.setAllowNullToPrimitiveDefault(true);
        NumericDto dto = executor.queryOne(sql, Map.of(), NumericDto.class);
        assertThat(dto.converted).isEqualTo((short) 123);
        assertThat(dto.nullable).isNull();
        assertThat(dto.primitiveValue).isZero();
        assertThat(executor.queryOne("SELECT NULL AS count", Map.of(), PrimitiveRecord.class))
                .isEqualTo(new PrimitiveRecord(0));
        assertThat(executor.queryOne("SELECT NULL AS enabled", Map.of(), BooleanRecord.class))
                .isEqualTo(new BooleanRecord(false));
    }

    @Test
    void conversionAndSetterFailuresAbortTheQuery() {
        assertThatThrownBy(() -> executor.queryOne("SELECT 'invalid' AS converted", Map.of(), NumericDto.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column 1")
                .hasMessageContaining("converted")
                .hasMessageContaining(NumericDto.class.getName())
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> executor.queryOne("SELECT 'Alice' AS firstName", Map.of(), RejectingDto.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column 1")
                .hasMessageContaining("firstName")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void sharedCacheManagerDoesNotShareQueryLayoutsBetweenExecutors() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:mapping", "sa", "");
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        JdbcDslCacheManager cacheManager = new JdbcDslCacheManager();
        JdbcDslExecutor first = new JdbcDslExecutor(template, new H2Dialect(), cacheManager);
        JdbcDslExecutor second = new JdbcDslExecutor(template, new H2Dialect(), cacheManager);
        assertName(first.queryOne(FORWARD, Map.of(), Person.class));
        assertName(second.queryOne(REVERSED, Map.of(), Person.class));
        assertName(first.queryOne(REVERSED, Map.of(), Person.class));
    }

    @Test
    void recordsMapDifferentColumnOrdersAndNullableValues() {
        PersonRecord expected = new PersonRecord("Alice", "Smith");
        assertThat(executor.query(FORWARD, Map.of(), PersonRecord.class)).containsExactly(expected);
        assertThat(executor.queryOne(REVERSED, Map.of(), PersonRecord.class)).isEqualTo(expected);
        assertThat(executor.queryOne(
                        "SELECT NULL AS firstName, 'Smith' AS lastName, 1 AS extra", Map.of(), PersonRecord.class))
                .isEqualTo(new PersonRecord(null, "Smith"));
        assertThat(executor.queryOne(
                        "SELECT CAST(123 AS INTEGER) AS converted, CAST(NULL AS INTEGER) AS nullable",
                        Map.of(),
                        NumericRecord.class))
                .isEqualTo(new NumericRecord((short) 123, null));
        assertThat(executor.queryOne("SELECT CAST(7 AS INTEGER) AS count", Map.of(), PrimitiveRecord.class))
                .isEqualTo(new PrimitiveRecord(7));
        assertThat(executor.queryOne(
                        "SELECT TIMESTAMP '2024-01-02 03:04:05' AS createdAt", Map.of(), TimestampRecord.class))
                .isEqualTo(new TimestampRecord(LocalDateTime.of(2024, 1, 2, 3, 4, 5)));
        assertThat(executor.queryOne(FORWARD, Map.of(), OtherPersonRecord.class))
                .isEqualTo(new OtherPersonRecord("Alice", "Smith"));
        assertThat(executor.queryOne(FORWARD, Map.of(), PersonRecord.class)).isEqualTo(expected);
    }

    @Test
    void recordMappingRejectsMissingDuplicateNullAndInvalidValues() {
        assertThatThrownBy(() -> executor.queryOne("SELECT 'Alice' AS firstName", Map.of(), PersonRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing column")
                .hasMessageContaining("lastName");
        assertThatThrownBy(() -> executor.queryOne(
                        "SELECT 'Alice' AS firstName, 'Bob' AS FIRSTNAME, 'Smith' AS lastName",
                        Map.of(),
                        PersonRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple columns")
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> executor.queryOne("SELECT NULL AS count", Map.of(), PrimitiveRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQL NULL")
                .hasMessageContaining("count");
        assertThatThrownBy(() -> executor.queryOne(
                        "SELECT 'invalid' AS converted, NULL AS nullable", Map.of(), NumericRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("converted")
                .hasMessageContaining("column 1")
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> executor.queryOne("SELECT 'bad' AS name", Map.of(), ValidatedRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ValidatedRecord.class.getName())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRecordQueriesUseTheirOwnLayouts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                String sql = i % 2 == 0 ? FORWARD : REVERSED;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 50; iteration++) {
                        assertThat(executor.queryOne(sql, Map.of(), PersonRecord.class))
                                .isEqualTo(new PersonRecord("Alice", "Smith"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertName(Person person) {
        assertThat(person.firstName).isEqualTo("Alice");
        assertThat(person.lastName).isEqualTo("Smith");
    }

    static class Person {
        private String firstName;
        private String lastName;
    }

    static class OtherPerson {
        private String firstName;
        private String lastName;
    }

    static class NumericDto {
        private Short converted;
        private Integer nullable;
        private int primitiveValue;
    }

    static class RejectingDto {
        public void setFirstName(String value) {
            throw new IllegalArgumentException("rejected");
        }
    }

    private record PersonRecord(String firstName, String lastName) {}

    private record NumericRecord(short converted, Integer nullable) {}

    private record PrimitiveRecord(int count) {}

    private record BooleanRecord(boolean enabled) {}

    private record OtherPersonRecord(String firstName, String lastName) {}

    private record TimestampRecord(LocalDateTime createdAt) {}

    private record ValidatedRecord(String name) {
        private ValidatedRecord {
            if ("bad".equals(name)) {
                throw new IllegalArgumentException("invalid name");
            }
        }
    }
}
