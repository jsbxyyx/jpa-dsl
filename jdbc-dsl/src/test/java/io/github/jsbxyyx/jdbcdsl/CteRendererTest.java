package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar;

/**
 * Unit tests for CTE (WITH clause) rendering in {@link SqlRenderer}.
 * Verifies generated SQL strings and parameter bindings — no database required.
 */
class CteRendererTest {

    // ------------------------------------------------------------------ //
    //  Basic WITH clause
    // ------------------------------------------------------------------ //

    @Test
    void withCte_singleCte_prependsWithClause() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("WITH active_users AS ("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM t_user"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains(") SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", rendered.getParams().get("p1"));
    }

    @Test
    void withCte_fromCteUsesCteName_notEntityTable() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class).mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        // The outer FROM must reference the CTE name, not t_user
        // Pattern: ") SELECT ... FROM active_users t"
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM active_users t"));
        // The CTE body is allowed to reference t_user (inside the WITH clause)
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM t_user"));
    }

    @Test
    void withCte_twoCtes_renderedAsCteList() {
        SelectSpec<TUser, TUser> activeBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TOrder, TOrder> paidBody = SelectBuilder.from(TOrder.class, "o")
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", activeBody)
                .withCte("paid_orders", paidBody)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("WITH "));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("active_users AS ("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("paid_orders AS ("));
        // Both CTEs appear before the outer SELECT
        int withPos = sql.indexOf("WITH ");
        int outerSelectPos = sql.lastIndexOf("SELECT");
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) withPos).compareTo(outerSelectPos) < 0);
    }

    @Test
    void withCte_cteBodyParams_mergedWithOuterParams() {
        // CTE body has a param (:p1), outer query WHERE adds another (:p2)
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.gt(TUser::getAge, 20))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(20, rendered.getParams().get("p2"));
    }

    @Test
    void withCte_orderByOmittedInCteBody() {
        // ORDER BY inside a CTE body must be suppressed
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.by(JOrder.asc(TUser::getAge)))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        // The outer query has no ORDER BY either, so ORDER BY must not appear anywhere
        int cteStart = sql.indexOf("WITH ");
        int cteBodyEnd = sql.indexOf(") SELECT");
        String cteBodySql = sql.substring(cteStart, cteBodyEnd);
        org.junit.jupiter.api.Assertions.assertFalse(cteBodySql.contains("ORDER BY"));
    }

    @Test
    void withCte_outerOrderBy_preservedAfterCte() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class).mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("all_users", TUser.class)
                .withCte("all_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        org.junit.jupiter.api.Assertions.assertTrue(sql.endsWith("ORDER BY t.username ASC"));
    }

    @Test
    void withCte_customAlias_usedInSelectAndFrom() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class).mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class, "u")
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        // The outer FROM alias must be "u", not "t"
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM active_users u"));
    }

    @Test
    void withCte_noCtes_noWithClause() {
        // A plain SelectBuilder.from() without withCte must not emit a WITH clause
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("WITH "));
    }

    // ------------------------------------------------------------------ //
    //  CTE with aggregate in body
    // ------------------------------------------------------------------ //

    @Test
    void withCte_bodyWithAggregate_rendersCorrectly() {
        // CTE: SELECT COUNT(*) AS total FROM t_order o WHERE status = 'PAID'
        SelectSpec<TOrder, TOrder> cteBody = SelectBuilder.from(TOrder.class, "o")
                .select(countStar().as("total"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TOrder, TOrder> spec = SelectBuilder.fromCte("paid_counts", TOrder.class, "o")
                .withCte("paid_counts", cteBody)
                .select(col(TOrder::getOrderNo, "o"))
                .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("paid_counts AS ("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("COUNT(*)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM paid_counts o"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAID", rendered.getParams().get("p1"));
    }
}
