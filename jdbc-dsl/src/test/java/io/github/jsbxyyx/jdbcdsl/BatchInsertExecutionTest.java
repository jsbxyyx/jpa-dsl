package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.PropertyAccessor;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.entity.TAuditUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BatchInsertExecutionTest {
    private NamedParameterJdbcTemplate jdbc;
    private JdbcDslExecutor executor;
    private final LocalDateTime time = LocalDateTime.of(2024, 6, 1, 12, 0);

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        executor = new JdbcDslExecutor(jdbc, new H2Dialect());
        executor.setTimeProvider(() -> time);
    }

    @Test
    void chunksPreserveOrderCountsAndAutofillBeforeExecution() {
        List<TAuditUser> rows = users(5);
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, rows).build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenAnswer(invocation -> {
                    assertThat(rows).allSatisfy(row -> {
                        assertThat(row.getCreatedAt()).isEqualTo(time);
                        assertThat(row.getUpdatedAt()).isEqualTo(time);
                    });
                    return new int[] {1, Statement.SUCCESS_NO_INFO};
                })
                .thenReturn(new int[] {Statement.EXECUTE_FAILED, 0}, new int[] {1});

        assertThat(executor.executeBatchInsert(spec, 2))
                .containsExactly(1, Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED, 0, 1);
        ArgumentCaptor<SqlParameterSource[]> batches = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(3)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.length).containsExactly(2, 2, 1);
        List<String> names = new ArrayList<>();
        for (SqlParameterSource[] batch : batches.getAllValues()) {
            for (SqlParameterSource params : batch) {
                names.add((String) params.getValue("username"));
                assertThat(params.hasValue("id")).isFalse();
                assertThat(params.getValue("created_at")).isEqualTo(time);
            }
        }
        assertThat(names).containsExactly("user-0", "user-1", "user-2", "user-3", "user-4");
    }

    @Test
    void unchunkedAndSingleChunkPreserveDriverCountsAndExplicitColumns() {
        List<TAuditUser> rows = users(2);
        BatchInsertSpec<TAuditUser> spec = BatchInsertBuilder.of(TAuditUser.class, rows)
                .columns(TAuditUser::getUsername)
                .build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenReturn(new int[] {Statement.SUCCESS_NO_INFO});

        assertThat(executor.executeBatchInsert(spec)).containsExactly(Statement.SUCCESS_NO_INFO);
        assertThat(executor.executeBatchInsert(spec, 10)).containsExactly(Statement.SUCCESS_NO_INFO);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource[]> params = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(2)).batchUpdate(sql.capture(), params.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value).contains("(username) VALUES (:username)"));
        assertThat(params.getValue()).hasSize(2);
        assertThat(params.getValue()[0].getValue("username")).isEqualTo("user-0");
    }

    @Test
    void rejectsInvalidSizesAndDoesNotExecuteForEmptyInput() {
        BatchInsertSpec<TAuditUser> empty =
                BatchInsertBuilder.of(TAuditUser.class, List.of()).build();
        assertThatThrownBy(() -> executor.executeBatchInsert(empty, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> executor.executeBatchInsert(empty, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(executor.executeBatchInsert(empty, 2)).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void stopsOnChunkFailureAndRejectsMismatchedCounts() {
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, users(5)).build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenReturn(new int[] {1, 1})
                .thenThrow(new DataIntegrityViolationException("failure"));
        assertThatThrownBy(() -> executor.executeBatchInsert(spec, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(jdbc, times(2)).batchUpdate(anyString(), any(SqlParameterSource[].class));

        reset(jdbc);
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenReturn(new int[] {1});
        assertThatThrownBy(() -> executor.executeBatchInsert(spec, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 counts for 2 rows");
        verify(jdbc, times(1)).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void batchResolvesAccessorsForEachRuntimeTypeAcrossChunks() {
        List<TAuditUser> rows = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String name = "user-" + i;
            rows.add(i % 2 == 0 ? new TAuditUser(name, "ACTIVE") : new OverridingUser(name));
            expected.add(i % 2 == 0 ? name : "override-" + name);
        }
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, rows).build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenReturn(new int[10]);

        assertThat(executor.executeBatchInsert(spec, 10)).hasSize(20).containsOnly(0);
        ArgumentCaptor<SqlParameterSource[]> batches = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(2)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues().stream()
                        .flatMap(batch -> java.util.Arrays.stream(batch))
                        .map(params -> params.getValue("username")))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void accessorFailureInLaterChunkStopsExecution() {
        List<TAuditUser> rows = users(16);
        rows.set(8, new OverridingUser("fail"));
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, rows).build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenReturn(new int[8]);

        assertThatThrownBy(() -> executor.executeBatchInsert(spec, 8))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("getter failed");
        verify(jdbc, times(1)).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void batchInvokesGettersInEveryRowIncludingAcrossChunks() {
        List<CountingUser> rows = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            rows.add(new CountingUser("user-" + i));
        }
        BatchInsertSpec<TAuditUser> spec = BatchInsertBuilder.of(TAuditUser.class, new ArrayList<TAuditUser>(rows))
                .build();
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenReturn(new int[8]);

        assertThat(executor.executeBatchInsert(spec, 8)).hasSize(16).containsOnly(0);
        assertThat(rows.get(0).getterCalls).isEqualTo(2);
        assertThat(rows.subList(1, rows.size()))
                .allSatisfy(row -> assertThat(row.getterCalls).isEqualTo(1));
    }

    @Test
    void customMetadataAndCacheReplacementRetainGetPropertySemantics() {
        JdbcDslCacheManager cache = new JdbcDslCacheManager();
        executor = new JdbcDslExecutor(jdbc, new H2Dialect(), cache);
        BeanMappingMeta first = mock(BeanMappingMeta.class);
        BeanMappingMeta second = mock(BeanMappingMeta.class);
        PropertyAccessor accessor = mock(PropertyAccessor.class);
        when(accessor.get(any())).thenReturn("accessor value");
        when(first.getPropertyAccessor("name")).thenReturn(accessor);
        when(second.getPropertyAccessor("name")).thenReturn(accessor);
        when(first.getProperty(any(), anyString())).thenReturn("first");
        when(second.getProperty(any(), anyString())).thenReturn("second");
        cache.getBeanMappingCache().put(SimpleRow.class, first);
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenAnswer(invocation -> {
            cache.getBeanMappingCache().put(SimpleRow.class, second);
            return new int[8];
        });
        BatchInsertSpec<SimpleRow> spec = BatchInsertBuilder.of(
                        SimpleRow.class,
                        java.util.stream.IntStream.range(0, 16)
                                .mapToObj(i -> new SimpleRow())
                                .toList())
                .build();

        assertThat(executor.executeBatchInsert(spec, 8)).hasSize(16).containsOnly(0);
        ArgumentCaptor<SqlParameterSource[]> batches = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(2)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues().get(0)[0].getValue("name")).isEqualTo("first");
        assertThat(batches.getAllValues().get(1)[0].getValue("name")).isEqualTo("second");
        verify(accessor, never()).get(any());
    }

    public static class SimpleRow {
        private String name;
    }

    public static class CountingUser extends TAuditUser {
        private int getterCalls;

        public CountingUser() {}

        CountingUser(String name) {
            super(name, "ACTIVE");
        }

        @Override
        public String getUsername() {
            getterCalls++;
            return super.getUsername();
        }
    }

    public static class OverridingUser extends TAuditUser {
        public OverridingUser() {}

        OverridingUser(String name) {
            super(name, "ACTIVE");
        }

        @Override
        public String getUsername() {
            if ("fail".equals(super.getUsername())) {
                throw new IllegalStateException("getter failed");
            }
            return "override-" + super.getUsername();
        }
    }

    private static List<TAuditUser> users(int count) {
        List<TAuditUser> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(new TAuditUser("user-" + i, "ACTIVE"));
        }
        return users;
    }
}
