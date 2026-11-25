package io.github.jsbxyyx.jdbcdsl.bean;

import io.github.jsbxyyx.jdbcdsl.PropertyRef;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MapperCache.
 */
class MapperCacheTest {

    private ConverterRegistry converterRegistry;
    private BeanMappingMetaFactory metaFactory;

    @BeforeEach
    void setUp() {
        converterRegistry = new DefaultConverterRegistry(DefaultConversionService.getSharedInstance());
        metaFactory = new BeanMappingMetaFactory();
    }

    @Test
    void configuredCacheSizesAreRespectedBelowLegacyMinimums() {
        JdbcDslCacheManager cacheManager = new JdbcDslCacheManager(1, 1, 1, 1, 1);

        cacheManager.getPropertyRefCache().put("first", new PropertyRef(TestEntity.class, "id"));
        cacheManager.getPropertyRefCache().put("second", new PropertyRef(TestEntity.class, "name"));
        cacheManager.getPropertyRefCache().cleanUp();

        assertTrue(
                cacheManager.getPropertyRefCache().estimatedSize() <= 1,
                "Property-ref cache should honor a configured maximum size of one");
    }

    @Test
    void testUnboundedCache() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);

        ResultSet rs1 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSet rs2 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);

        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        ResultSetMapper mapper1 = cache.getMapper(rs1, meta);
        ResultSetMapper mapper2 = cache.getMapper(rs2, meta);

        // Same structure should return same mapper instance
        assertSame(mapper1, mapper2, "Cache should return same mapper for identical structure");
        assertEquals(1, cache.size(), "Cache should contain exactly one mapper");
    }

    @Test
    void boundedCacheRemainsCorrectAfterEviction() throws SQLException {
        JdbcDslCacheManager cacheManager = new JdbcDslCacheManager(500, 100, 100, 100, 2);
        MapperCache cache = new MapperCache(cacheManager, converterRegistry);

        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        // Create 3 different ResultSet structures
        ResultSet rs1 = createMockResultSet("id", Types.BIGINT);
        ResultSet rs2 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSet rs3 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR, "email", Types.VARCHAR);

        cache.getMapper(rs1, meta);
        ResultSetMapper mapper2 = cache.getMapper(rs2, meta);

        ResultSet rs2Again = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSetMapper mapper2Again = cache.getMapper(rs2Again, meta);
        assertSame(mapper2, mapper2Again, "Should return cached mapper");

        cache.getMapper(rs3, meta);

        // Trigger Caffeine maintenance before asserting the configured bound.
        cache.cleanUp();
        assertTrue(cache.size() <= 2, "Cache size should not exceed maxSize (2)");

        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(123L);
        when(row.getObject(2)).thenReturn("alice");
        when(row.getObject(3)).thenReturn("alice@example.test");
        when(row.getString(2)).thenReturn("alice");
        when(row.getString(3)).thenReturn("alice@example.test");
        TestEntity twoColumns = (TestEntity) cache.getMapper(rs2Again, meta).mapRow(row);
        TestEntity threeColumns = (TestEntity) cache.getMapper(rs3, meta).mapRow(row);
        assertEquals(123L, twoColumns.getId());
        assertEquals("alice", twoColumns.getName());
        assertNull(twoColumns.getEmail());
        assertEquals(123L, threeColumns.getId());
        assertEquals("alice", threeColumns.getName());
        assertEquals("alice@example.test", threeColumns.getEmail());
        cache.cleanUp();
        assertTrue(cache.size() <= 2, "Cache size should remain bounded after reloading");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException, SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        ResultSet rs = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
                        ResultSetMapper mapper = cache.getMapper(rs, meta);
                        assertNotNull(mapper);

                        // The cached mapper is shared across threads, but each thread must
                        // map its own ResultSet without leaking row values through mapper state.
                        long expectedId = (long) threadIndex * iterationsPerThread + j;
                        ResultSet row = mock(ResultSet.class);
                        when(row.getObject(1)).thenReturn(expectedId);
                        when(row.wasNull()).thenReturn(false);
                        when(row.getString(2)).thenReturn("thread-" + threadIndex + "-row-" + j);
                        TestEntity entity = (TestEntity) mapper.mapRow(row);
                        assertEquals(expectedId, entity.getId());
                        assertEquals("thread-" + threadIndex + "-row-" + j, entity.getName());
                        successCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertNull(failure.get(), "Concurrent mapper access or row mapping failed");
        assertEquals(threadCount * iterationsPerThread, successCount.get(), "All operations should succeed");
        assertEquals(1, cache.size(), "Cache should contain exactly one mapper for same structure");
    }

    @Test
    void unsignedBigIntBeyondLongRangeMapsToBigIntegerWithoutPrecisionLoss() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(BigIntegerEntity.class);
        ResultSet metadata = createMockResultSet("id", Types.BIGINT, false);
        ResultSetMapper mapper = cache.getMapper(metadata, meta);

        BigInteger unsignedMax = new BigInteger("18446744073709551615");
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(unsignedMax);

        BigIntegerEntity entity = (BigIntegerEntity) mapper.mapRow(row);

        assertEquals(unsignedMax, entity.getId());
        verify(row).getObject(1);
        verify(row, never()).getLong(1);
    }

    @Test
    void bigIntegerJdbcValueMapsToBigDecimalWithoutPrecisionLoss() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(BigDecimalEntity.class);
        ResultSet metadata = createMockResultSet("amount", Types.BIGINT, false);
        ResultSetMapper mapper = cache.getMapper(metadata, meta);

        BigInteger source = new BigInteger("18446744073709551615");
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(source);

        BigDecimalEntity entity = (BigDecimalEntity) mapper.mapRow(row);

        assertEquals(new java.math.BigDecimal(source), entity.getAmount());
    }

    @Test
    void recordMappingPreservesUnsignedBigIntegerAndExactDecimalValues() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta bigIntegerMeta = metaFactory.create(UnsignedRecord.class);
        ResultSet unsignedMetadata = createMockResultSet("id", Types.BIGINT, false);
        ResultSetMapper unsignedMapper = cache.getMapper(unsignedMetadata, bigIntegerMeta);
        BigInteger unsignedMax = new BigInteger("18446744073709551615");
        ResultSet unsignedRow = mock(ResultSet.class);
        when(unsignedRow.getObject(1)).thenReturn(unsignedMax);

        assertEquals(unsignedMax, ((UnsignedRecord) unsignedMapper.mapRow(unsignedRow)).id());

        BeanMappingMeta decimalMeta = metaFactory.create(DecimalRecord.class);
        ResultSet decimalMetadata = createMockResultSet("amount", Types.DECIMAL);
        ResultSetMapper decimalMapper = cache.getMapper(decimalMetadata, decimalMeta);
        java.math.BigDecimal exactAmount = new java.math.BigDecimal("12345678901234567890.12345678");
        ResultSet decimalRow = mock(ResultSet.class);
        when(decimalRow.getObject(1)).thenReturn(exactAmount);

        assertEquals(exactAmount, ((DecimalRecord) decimalMapper.mapRow(decimalRow)).amount());
    }

    @Test
    void sharedRecordMapperMapsConcurrentRowsWithoutCrossThreadValues() throws InterruptedException, SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(UnsignedRecord.class);
        ResultSet metadata = createMockResultSet("id", Types.BIGINT, false);
        ResultSetMapper mapper = cache.getMapper(metadata, meta);

        int threadCount = 8;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < threadCount; i++) {
            int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        BigInteger expected = BigInteger.valueOf((long) threadIndex * iterationsPerThread + j);
                        ResultSet row = mock(ResultSet.class);
                        when(row.getObject(1)).thenReturn(expected);
                        UnsignedRecord record = (UnsignedRecord) mapper.mapRow(row);
                        assertEquals(expected, record.id());
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdownNow();
        assertNull(failure.get(), "Concurrent record mapping failed");
    }

    @Test
    void signedMetadataIsPartOfCacheKey() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(LongEntity.class);

        ResultSet signed = createMockResultSet("id", Types.BIGINT, true);
        ResultSet unsigned = createMockResultSet("id", Types.BIGINT, false);
        ResultSetMapper signedMapper = cache.getMapper(signed, meta);
        ResultSetMapper unsignedMapper = cache.getMapper(unsigned, meta);

        assertNotSame(
                signedMapper,
                unsignedMapper,
                "Signed and unsigned columns must not share a mapper with different read strategies");
        assertEquals(2, cache.size());

        ResultSet signedRow = mock(ResultSet.class);
        when(signedRow.getLong(1)).thenReturn(42L);
        when(signedRow.wasNull()).thenReturn(false);
        assertEquals(42L, ((LongEntity) signedMapper.mapRow(signedRow)).getId());

        ResultSet unsignedRow = mock(ResultSet.class);
        when(unsignedRow.getObject(1)).thenReturn(43L);
        assertEquals(43L, ((LongEntity) unsignedMapper.mapRow(unsignedRow)).getId());
    }

    @Test
    void testDifferentColumnStructures() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        ResultSet rs1 = createMockResultSet("id", Types.BIGINT);
        ResultSet rs2 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSet rs3 = createMockResultSet("name", Types.VARCHAR, "email", Types.VARCHAR);

        ResultSetMapper mapper1 = cache.getMapper(rs1, meta);
        ResultSetMapper mapper2 = cache.getMapper(rs2, meta);
        ResultSetMapper mapper3 = cache.getMapper(rs3, meta);

        assertNotSame(mapper1, mapper2, "Different structures should have different mappers");
        assertNotSame(mapper2, mapper3, "Different structures should have different mappers");
        assertEquals(3, cache.size(), "Cache should contain three different mappers");
    }

    @Test
    void unnamedColumnsRetainTheirPositionInTheCacheKey() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);
        ResultSet first = createMockResultSet("", Types.INTEGER, "name", Types.VARCHAR);
        ResultSet second = createMockResultSet("name", Types.VARCHAR, "", Types.INTEGER);
        ResultSetMapper firstMapper = cache.getMapper(first, meta);
        ResultSetMapper secondMapper = cache.getMapper(second, meta);
        assertNotSame(firstMapper, secondMapper);
        assertNull(((ResultSetHandler) firstMapper).getPropertyAccessor(0));
        assertNull(((ResultSetHandler) secondMapper).getPropertyAccessor(1));
    }

    @Test
    void sharedManagerIsolatesRegistriesAndMetadata() throws SQLException {
        JdbcDslCacheManager manager = new JdbcDslCacheManager();
        ConverterRegistry otherRegistry = mock(ConverterRegistry.class);
        when(otherRegistry.getConverter(Types.VARCHAR, String.class)).thenReturn(value -> "custom");
        MapperCache first = new MapperCache(manager, converterRegistry);
        MapperCache second = new MapperCache(manager, otherRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);
        ResultSet rs = createMockResultSet("name", Types.VARCHAR);
        when(rs.getObject(1)).thenReturn("original");
        when(rs.getString(1)).thenReturn("original");
        assertEquals("original", ((TestEntity) first.getMapper(rs, meta).mapRow(rs)).getName());
        assertEquals("custom", ((TestEntity) second.getMapper(rs, meta).mapRow(rs)).getName());
        assertNotSame(first.getMapper(rs, meta), first.getMapper(rs, metaFactory.create(TestEntity.class)));
    }

    @Test
    void customConverterRegistryReceivesOriginalJdbcValueEvenForSignedIntegerColumns() throws SQLException {
        ConverterRegistry customRegistry = mock(ConverterRegistry.class);
        when(customRegistry.getConverter(Types.BIGINT, Long.class))
                .thenReturn(value -> ((BigInteger) value).longValueExact());
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), customRegistry);
        BeanMappingMeta meta = metaFactory.create(LongEntity.class);
        ResultSet metadataSource = createMockResultSet("id", Types.BIGINT, true);
        ResultSetMapper mapper = cache.getMapper(metadataSource, meta);

        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(BigInteger.valueOf(9876543210L));

        LongEntity entity = (LongEntity) mapper.mapRow(row);

        assertEquals(9876543210L, entity.getId());
        verify(row).getObject(1);
        verify(row, never()).getLong(1);
    }

    @Test
    void defaultHandlerUsesTypedDateAndDecimalGettersWithoutChangingValues() throws SQLException {
        LocalDate expectedDate = LocalDate.of(2026, 11, 20);
        BigDecimal expectedAmount = new BigDecimal("12345678901234567890.120000");

        ResultSet dateMetadata = createMockResultSet("date", Types.DATE);
        ResultSetHandler dateHandler = new ResultSetHandler(dateMetadata, metaFactory.create(DateEntity.class));
        ResultSet dateRow = mock(ResultSet.class);
        when(dateRow.getObject(1, LocalDate.class)).thenReturn(expectedDate);
        DateEntity dateEntity = (DateEntity) dateHandler.mapRow(dateRow);
        assertEquals(expectedDate, dateEntity.getDate());
        verify(dateRow).getObject(1, LocalDate.class);
        verify(dateRow, never()).getDate(1);

        ResultSet decimalMetadata = createMockResultSet("amount", Types.DECIMAL);
        ResultSetHandler decimalHandler =
                new ResultSetHandler(decimalMetadata, metaFactory.create(BigDecimalEntity.class));
        ResultSet decimalRow = mock(ResultSet.class);
        when(decimalRow.getBigDecimal(1)).thenReturn(expectedAmount);
        BigDecimalEntity decimalEntity = (BigDecimalEntity) decimalHandler.mapRow(decimalRow);
        assertEquals(expectedAmount, decimalEntity.getAmount());
        assertEquals(expectedAmount.scale(), decimalEntity.getAmount().scale());
        verify(decimalRow).getBigDecimal(1);
        verify(decimalRow, never()).getObject(1);
    }

    @Test
    void customRegistryStillReceivesOriginalDateJdbcValue() throws SQLException {
        ConverterRegistry customRegistry = mock(ConverterRegistry.class);
        AtomicReference<Object> received = new AtomicReference<>();
        when(customRegistry.getConverter(Types.DATE, LocalDate.class)).thenReturn(value -> {
            received.set(value);
            return ((Date) value).toLocalDate();
        });
        ResultSet metadata = createMockResultSet("date", Types.DATE);
        ResultSetHandler handler = new ResultSetHandler(metadata, metaFactory.create(DateEntity.class), customRegistry);
        Date jdbcDate = Date.valueOf("2026-11-20");
        ResultSet row = mock(ResultSet.class);
        ResultSetMetaData rowMetaData = metadata.getMetaData();
        when(row.getMetaData()).thenReturn(rowMetaData);
        when(row.getObject(1)).thenReturn(jdbcDate);

        DateEntity entity = (DateEntity) handler.mapRow(row);

        assertEquals(LocalDate.of(2026, 11, 20), entity.getDate());
        assertSame(jdbcDate, received.get());
        verify(row).getObject(1);
        verify(row, never()).getObject(1, LocalDate.class);
    }

    @Test
    void customDefaultRegistryUsesSafeTypedDateReadWithoutConversionServiceOverride() throws SQLException {
        ConversionService customConversionService = mock(ConversionService.class);
        LocalDate expected = LocalDate.of(2026, 11, 20);
        ConverterRegistry customRegistry = new DefaultConverterRegistry(customConversionService);

        ResultSet metadata = createMockResultSet("date", Types.DATE);
        ResultSetHandler handler = new ResultSetHandler(metadata, metaFactory.create(DateEntity.class), customRegistry);
        ResultSet row = mock(ResultSet.class);
        ResultSetMetaData rowMetaData = metadata.getMetaData();
        when(row.getMetaData()).thenReturn(rowMetaData);
        when(row.getObject(1, LocalDate.class)).thenReturn(expected);

        DateEntity entity = (DateEntity) handler.mapRow(row);

        assertEquals(expected, entity.getDate());
        verifyNoInteractions(customConversionService);
        verify(row).getObject(1, LocalDate.class);
        verify(row, never()).getObject(1);
    }

    @Test
    void testClearCache() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        ResultSet rs = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        cache.getMapper(rs, meta);

        assertEquals(1, cache.size(), "Cache should contain one mapper");

        cache.clear();

        assertEquals(0, cache.size(), "Cache should be empty after clear");
    }

    @Test
    void testCaseInsensitiveColumnNames() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        ResultSet rs1 = createMockResultSet("ID", Types.BIGINT, "NAME", Types.VARCHAR);
        ResultSet rs2 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);

        ResultSetMapper mapper1 = cache.getMapper(rs1, meta);
        ResultSetMapper mapper2 = cache.getMapper(rs2, meta);

        assertSame(mapper1, mapper2, "Column names should be case-insensitive");
        assertEquals(1, cache.size(), "Cache should contain one mapper");
    }

    @Test
    void testColumnLabelSmallIntAliasMapsToShortProperty() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);

        ResultSet rs = createMockResultSet("timeIndex", Types.SMALLINT);
        ResultSetMapper mapper = cache.getMapper(rs, meta);

        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(Short.valueOf((short) 123));

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertEquals(Short.valueOf((short) 123), entity.getTimeIndex());
    }

    @Test
    void testColumnLabelPreferredOverColumnNameForAlias() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);

        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("timeIndex");
        when(metaData.getColumnName(1)).thenReturn("time_index");
        when(metaData.getColumnType(1)).thenReturn(Types.SMALLINT);
        when(rs.getMetaData()).thenReturn(metaData);

        ResultSetMapper mapper = cache.getMapper(rs, meta);
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(Short.valueOf((short) 10));

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertEquals(Short.valueOf((short) 10), entity.getTimeIndex());
    }

    @Test
    void testBlankColumnLabelFallsBackToGeneralMappingPath() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);

        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn(" ");
        when(metaData.getColumnName(1)).thenReturn("");
        when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(rs.getMetaData()).thenReturn(metaData);

        ResultSetMapper mapper = cache.getMapper(rs, meta);
        ResultSet row = mock(ResultSet.class);

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertNull(entity.getTimeIndex());
        verify(row, never()).getObject(1);
    }

    @Test
    void testJdbcIntegerValueConvertsToShortProperty() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);

        ResultSet rs = createMockResultSet("timeIndex", Types.SMALLINT);
        ResultSetMapper mapper = cache.getMapper(rs, meta);

        ResultSet row = mock(ResultSet.class);
        // Some JDBC drivers return Integer for SMALLINT/TINYINT.
        when(row.getObject(1)).thenReturn(Integer.valueOf(123));

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertEquals(Short.valueOf((short) 123), entity.getTimeIndex());
    }

    @Test
    void testJdbcNullValueKeepsWrapperTypeNull() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);

        ResultSet rs = createMockResultSet("timeIndex", Types.SMALLINT);
        ResultSetMapper mapper = cache.getMapper(rs, meta);

        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1)).thenReturn(null);

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertNull(entity.getTimeIndex());
    }

    @Test
    void sharedManagerDoesNotReuseMapperAcrossMappingModesAndPrimitiveNullPolicies() throws SQLException {
        JdbcDslCacheManager manager = new JdbcDslCacheManager();
        BeanMappingMeta meta = metaFactory.create(PrimitiveShortEntity.class);
        ResultSet metadataSource = createMockResultSet("timeIndex", Types.SMALLINT, true);
        MapperCache strict = new MapperCache(
                manager, converterRegistry, UnknownColumnBehavior.NONE, MappingMode.STRICT, () -> false);
        MapperCache mybatis = new MapperCache(
                manager, converterRegistry, UnknownColumnBehavior.NONE, MappingMode.MYBATIS, () -> false);

        ResultSetMapper strictMapper = strict.getMapper(metadataSource, meta);
        ResultSetMapper mybatisMapper = mybatis.getMapper(metadataSource, meta);

        assertNotSame(
                strictMapper,
                mybatisMapper,
                "Mapper cache entries must remain isolated across mapping and primitive-null policies");
        assertEquals(2, strict.size());

        ResultSet row = mock(ResultSet.class);
        when(row.getShort(1)).thenReturn((short) 0);
        when(row.wasNull()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> strictMapper.mapRow(row));
        PrimitiveShortEntity mybatisEntity = (PrimitiveShortEntity) mybatisMapper.mapRow(row);
        assertEquals((short) 0, mybatisEntity.getTimeIndex());
    }

    @Test
    void signedTypedGetterPreservesSqlNullForWrapperProperties() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(ShortEntity.class);
        ResultSet metadataSource = createMockResultSet("timeIndex", Types.SMALLINT, true);
        ResultSetMapper mapper = cache.getMapper(metadataSource, meta);

        ResultSet row = mock(ResultSet.class);
        when(row.getShort(1)).thenReturn((short) 0);
        when(row.wasNull()).thenReturn(true);

        ShortEntity entity = (ShortEntity) mapper.mapRow(row);

        assertNull(entity.getTimeIndex());
        verify(row).getShort(1);
        verify(row).wasNull();
        verify(row, never()).getObject(1);
    }

    @Test
    void signedTypedGetterRejectsSqlNullForPrimitivePropertiesByDefault() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);
        BeanMappingMeta meta = metaFactory.create(PrimitiveShortEntity.class);
        ResultSet metadataSource = createMockResultSet("timeIndex", Types.SMALLINT, true);
        ResultSetMapper mapper = cache.getMapper(metadataSource, meta);

        ResultSet row = mock(ResultSet.class);
        when(row.getShort(1)).thenReturn((short) 0);
        when(row.wasNull()).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> mapper.mapRow(row));

        assertTrue(error.getCause().getMessage().contains("SQL NULL"));
        verify(row).getShort(1);
        verify(row).wasNull();
    }

    private ResultSet createMockResultSet(String columnName, int columnType, boolean signed) throws SQLException {
        ResultSet rs = createMockResultSet(columnName, columnType);
        when(rs.getMetaData().isSigned(1)).thenReturn(signed);
        return rs;
    }

    private ResultSet createMockResultSet(Object... columnDefs) throws SQLException {
        if (columnDefs.length % 2 != 0) {
            throw new IllegalArgumentException("Column definitions must be in pairs (name, type)");
        }

        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        int columnCount = columnDefs.length / 2;
        when(metaData.getColumnCount()).thenReturn(columnCount);

        for (int i = 0; i < columnCount; i++) {
            int columnIndex = i + 1;
            String columnName = (String) columnDefs[i * 2];
            int columnType = (Integer) columnDefs[i * 2 + 1];

            when(metaData.getColumnLabel(columnIndex)).thenReturn(columnName);
            when(metaData.getColumnName(columnIndex)).thenReturn(columnName);
            when(metaData.getColumnType(columnIndex)).thenReturn(columnType);
        }

        when(rs.getMetaData()).thenReturn(metaData);
        return rs;
    }

    static class DateEntity {
        private LocalDate date;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }

    record UnsignedRecord(BigInteger id) {}

    record DecimalRecord(java.math.BigDecimal amount) {}

    static class BigIntegerEntity {
        private BigInteger id;

        public BigInteger getId() {
            return id;
        }

        public void setId(BigInteger id) {
            this.id = id;
        }
    }

    static class BigDecimalEntity {
        private java.math.BigDecimal amount;

        public java.math.BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(java.math.BigDecimal amount) {
            this.amount = amount;
        }
    }

    static class LongEntity {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    static class ShortEntity {
        private Short timeIndex;

        public Short getTimeIndex() {
            return timeIndex;
        }

        public void setTimeIndex(Short timeIndex) {
            this.timeIndex = timeIndex;
        }
    }

    static class PrimitiveShortEntity {
        private short timeIndex;

        public short getTimeIndex() {
            return timeIndex;
        }

        public void setTimeIndex(short timeIndex) {
            this.timeIndex = timeIndex;
        }
    }

    static class TestEntity {
        private Long id;
        private String name;
        private String email;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
