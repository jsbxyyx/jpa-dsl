package io.github.jsbxyyx.jdbcdsl.bean;

import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    void testBoundedCacheWithLRU() throws SQLException {
        MapperCache cache = new MapperCache(new JdbcDslCacheManager(), converterRegistry);

        BeanMappingMeta meta = metaFactory.create(TestEntity.class);

        // Create 3 different ResultSet structures
        ResultSet rs1 = createMockResultSet("id", Types.BIGINT);
        ResultSet rs2 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSet rs3 = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR, "email", Types.VARCHAR);

        ResultSetMapper mapper1 = cache.getMapper(rs1, meta);
        ResultSetMapper mapper2 = cache.getMapper(rs2, meta);
        ResultSetMapper mapper3 = cache.getMapper(rs3, meta);

        // Cache size should not exceed maxSize
        assertTrue(cache.size() <= 2, "Cache size should not exceed maxSize (2)");

        // Access mapper2 again to make it recently used
        ResultSet rs2Again = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
        ResultSetMapper mapper2Again = cache.getMapper(rs2Again, meta);
        assertSame(mapper2, mapper2Again, "Should return cached mapper");
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

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        ResultSet rs = createMockResultSet("id", Types.BIGINT, "name", Types.VARCHAR);
                        ResultSetMapper mapper = cache.getMapper(rs, meta);
                        assertNotNull(mapper);
                        successCount.incrementAndGet();
                    }
                } catch (SQLException e) {
                    fail("SQLException during concurrent access: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * iterationsPerThread, successCount.get(), "All operations should succeed");
        assertEquals(1, cache.size(), "Cache should contain exactly one mapper for same structure");
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

    static class ShortEntity {
        private Short timeIndex;

        public Short getTimeIndex() {
            return timeIndex;
        }

        public void setTimeIndex(Short timeIndex) {
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
