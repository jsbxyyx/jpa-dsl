package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScalarRowMapperTest {

    @Test
    void cachesMetadataAndConverterAcrossRows() throws Exception {
        ResultSet rs = resultSet(1, Types.BIGINT, 7L);
        ResultSetMetaData metadata = rs.getMetaData();
        clearInvocations(rs);
        ConverterRegistry registry = mock(ConverterRegistry.class);
        when(registry.getConverter(Types.BIGINT, Long.class)).thenReturn(value -> value);
        ScalarRowMapper<Long> mapper = new ScalarRowMapper<>(Long.class, registry);

        assertEquals(7L, mapper.mapRow(rs, 0));
        assertEquals(7L, mapper.mapRow(rs, 1));
        assertEquals(7L, mapper.mapRow(rs, 2));
        verify(rs, times(1)).getMetaData();
        verify(metadata, times(1)).getColumnCount();
        verify(metadata, times(1)).getColumnType(1);
        verify(registry, times(1)).getConverter(Types.BIGINT, Long.class);
    }

    @Test
    void differentResultSetsAreValidatedAndUseTheirOwnConverter() throws Exception {
        ResultSet numbers = resultSet(1, Types.INTEGER, 7);
        ResultSet strings = resultSet(1, Types.VARCHAR, "8");
        ResultSet invalid = resultSet(2, Types.INTEGER, 1);
        ConverterRegistry registry = mock(ConverterRegistry.class);
        when(registry.getConverter(Types.INTEGER, String.class)).thenReturn(value -> "number:" + value);
        when(registry.getConverter(Types.VARCHAR, String.class)).thenReturn(value -> "string:" + value);
        ScalarRowMapper<String> mapper = new ScalarRowMapper<>(String.class, registry);

        assertEquals("number:7", mapper.mapRow(numbers, 0));
        assertEquals("string:8", mapper.mapRow(strings, 0));
        assertThrows(IncorrectResultSetColumnCountException.class, () -> mapper.mapRow(invalid, 0));
        assertEquals("string:8", mapper.mapRow(strings, 1));
        assertEquals("number:7", mapper.mapRow(numbers, 1));
    }

    @Test
    void concurrentRowsInitializeMetadataAndConverterOnce() throws Exception {
        ResultSet rs = resultSet(1, Types.BIGINT, 7L);
        ResultSetMetaData metadata = rs.getMetaData();
        clearInvocations(rs);
        ConverterRegistry registry = mock(ConverterRegistry.class);
        when(registry.getConverter(Types.BIGINT, Long.class)).thenReturn(value -> value);
        ScalarRowMapper<Long> mapper = new ScalarRowMapper<>(Long.class, registry);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mapper.mapRow(rs, 0);
                }));
            }
            start.countDown();
            for (Future<Long> result : results) {
                assertEquals(7L, result.get(10, TimeUnit.SECONDS));
            }
            verify(rs, times(1)).getMetaData();
            verify(metadata, times(1)).getColumnCount();
            verify(metadata, times(1)).getColumnType(1);
            verify(registry, times(1)).getConverter(Types.BIGINT, Long.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDifferentResultSetsKeepTheirOwnJdbcType() throws Exception {
        ResultSet numbers = resultSet(1, Types.INTEGER, 7);
        ResultSet strings = resultSet(1, Types.VARCHAR, "8");
        ConverterRegistry registry = mock(ConverterRegistry.class);
        when(registry.getConverter(Types.INTEGER, String.class)).thenReturn(value -> "number:" + value);
        when(registry.getConverter(Types.VARCHAR, String.class)).thenReturn(value -> "string:" + value);
        ScalarRowMapper<String> mapper = new ScalarRowMapper<>(String.class, registry);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 200; i++) {
                    assertEquals("number:7", mapper.mapRow(numbers, i));
                }
                return null;
            });
            Future<?> second = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 200; i++) {
                    assertEquals("string:8", mapper.mapRow(strings, i));
                }
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void converterLookupFailureIsWrappedAndRetried() throws Exception {
        ResultSet rs = resultSet(1, Types.BIGINT, 7L);
        ConverterRegistry registry = mock(ConverterRegistry.class);
        when(registry.getConverter(Types.BIGINT, Long.class))
                .thenThrow(new IllegalArgumentException("missing converter"))
                .thenReturn(value -> value);
        ScalarRowMapper<Long> mapper = new ScalarRowMapper<>(Long.class, registry);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> mapper.mapRow(rs, 0));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(7L, mapper.mapRow(rs, 1));
        verify(rs, times(1)).getMetaData();
        verify(registry, times(2)).getConverter(Types.BIGINT, Long.class);
    }

    @Test
    void nullPrimitiveAndMybatisPathsDoNotLookUpConverter() throws Exception {
        ResultSet rs = resultSet(1, Types.INTEGER, null);
        ConverterRegistry registry = mock(ConverterRegistry.class);
        ScalarRowMapper<Integer> strict = new ScalarRowMapper<>(int.class, registry, MappingMode.STRICT, () -> false);
        assertThrows(IllegalStateException.class, () -> strict.mapRow(rs, 0));
        ScalarRowMapper<Integer> defaults = new ScalarRowMapper<>(int.class, registry, MappingMode.STRICT, () -> true);
        assertEquals(0, defaults.mapRow(rs, 0));
        when(rs.wasNull()).thenReturn(true);
        ScalarRowMapper<Integer> mybatis = new ScalarRowMapper<>(int.class, registry, MappingMode.MYBATIS);
        assertNull(mybatis.mapRow(rs, 0));
        verifyNoInteractions(registry);
    }

    private static ResultSet resultSet(int count, int jdbcType, Object value) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(count);
        when(metadata.getColumnType(1)).thenReturn(jdbcType);
        when(rs.getObject(1)).thenReturn(value);
        return rs;
    }
}
