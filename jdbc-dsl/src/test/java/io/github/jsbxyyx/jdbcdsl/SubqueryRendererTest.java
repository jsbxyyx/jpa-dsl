package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.max;

/**
 * Unit tests for subquery rendering in {@link SqlRenderer}.
 * Verifies generated SQL strings and parameter bindings — no database required.
 */
class SubqueryRendererTest {

    // ------------------------------------------------------------------ //
    //  IN subquery
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_inSubquery_generatesCorrectSql() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.id IN (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM t_order o"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("o.user_id"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("o.status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAID", rendered.getParams().get("p1"));
    }

    @Test
    void renderSelect_notInSubquery_generatesCorrectSql() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.notInSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.id NOT IN (SELECT"));
    }

    // ------------------------------------------------------------------ //
    //  EXISTS / NOT EXISTS
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_exists_generatesCorrectSql() {
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.exists(inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("EXISTS (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM t_order o"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAID", rendered.getParams().get("p1"));
    }

    @Test
    void renderSelect_notExists_generatesCorrectSql() {
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getId, "o"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.notExists(inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("NOT EXISTS (SELECT"));
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_scalarSubquery_gt_generatesCorrectSql() {
        // SELECT AVG(age) FROM t_user — inner uses the default alias "t"
        SelectSpec<TUser, Integer> inner = SelectBuilder.from(TUser.class)
                .select(max(TUser::getAge).as("maxAge"))
                .mapTo(Integer.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.gtScalar(col(TUser::getAge), scalar(inner)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.age > (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("MAX(t.age)"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM t_user"));
    }

    @Test
    void renderSelect_scalarSubquery_eq_generatesCorrectSql() {
        SelectSpec<TUser, Integer> inner = SelectBuilder.from(TUser.class)
                .select(SqlFunctions.max(TUser::getAge).as("maxAge"))
                .mapTo(Integer.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eqScalar(col(TUser::getAge), scalar(inner)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.age = (SELECT"));
    }

    // ------------------------------------------------------------------ //
    //  Subquery with WHERE parameters — outer + inner params must not collide
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_subqueryParams_numberedSequentially() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        // Outer param is :p1, inner param is :p2 — no collision
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAID", rendered.getParams().get("p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("o.status = :p2"));
    }

    // ------------------------------------------------------------------ //
    //  ORDER BY omitted inside subquery
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_subquery_orderByOmitted() {
        // Even if the inner spec has an orderBy, it must NOT appear in the subquery SQL
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .orderBy(JSort.by(JOrder.desc(TOrder::getAmount)))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        // ORDER BY must appear only AFTER the closing paren of the subquery (i.e., not at all here)
        String sql = rendered.getSql();
        int inPos = sql.indexOf("IN (");
        int closePos = sql.indexOf(")", inPos);
        String subquerySql = sql.substring(inPos, closePos + 1);
        org.junit.jupiter.api.Assertions.assertFalse(subquerySql.contains("ORDER BY"));
    }
}
