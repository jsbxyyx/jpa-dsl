package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserOrderCountDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserRnDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for window-function and scalar-subquery rendering in {@link SqlRenderer}.
 * Verifies generated SQL strings and parameter bindings — no database required.
 */
class WindowFunctionRendererTest {

    // ------------------------------------------------------------------ //
    //  ROW_NUMBER
    // ------------------------------------------------------------------ //

    @Test
    void rowNumber_emptyOver_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rowNumber().over().as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("ROW_NUMBER() OVER ()")
                .contains("AS rn");
    }

    @Test
    void rowNumber_overOrderBy_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rowNumber().over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("ROW_NUMBER() OVER (ORDER BY t.age ASC)")
                .contains("AS rn");
    }

    @Test
    void rowNumber_overPartitionByAndOrderBy_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rowNumber().over(w -> w
                                .partitionBy(TUser::getStatus)
                                .orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("ROW_NUMBER() OVER (PARTITION BY t.status ORDER BY t.age ASC)")
                .contains("AS rn");
    }

    // ------------------------------------------------------------------ //
    //  RANK / DENSE_RANK
    // ------------------------------------------------------------------ //

    @Test
    void rank_overOrderBy_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rank().over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("RANK() OVER (ORDER BY t.age ASC)");
    }

    @Test
    void denseRank_overPartitionBy_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        denseRank().over(w -> w
                                .partitionBy(TUser::getStatus)
                                .orderBy(JOrder.desc(TUser::getAge))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("DENSE_RANK() OVER (PARTITION BY t.status ORDER BY t.age DESC)");
    }

    // ------------------------------------------------------------------ //
    //  NTILE
    // ------------------------------------------------------------------ //

    @Test
    void ntile_overOrderBy_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        ntile(4).over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NTILE(4) OVER (ORDER BY t.age ASC)");
    }

    // ------------------------------------------------------------------ //
    //  LAG / LEAD
    // ------------------------------------------------------------------ //

    @Test
    void lag_withOffset_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        lag(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LAG(t.age, 1) OVER (ORDER BY t.id ASC)");
    }

    @Test
    void lead_withOffset_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        lead(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LEAD(t.age, 1) OVER (ORDER BY t.id ASC)");
    }

    // ------------------------------------------------------------------ //
    //  Aggregate OVER (window aggregate)
    // ------------------------------------------------------------------ //

    @Test
    void sum_overPartitionBy_rendersCorrectly() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getOrderNo, "o"),
                        sum(TOrder::getAmount).over(w -> w
                                .partitionBy(TOrder::getUserId)
                                .orderBy(JOrder.asc(TOrder::getId))).as("runningTotal"))
                .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("SUM(o.amount) OVER (PARTITION BY o.user_id ORDER BY o.id ASC)")
                .contains("AS runningTotal");
    }

    @Test
    void count_overEmptyWindow_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        count(TUser::getId).over().as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("COUNT(t.id) OVER ()")
                .contains("AS rn");
    }

    // ------------------------------------------------------------------ //
    //  FIRST_VALUE / LAST_VALUE
    // ------------------------------------------------------------------ //

    @Test
    void firstValue_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        firstValue(TUser::getAge).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("FIRST_VALUE(t.age) OVER (ORDER BY t.id ASC)");
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery in SELECT clause
    // ------------------------------------------------------------------ //

    @Test
    void scalarSubquery_inSelectClause_rendersCorrectly() {
        SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
                .select(countStar())
                .where(w -> w.eq(TOrder::getUserId, SqlFunctions.col(TUser::getId)))
                .mapToEntity();

        SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        SqlFunctions.<Long>subquery(countSpec).as("orderCount"))
                .mapTo(UserOrderCountDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();
        assertThat(sql)
                .contains("(SELECT COUNT(*)")
                .contains("FROM t_order o")
                .contains("AS orderCount");
    }

    @Test
    void scalarSubquery_parametersDontCollideWithOuter_rendersCorrectly() {
        // Outer param at :p1, inner adds :p2
        SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
                .select(countStar())
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        SqlFunctions.<Long>subquery(countSpec).as("orderCount"))
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserOrderCountDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        // SELECT clause is rendered before WHERE, so the scalar subquery's param is :p1
        // and the outer WHERE param is :p2.
        assertThat(rendered.getParams())
                .containsEntry("p1", "PAID")
                .containsEntry("p2", "ACTIVE");
        assertThat(rendered.getSql())
                .contains("o.status = :p1")
                .contains("t.status = :p2");
    }

    // ------------------------------------------------------------------ //
    //  ORDER BY with NULLS FIRST/LAST in window
    // ------------------------------------------------------------------ //

    @Test
    void windowOrderBy_nullsFirst_rendersCorrectly() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rowNumber().over(w -> w
                                .orderBy(JOrder.asc(TUser::getAge).nullsFirst())).as("rn"))
                .mapTo(UserRnDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("ROW_NUMBER() OVER (ORDER BY t.age ASC NULLS FIRST)");
    }
}
