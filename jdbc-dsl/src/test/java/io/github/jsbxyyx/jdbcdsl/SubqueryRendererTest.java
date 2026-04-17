package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.avg;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static org.assertj.core.api.Assertions.assertThat;

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
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getId, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        assertThat(rendered.getSql())
                .contains("t.id IN (SELECT")
                .contains("FROM t_order o")
                .contains("o.user_id")
                .contains("WHERE")
                .contains("o.status = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "PAID");
    }

    @Test
    void renderSelect_notInSubquery_generatesCorrectSql() {
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.notIn(TUser::getId, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        assertThat(rendered.getSql()).contains("t.id NOT IN (SELECT");
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
        assertThat(rendered.getSql())
                .contains("EXISTS (SELECT")
                .contains("FROM t_order o");
        assertThat(rendered.getParams()).containsEntry("p1", "PAID");
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
        assertThat(rendered.getSql()).contains("NOT EXISTS (SELECT");
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_scalarSubquery_gt_generatesCorrectSql() {
        // SELECT AVG(age) FROM t_user — inner uses the default alias "t"
        SelectSpec<TUser, TUser> inner = SelectBuilder.from(TUser.class)
                .select(avg(TUser::getAge).as("avgAge"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.gt((SFunction<TUser, Integer>) TUser::getAge, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        assertThat(rendered.getSql())
                .contains("t.age > (SELECT")
                .contains("AVG(t.age)")
                .contains("FROM t_user");
    }

    @Test
    void renderSelect_scalarSubquery_eq_generatesCorrectSql() {
        SelectSpec<TUser, TUser> inner = SelectBuilder.from(TUser.class)
                .select(SqlFunctions.max(TUser::getAge).as("maxAge"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq((SFunction<TUser, Integer>) TUser::getAge, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        assertThat(rendered.getSql()).contains("t.age = (SELECT");
    }

    // ------------------------------------------------------------------ //
    //  Subquery with WHERE parameters — outer + inner params must not collide
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_subqueryParams_numberedSequentially() {
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")
                        .in(TUser::getId, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        // Outer param is :p1, inner param is :p2 — no collision
        assertThat(rendered.getParams())
                .containsEntry("p1", "ACTIVE")
                .containsEntry("p2", "PAID");
        assertThat(rendered.getSql())
                .contains("t.status = :p1")
                .contains("o.status = :p2");
    }

    // ------------------------------------------------------------------ //
    //  ORDER BY omitted inside subquery
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_subquery_orderByOmitted() {
        // Even if the inner spec has an orderBy, it must NOT appear in the subquery SQL
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .orderBy(JSort.by(JOrder.desc(TOrder::getAmount)))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getId, inner))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        // ORDER BY must appear only AFTER the closing paren of the subquery (i.e., not at all here)
        String sql = rendered.getSql();
        int inPos = sql.indexOf("IN (");
        int closePos = sql.indexOf(")", inPos);
        String subquerySql = sql.substring(inPos, closePos + 1);
        assertThat(subquerySql).doesNotContain("ORDER BY");
    }
}
