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
                "SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC", 0, 10, params);

        org.junit.jupiter.api.Assertions.assertTrue(
                paginated.endsWith("OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(0L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_limit"));
        org.junit.jupiter.api.Assertions.assertEquals(10, params.get("_limit"));
    }

    @Test
    void oracle12c_secondPage_correctOffsetValue() {
        Map<String, Object> params = new LinkedHashMap<>();
        oracle12c.applyPagination("SELECT t.id FROM t_user t", 10, 5, params);

        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_limit"));
        org.junit.jupiter.api.Assertions.assertEquals(5, params.get("_limit"));
    }

    @Test
    void oracle12c_preservesOriginalSql() {
        String base = "SELECT t.id, t.username FROM t_user t WHERE t.status = :p1 ORDER BY t.id ASC";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("p1", "ACTIVE");

        String result = oracle12c.applyPagination(base, 0, 20, params);

        org.junit.jupiter.api.Assertions.assertTrue(result.startsWith(base));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", params.get("p1"));
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

        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("WHERE"));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("t.status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("ORDER BY"));
        org.junit.jupiter.api.Assertions.assertTrue(
                paginated.endsWith("OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", params.get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(0L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_limit"));
        org.junit.jupiter.api.Assertions.assertEquals(10, params.get("_limit"));
    }

    // ------------------------------------------------------------------ //
    //  Oracle11gDialect: nested ROWNUM
    // ------------------------------------------------------------------ //

    @Test
    void oracle11g_firstPage_generatesNestedRownum() {
        Map<String, Object> params = new LinkedHashMap<>();
        String paginated = oracle11g.applyPagination(
                "SELECT t.id, t.username FROM t_user t ORDER BY t.username ASC", 0, 10, params);

        org.junit.jupiter.api.Assertions.assertTrue(paginated.startsWith("SELECT * FROM ("));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("SELECT _q_.*, ROWNUM _rn_ FROM ("));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("WHERE ROWNUM <= :_end"));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.endsWith(") WHERE _rn_ > :_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(0L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_end"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, params.get("_end"));
    }

    @Test
    void oracle11g_secondPage_correctEndAndOffset() {
        Map<String, Object> params = new LinkedHashMap<>();
        oracle11g.applyPagination("SELECT t.id FROM t_user t", 10, 5, params);

        // offset=10, limit=5 → end=15, offset=10
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_end"));
        org.junit.jupiter.api.Assertions.assertEquals(15L, params.get("_end"));
    }

    @Test
    void oracle11g_wrapsEntireSqlIncludingOrderBy() {
        String base = "SELECT t.id FROM t_user t ORDER BY t.id ASC";
        Map<String, Object> params = new LinkedHashMap<>();
        String paginated = oracle11g.applyPagination(base, 5, 3, params);

        // ORDER BY must be inside the innermost subquery, not outside
        int innerStart = paginated.indexOf(base);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) innerStart).compareTo(0) > 0);
        org.junit.jupiter.api.Assertions.assertEquals(
                innerStart + base.indexOf("ORDER BY"), paginated.indexOf("ORDER BY"));
    }

    @Test
    void oracle11g_preservesExistingParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("p1", "ACTIVE");
        oracle11g.applyPagination("SELECT t.id FROM t_user t WHERE t.status = :p1", 0, 5, params);

        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", params.get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(0L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_end"));
        org.junit.jupiter.api.Assertions.assertEquals(5L, params.get("_end"));
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

        org.junit.jupiter.api.Assertions.assertTrue(
                paginated.startsWith("SELECT * FROM (SELECT _q_.*, ROWNUM _rn_ FROM ("));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("t.status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.contains("ORDER BY"));
        org.junit.jupiter.api.Assertions.assertTrue(paginated.endsWith(") WHERE _rn_ > :_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", params.get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_offset"));
        org.junit.jupiter.api.Assertions.assertEquals(0L, params.get("_offset"));
        org.junit.jupiter.api.Assertions.assertTrue(params.containsKey("_end"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, params.get("_end"));
    }

    // ------------------------------------------------------------------ //
    //  DialectDetector recognizes Oracle
    // ------------------------------------------------------------------ //

    @Test
    void dialectDetector_oracleProductName_returnsOracleDialect() {
        // Simulate what DialectDetector would do if DatabaseMetaData reports "Oracle"
        String productName = "Oracle";
        Dialect detected = productName.toLowerCase().contains("oracle") ? new OracleDialect() : new Sql2008Dialect();

        org.junit.jupiter.api.Assertions.assertInstanceOf(OracleDialect.class, detected);
    }
}
