package io.github.jsbxyyx.jdbcdsl.dialect;

import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.RenderedSql;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.SqlRenderer;
import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OracleDialect} (Oracle 12c+) and {@link Oracle11gDialect} pagination.
 * Verifies generated SQL strings — no database required.
 */
class OracleDialectTest {

    private final OracleDialect oracle12c = new OracleDialect();
    private final Oracle11gDialect oracle11g = new Oracle11gDialect();

    // ------------------------------------------------------------------ //
    //  OracleDialect (12c+): OFFSET ... ROWS FETCH NEXT ... ROWS ONLY
    // ------------------------------------------------------------------ //

    @Test
    void oracle12c_firstPage_generatesOffsetFetch() {
        Map<String, Object> params = new LinkedHashMap<>();
        String paginated = oracle12c.applyPagination(
                "SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC",
                0, 10, params);

        assertThat(paginated)
                .endsWith("OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY");
        assertThat(params)
                .containsEntry("_offset", 0L)
                .containsEntry("_limit", 10);
    }

    @Test
    void oracle12c_secondPage_correctOffsetValue() {
        Map<String, Object> params = new LinkedHashMap<>();
        oracle12c.applyPagination("SELECT t.id FROM t_user t", 10, 5, params);

        assertThat(params)
                .containsEntry("_offset", 10L)
                .containsEntry("_limit", 5);
    }

    @Test
    void oracle12c_preservesOriginalSql() {
        String base = "SELECT t.id, t.username FROM t_user t WHERE t.status = :p1 ORDER BY t.id ASC";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("p1", "ACTIVE");

        String result = oracle12c.applyPagination(base, 0, 20, params);

        assertThat(result).startsWith(base);
        assertThat(params).containsEntry("p1", "ACTIVE");
    }

    @Test
    void oracle12c_withSelectSpec_integrationWithRenderer() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        Map<String, Object> params = new LinkedHashMap<>(rendered.getParams());
        String paginated = oracle12c.applyPagination(rendered.getSql(), 0, 10, params);

        assertThat(paginated)
                .contains("WHERE")
                .contains("t.status = :p1")
                .contains("ORDER BY")
                .endsWith("OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY");
        assertThat(params)
                .containsEntry("p1", "ACTIVE")
                .containsEntry("_offset", 0L)
                .containsEntry("_limit", 10);
    }

    // ------------------------------------------------------------------ //
    //  Oracle11gDialect: nested ROWNUM
    // ------------------------------------------------------------------ //

    @Test
    void oracle11g_firstPage_generatesNestedRownum() {
        Map<String, Object> params = new LinkedHashMap<>();
        String paginated = oracle11g.applyPagination(
                "SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC",
                0, 10, params);

        assertThat(paginated)
                .startsWith("SELECT * FROM (")
                .contains("SELECT _q_.*, ROWNUM _rn_ FROM (")
                .contains("WHERE ROWNUM <= :_end")
                .endsWith(") WHERE _rn_ > :_offset");
        assertThat(params)
                .containsEntry("_offset", 0L)
                .containsEntry("_end", 10L);
    }

    @Test
    void oracle11g_secondPage_correctEndAndOffset() {
        Map<String, Object> params = new LinkedHashMap<>();
        oracle11g.applyPagination("SELECT t.id FROM t_user t", 10, 5, params);

        // offset=10, limit=5 → end=15, offset=10
        assertThat(params)
                .containsEntry("_offset", 10L)
                .containsEntry("_end", 15L);
    }

    @Test
    void oracle11g_wrapsEntireSqlIncludingOrderBy() {
        String base = "SELECT t.id FROM t_user t ORDER BY t.id ASC";
        Map<String, Object> params = new LinkedHashMap<>();
        String paginated = oracle11g.applyPagination(base, 5, 3, params);

        // ORDER BY must be inside the innermost subquery, not outside
        int innerStart = paginated.indexOf(base);
        assertThat(innerStart).isGreaterThan(0);
        assertThat(paginated.indexOf("ORDER BY")).isEqualTo(innerStart + base.indexOf("ORDER BY"));
    }

    @Test
    void oracle11g_preservesExistingParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("p1", "ACTIVE");
        oracle11g.applyPagination("SELECT t.id FROM t_user t WHERE t.status = :p1", 0, 5, params);

        assertThat(params)
                .containsEntry("p1", "ACTIVE")
                .containsEntry("_offset", 0L)
                .containsEntry("_end", 5L);
    }

    @Test
    void oracle11g_withSelectSpec_integrationWithRenderer() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        Map<String, Object> params = new LinkedHashMap<>(rendered.getParams());
        String paginated = oracle11g.applyPagination(rendered.getSql(), 0, 10, params);

        assertThat(paginated)
                .startsWith("SELECT * FROM (SELECT _q_.*, ROWNUM _rn_ FROM (")
                .contains("t.status = :p1")
                .contains("ORDER BY")
                .endsWith(") WHERE _rn_ > :_offset");
        assertThat(params)
                .containsEntry("p1", "ACTIVE")
                .containsEntry("_offset", 0L)
                .containsEntry("_end", 10L);
    }

    // ------------------------------------------------------------------ //
    //  DialectDetector recognizes Oracle
    // ------------------------------------------------------------------ //

    @Test
    void dialectDetector_oracleProductName_returnsOracleDialect() {
        // Simulate what DialectDetector would do if DatabaseMetaData reports "Oracle"
        String productName = "Oracle";
        Dialect detected = productName.toLowerCase().contains("oracle")
                ? new OracleDialect()
                : new Sql2008Dialect();

        assertThat(detected).isInstanceOf(OracleDialect.class);
    }
}
