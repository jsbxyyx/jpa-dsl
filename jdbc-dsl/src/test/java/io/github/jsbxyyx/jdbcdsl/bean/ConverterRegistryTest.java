package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConverterRegistry and DefaultConverterRegistry.
 */
class ConverterRegistryTest {

    private ConverterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultConverterRegistry(DefaultConversionService.getSharedInstance());
    }

    @Test
    void testGetConverterByJdbcType() {
        ValueConverter converter = registry.getConverter(Types.VARCHAR, String.class);
        assertNotNull(converter, "Converter should not be null");

        String result = (String) converter.convert("test");
        assertEquals("test", result, "String conversion should work");
    }

    @Test
    void testGetConverterBySourceType() {
        ValueConverter converter = registry.getConverter(String.class, Integer.class);
        assertNotNull(converter, "Converter should not be null");

        Integer result = (Integer) converter.convert("123");
        assertEquals(123, result, "String to Integer conversion should work");
    }

    @Test
    void testConverterCaching() {
        ValueConverter converter1 = registry.getConverter(Types.BIGINT, Long.class);
        ValueConverter converter2 = registry.getConverter(Types.BIGINT, Long.class);

        assertSame(converter1, converter2, "Same converter should be returned from cache");
    }

    @Test
    void testNumericConversions() {
        // Integer conversions
        ValueConverter intConverter = registry.getConverter(Types.INTEGER, Integer.class);
        assertEquals(42, intConverter.convert(42));

        // Long conversions
        ValueConverter longConverter = registry.getConverter(Types.BIGINT, Long.class);
        assertEquals(42L, longConverter.convert(42L));

        // BigDecimal conversions
        ValueConverter decimalConverter = registry.getConverter(Types.DECIMAL, BigDecimal.class);
        assertEquals(new BigDecimal("42.5"), decimalConverter.convert(new BigDecimal("42.5")));

        // String to Integer conversion
        ValueConverter stringToIntConverter = registry.getConverter(String.class, Integer.class);
        assertEquals(42, stringToIntConverter.convert("42"));
    }

    @Test
    void testTemporalConversions() {
        // LocalDateTime
        ValueConverter dateTimeConverter = registry.getConverter(Types.TIMESTAMP, LocalDateTime.class);
        LocalDateTime now = LocalDateTime.now();
        assertEquals(now, dateTimeConverter.convert(now));

        // LocalDate
        ValueConverter dateConverter = registry.getConverter(Types.DATE, LocalDate.class);
        LocalDate today = LocalDate.now();
        assertEquals(today, dateConverter.convert(today));
    }

    @Test
    void testBooleanConversions() {
        ValueConverter booleanConverter = registry.getConverter(Types.BOOLEAN, Boolean.class);

        assertEquals(true, booleanConverter.convert(true));
        assertEquals(false, booleanConverter.convert(false));

        // Boolean identity conversion
        assertEquals(Boolean.TRUE, booleanConverter.convert(Boolean.TRUE));
        assertEquals(Boolean.FALSE, booleanConverter.convert(Boolean.FALSE));
    }

    @Test
    void testNullHandling() {
        ValueConverter converter = registry.getConverter(Types.VARCHAR, String.class);
        assertNull(converter.convert(null), "Null input should return null");
    }

    @Test
    void testIdentityConversion() {
        // Same type should use identity converter
        ValueConverter converter = registry.getConverter(String.class, String.class);
        String input = "test";
        assertSame(input, converter.convert(input), "Identity conversion should return same instance");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        ValueConverter converter = registry.getConverter(Types.VARCHAR, String.class);
                        assertNotNull(converter);
                        String result = (String) converter.convert("test" + j);
                        assertEquals("test" + j, result);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * iterationsPerThread, successCount.get(), "All operations should succeed");
    }

    @Test
    void testDifferentJdbcTypesToSameTarget() {
        // Different JDBC types to same target type should use different converters
        ValueConverter varcharConverter = registry.getConverter(Types.VARCHAR, String.class);
        ValueConverter charConverter = registry.getConverter(Types.CHAR, String.class);

        assertNotNull(varcharConverter);
        assertNotNull(charConverter);
        // Both should work correctly
        assertEquals("test", varcharConverter.convert("test"));
        assertEquals("test", charConverter.convert("test"));
    }

    @Test
    void testEnumConversion() {
        ValueConverter converter = registry.getConverter(Types.VARCHAR, TestEnum.class);
        assertNotNull(converter);

        TestEnum result = (TestEnum) converter.convert("VALUE1");
        assertEquals(TestEnum.VALUE1, result);
    }

    @Test
    void testPrimitiveToWrapperConversion() {
        // int to Integer
        ValueConverter converter = registry.getConverter(int.class, Integer.class);
        assertNotNull(converter);
        assertEquals(42, converter.convert(42));

        // long to Long
        ValueConverter longConverter = registry.getConverter(long.class, Long.class);
        assertNotNull(longConverter);
        assertEquals(42L, longConverter.convert(42L));
    }

    @Test
    void testConverterReusability() {
        // Get same converter multiple times and verify it's cached
        ValueConverter c1 = registry.getConverter(Types.INTEGER, Integer.class);
        ValueConverter c2 = registry.getConverter(Types.INTEGER, Integer.class);
        ValueConverter c3 = registry.getConverter(Types.INTEGER, Integer.class);

        assertSame(c1, c2, "Converters should be cached");
        assertSame(c2, c3, "Converters should be cached");

        // Verify converter still works after multiple retrievals
        assertEquals(100, c1.convert(100));
        assertEquals(200, c2.convert(200));
        assertEquals(300, c3.convert(300));
    }

    enum TestEnum {
        VALUE1,
        VALUE2,
        VALUE3
    }
}
