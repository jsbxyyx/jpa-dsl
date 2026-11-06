package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;
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
        // JDBC Timestamp -> LocalDateTime
        ValueConverter dateTimeConverter = registry.getConverter(Types.TIMESTAMP, LocalDateTime.class);
        Timestamp timestamp = Timestamp.valueOf("2026-01-01 10:20:30");
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 20, 30), dateTimeConverter.convert(timestamp));

        // JDBC Date -> LocalDate
        ValueConverter dateConverter = registry.getConverter(Types.DATE, LocalDate.class);
        Date sqlDate = Date.valueOf("2026-01-01");
        assertEquals(LocalDate.of(2026, 1, 1), dateConverter.convert(sqlDate));

        assertNull(dateTimeConverter.convert(null));
        assertNull(dateConverter.convert(null));
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

    @Test
    void testNumericWrapperConversionsFromIntegerJdbcValue() {
        ValueConverter byteConverter = registry.getConverter(Types.TINYINT, Byte.class);
        assertEquals(Byte.valueOf((byte) 7), byteConverter.convert(Integer.valueOf(7)));

        ValueConverter shortConverter = registry.getConverter(Types.SMALLINT, Short.class);
        assertEquals(Short.valueOf((short) 123), shortConverter.convert(Integer.valueOf(123)));

        ValueConverter longConverter = registry.getConverter(Types.BIGINT, Long.class);
        assertEquals(Long.valueOf(123L), longConverter.convert(Integer.valueOf(123)));
    }

    @Test
    void testIntegerConversionsCheckAllBoundaries() {
        Class<?>[][] types = {
            {byte.class, Byte.class}, {short.class, Short.class},
            {int.class, Integer.class}, {long.class, Long.class}
        };
        long[] minima = {Byte.MIN_VALUE, Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE};
        long[] maxima = {Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE};
        Object[] expectedMinima = {Byte.MIN_VALUE, Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE};
        Object[] expectedMaxima = {Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE};
        for (int i = 0; i < types.length; i++) {
            for (Class<?> type : types[i]) {
                ValueConverter converter = registry.getConverter(Types.NUMERIC, type);
                BigInteger min = BigInteger.valueOf(minima[i]);
                BigInteger max = BigInteger.valueOf(maxima[i]);
                assertEquals(expectedMinima[i], converter.convert(new BigDecimal(min)));
                assertEquals(expectedMaxima[i], converter.convert(new BigDecimal(max)));
                assertThrows(ArithmeticException.class, () -> converter.convert(min.subtract(BigInteger.ONE)));
                assertThrows(ArithmeticException.class, () -> converter.convert(max.add(BigInteger.ONE)));
                assertThrows(ArithmeticException.class, () -> converter.convert(new BigDecimal("1.01")));
                assertThrows(ArithmeticException.class, () -> converter.convert(-1.5d));
                assertEquals(expectedMinima[i], converter.convert(minima[i]));
                assertEquals(expectedMaxima[i], converter.convert(maxima[i]));
            }
        }
    }

    @Test
    void testIntegerConversionsHandleDecimalFloatingPointAndOtherNumbersExactly() {
        ValueConverter converter = registry.getConverter(Types.NUMERIC, long.class);
        assertEquals(123L, converter.convert(new BigDecimal("123.000")));
        assertEquals(-123L, converter.convert(-123.0f));
        assertEquals(42L, converter.convert(new AtomicInteger(42)));
        assertEquals(9223372036854774784L, converter.convert(0x1.fffffffffffffp62));
        assertEquals(Long.MIN_VALUE, converter.convert(-0x1.0p63));
        assertThrows(ArithmeticException.class, () -> converter.convert(0x1.0p63));
        assertThrows(ArithmeticException.class, () -> converter.convert(Math.nextDown(-0x1.0p63)));
        assertThrows(ArithmeticException.class, () -> converter.convert(Double.NaN));
        assertThrows(ArithmeticException.class, () -> converter.convert(Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> converter.convert(Float.NEGATIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> converter.convert(new BigDecimal("1.0000000000000000001")));
    }

    @Test
    void floatingPointConversionsMatchExactDecimalReference() {
        Random random = new Random(0x5eedL);
        double[] boundaries = {
            -0x1.0p63,
            Math.nextDown(-0x1.0p63),
            Math.nextUp(-0x1.0p63),
            0x1.0p63,
            Math.nextDown(0x1.0p63),
            Math.nextUp(0x1.0p63),
            -0.0,
            0.0,
            Double.MIN_VALUE,
            -Double.MIN_VALUE,
            -129,
            -128,
            127,
            128,
            -32769,
            -32768,
            32767,
            32768,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            0x1.0p31,
            -1.5,
            1.5,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        };
        for (double value : boundaries) {
            assertFloatingConversionMatchesReference(value);
            assertFloatingConversionMatchesReference((float) value);
        }
        for (int i = 0; i < 5000; i++) {
            assertFloatingConversionMatchesReference(Double.longBitsToDouble(random.nextLong()));
            assertFloatingConversionMatchesReference(Float.intBitsToFloat(random.nextInt()));
            assertFloatingConversionMatchesReference((double) random.nextLong());
            assertFloatingConversionMatchesReference((float) random.nextInt());
        }
    }

    private void assertFloatingConversionMatchesReference(Number input) {
        Class<?>[][] types = {
            {byte.class, Byte.class}, {short.class, Short.class},
            {int.class, Integer.class}, {long.class, Long.class}
        };
        long[] minima = {Byte.MIN_VALUE, Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE};
        long[] maxima = {Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE};
        double value = input.doubleValue();
        Long exact = null;
        if (Double.isFinite(value)) {
            try {
                exact = new BigDecimal(value).longValueExact();
            } catch (ArithmeticException ignored) {
                // An inexact or out-of-range value must fail for every target type.
            }
        }
        for (int i = 0; i < types.length; i++) {
            for (Class<?> type : types[i]) {
                ValueConverter converter = registry.getConverter(Types.NUMERIC, type);
                if (exact == null || exact < minima[i] || exact > maxima[i]) {
                    ArithmeticException failure =
                            assertThrows(ArithmeticException.class, () -> converter.convert(input));
                    assertEquals(
                            "Cannot convert " + input.getClass().getName() + " exactly to " + type.getTypeName(),
                            failure.getMessage());
                    assertEquals(exact == null, failure.getCause() instanceof ArithmeticException);
                } else {
                    Number result = (Number) converter.convert(input);
                    assertEquals(exact.longValue(), result.longValue());
                    assertEquals(types[i][1], result.getClass());
                }
            }
        }
    }

    @Test
    void testNumericWrapperConversionsKeepNull() {
        assertNull(registry.getConverter(Types.TINYINT, Byte.class).convert(null));
        assertNull(registry.getConverter(Types.SMALLINT, Short.class).convert(null));
        assertNull(registry.getConverter(Types.BIGINT, Long.class).convert(null));
    }

    @Test
    void testPrimitiveNumericNullUsesDefaultValue() {
        assertEquals((byte) 0, registry.getConverter(Types.TINYINT, byte.class).convert(null));
        assertEquals(
                (short) 0, registry.getConverter(Types.SMALLINT, short.class).convert(null));
        assertEquals(0, registry.getConverter(Types.INTEGER, int.class).convert(null));
        assertEquals(0L, registry.getConverter(Types.BIGINT, long.class).convert(null));
    }

    @Test
    void testJdbcReturnedIntegerCanMapToPrimitiveNumericTypes() {
        assertEquals((byte) 8, registry.getConverter(Types.TINYINT, byte.class).convert(Integer.valueOf(8)));
        assertEquals(
                (short) 128, registry.getConverter(Types.SMALLINT, short.class).convert(Integer.valueOf(128)));
        assertEquals(1024, registry.getConverter(Types.INTEGER, int.class).convert(Integer.valueOf(1024)));
    }

    enum TestEnum {
        VALUE1,
        VALUE2,
        VALUE3
    }
}
