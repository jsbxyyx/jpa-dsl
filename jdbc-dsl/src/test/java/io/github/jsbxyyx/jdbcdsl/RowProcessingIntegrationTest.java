package io.github.jsbxyyx.jdbcdsl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RowProcessingIntegrationTest {
    private HikariDataSource dataSource;
    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:rows_" + UUID.randomUUID().toString().replace("-", ""));
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE each_row (id BIGINT PRIMARY KEY, name VARCHAR(30), deleted INTEGER)");
        jdbc.update("INSERT INTO each_row (id, name, deleted) VALUES (1, 'one', 0), (2, 'two', 1), (3, 'three', 0)");
        jdbc.setFetchSize(3);
        executor = new JdbcDslExecutor(new NamedParameterJdbcTemplate(jdbc), new H2Dialect());
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void rawQueryConsumesScalarsRecordsAndEmptyResults() {
        List<Long> scalar = new ArrayList<>();
        executor.queryEach(
                "SELECT id FROM each_row WHERE id > :min ORDER BY id", Map.of("min", 0), Long.class, scalar::add);
        assertThat(scalar).containsExactly(1L, 2L, 3L);

        List<RowRecord> records = new ArrayList<>();
        executor.queryEach("SELECT id, name FROM each_row ORDER BY id", Map.of(), RowRecord.class, records::add);
        assertThat(records)
                .containsExactly(new RowRecord(1L, "one"), new RowRecord(2L, "two"), new RowRecord(3L, "three"));

        List<Long> nullable = new ArrayList<>();
        executor.queryEach("SELECT CAST(NULL AS BIGINT)", Map.of(), Long.class, nullable::add);
        assertThat(nullable).containsExactly((Long) null);
        executor.queryEach("SELECT id FROM each_row WHERE id < 0", Map.of(), Long.class, nullable::add);
        assertThat(nullable).hasSize(1);
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void dslUsesNormalFilterAndMapperCanSeeRowNumbers() {
        SelectSpec<Row, Row> spec =
                SelectBuilder.from(Row.class).orderBy(JSort.byAsc(Row::getId)).mapToEntity();
        List<Row> rows = new ArrayList<>();
        executor.selectEach(spec, rows::add);
        assertThat(rows).extracting(Row::getName).containsExactly("one", "three");

        List<String> mapped = new ArrayList<>();
        executor.selectEachMapped(spec, (rs, rowNum) -> rowNum + ":" + rs.getLong(1), mapped::add);
        assertThat(mapped).containsExactly("0:1", "1:3");
    }

    @Test
    void pagedAndSortedUnpagedCallbacksMatchSelect() {
        SelectSpec<Row, Row> spec = SelectBuilder.from(Row.class).mapToEntity();
        JSort<Row> sort = JSort.byDesc(Row::getId);
        JPageable<Row> page = JPageable.of(1, 1, sort);
        List<Row> rows = new ArrayList<>();
        executor.selectEach(spec, page, rows::add);
        assertThat(rows).extracting(Row::getName).containsExactly("one");
        assertThat(executor.select(spec, page)).extracting(Row::getName).containsExactly("one");

        List<Long> ids = new ArrayList<>();
        executor.selectEachMapped(spec, JPageable.unpaged(sort), (rs, rowNum) -> rs.getLong(1), ids::add);
        assertThat(ids).containsExactly(3L, 1L);
    }

    @Test
    void sliceFindsNextWithoutCountAndRespectsSortAndLogicalDelete() {
        NamedParameterJdbcTemplate jdbc = spy(new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource)));
        executor = new JdbcDslExecutor(jdbc, new H2Dialect());
        SelectSpec<Row, Row> spec = SelectBuilder.from(Row.class).mapToEntity();
        JSort<Row> sort = JSort.byDesc(Row::getId);

        Slice<Row> first = executor.selectSlice(spec, JPageable.of(0, 1, sort));
        Slice<Row> last = executor.selectSlice(spec, JPageable.of(1, 1, sort));
        Slice<Row> empty = executor.selectSlice(spec, JPageable.of(2, 1, sort));
        Slice<Row> exact = executor.selectSlice(spec, JPageable.of(0, 2, sort));
        assertThat(first.getContent()).extracting(Row::getId).containsExactly(3L);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.getNumber()).isZero();
        assertThat(last.getContent()).extracting(Row::getId).containsExactly(1L);
        assertThat(last.hasNext()).isFalse();
        assertThat(empty).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(exact.getContent()).extracting(Row::getId).containsExactly(3L, 1L);
        assertThat(exact.hasNext()).isFalse();
        verify(jdbc, times(4)).query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Row>>any());
        verify(jdbc, never()).queryForObject(anyString(), anyMap(), eq(Long.class));
    }

    @Test
    void sliceRejectsUnpagedAndOverflowingSizes() {
        SelectSpec<Row, Row> spec = SelectBuilder.from(Row.class).mapToEntity();
        assertThatThrownBy(() -> executor.selectSlice(spec, JPageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paged");
        assertThatThrownBy(() -> executor.selectSlice(spec, JPageable.ofSize(Integer.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void callbackFailureStopsProcessingAndClosesResources() throws SQLException {
        AtomicReference<ResultSet> seen = new AtomicReference<>();
        List<Long> visited = new ArrayList<>();
        assertThatThrownBy(() -> executor.queryEachMapped(
                        "SELECT id FROM each_row ORDER BY id",
                        Map.of(),
                        (rs, rowNum) -> {
                            seen.set(rs);
                            return rs.getLong(1);
                        },
                        id -> {
                            visited.add(id);
                            if (id == 2L) {
                                throw new IllegalStateException("stop");
                            }
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stop");
        assertThat(visited).containsExactly(1L, 2L);
        assertThat(seen.get().isClosed()).isTrue();
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void mapperSqlFailureIsTranslatedAndResourcesAreClosed() throws SQLException {
        AtomicReference<ResultSet> seen = new AtomicReference<>();
        RowMapper<Long> mapper = (rs, rowNum) -> {
            seen.set(rs);
            throw new SQLException("mapper failed");
        };
        assertThatThrownBy(() -> executor.queryEachMapped("SELECT id FROM each_row", Map.of(), mapper, value -> {}))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseMessage("mapper failed");
        assertThat(seen.get().isClosed()).isTrue();
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void nullMapperAndConsumerAreRejected() {
        assertThatThrownBy(() -> executor.queryEachMapped("SELECT 1", Map.of(), null, value -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper");
        assertThatThrownBy(() -> executor.queryEach("SELECT 1", Map.of(), Long.class, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("consumer");
    }

    public record RowRecord(Long id, String name) {}

    @Table(name = "each_row")
    public static class Row {
        @Id
        private Long id;

        private String name;

        @LogicalDelete(deletedValue = "1", normalValue = "0")
        private Integer deleted;

        public Row() {}

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

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }
}
