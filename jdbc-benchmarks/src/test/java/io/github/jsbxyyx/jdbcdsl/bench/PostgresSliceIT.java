package io.github.jsbxyyx.jdbcdsl.bench;

import io.github.jsbxyyx.jdbcdsl.JPageable;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.Slice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "JDBC_BENCH_ALLOW_EXTERNAL", matches = "true")
class PostgresSliceIT {
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
    void slicesRespectOffsetsSortAndEndOfResults() {
        SelectSpec<BenchmarkRow, BenchmarkRow> spec =
                SelectBuilder.from(BenchmarkRow.class).mapToEntity();
        JSort<BenchmarkRow> sort = JSort.byDesc(BenchmarkRow::getId);
        Slice<BenchmarkRow> first = db.dsl.selectSlice(spec, JPageable.of(0, 100, sort));
        Slice<BenchmarkRow> last = db.dsl.selectSlice(spec, JPageable.of(99, 100, sort));
        Slice<BenchmarkRow> empty = db.dsl.selectSlice(spec, JPageable.of(100, 100, sort));

        assertEquals(100, first.getNumberOfElements());
        assertEquals(10_000L, first.getContent().get(0).getId());
        assertEquals(9_901L, first.getContent().get(99).getId());
        assertTrue(first.hasNext());
        assertEquals(100, last.getNumberOfElements());
        assertEquals(100L, last.getContent().get(0).getId());
        assertEquals(1L, last.getContent().get(99).getId());
        assertFalse(last.hasNext());
        assertTrue(empty.isEmpty());
        assertFalse(empty.hasNext());
    }
}
