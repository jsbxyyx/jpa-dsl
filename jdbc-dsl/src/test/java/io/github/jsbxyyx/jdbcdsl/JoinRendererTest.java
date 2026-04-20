package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserOrderDto;
import io.github.jsbxyyx.jdbcdsl.entity.TAuditUser;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JOIN clause rendering in {@link SqlRenderer}.
 * Verifies generated SQL strings — no database required.
 *
 * <p>Covers three scenarios from the JOIN-enhancement feature:
 * <ol>
 *   <li>Multi-table chained {@code .join().join()} (item 7)</li>
 *   <li>Cross-table field projection via {@code col(fn, alias)} (item 8)</li>
 *   <li>Self-join with explicit alias management (item 9)</li>
 * </ol>
 */
class JoinRendererTest {

    // ------------------------------------------------------------------ //
    //  Item 7: Multi-table chained JOIN
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_multiJoinChain_generatesAllJoinClauses() {
        // t_user u  INNER JOIN t_order o ON u.id = o.user_id
        //           LEFT  JOIN t_audit_user au ON u.id = au.id
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(TUser::getId, TUser::getUsername)
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .join(TAuditUser.class, "au", JoinType.LEFT,
                        on -> on.eq(TUser::getId, "u", TAuditUser::getId, "au"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql)
                .contains("FROM t_user u")
                .contains("INNER JOIN t_order o ON u.id = o.user_id")
                .contains("LEFT JOIN t_audit_user au ON u.id = au.id");
    }

    @Test
    void renderCount_multiJoinChain_usesCountDistinct() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(TUser::getId, TUser::getUsername)
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .join(TAuditUser.class, "au", JoinType.LEFT,
                        on -> on.eq(TUser::getId, "u", TAuditUser::getId, "au"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        String sql = rendered.getSql();

        // With JOINs, must use COUNT(DISTINCT pk) to avoid duplicate rows
        assertThat(sql)
                .contains("COUNT(DISTINCT u.id)")
                .contains("INNER JOIN t_order o")
                .contains("LEFT JOIN t_audit_user au");
    }

    // ------------------------------------------------------------------ //
    //  Item 8: Cross-table field projection
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_crossTableProjection_generatesAliasedColumns() {
        // SELECT u.username AS username, o.order_no AS orderNo, o.amount AS amount
        // FROM t_user u INNER JOIN t_order o ON u.id = o.user_id
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(
                        col(TUser::getUsername, "u"),
                        col(TOrder::getOrderNo, "o"),
                        col(TOrder::getAmount, "o"),
                        col(TOrder::getStatus, "o").as("orderStatus")
                )
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .mapTo(UserOrderDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql)
                .contains("u.username AS username")
                .contains("o.order_no AS orderNo")
                .contains("o.amount AS amount")
                .contains("o.status AS orderStatus")
                .contains("FROM t_user u")
                .contains("INNER JOIN t_order o ON u.id = o.user_id");
    }

    @Test
    void renderSelect_crossTableWhere_filterOnJoinedColumn() {
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(col(TUser::getUsername, "u"), col(TOrder::getOrderNo, "o"))
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "o", "PAID"))
                .mapTo(UserOrderDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("o.status = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "PAID");
    }

    // ------------------------------------------------------------------ //
    //  Item 9: Self-join alias management
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_selfJoin_distinctAliasesInOnClause() {
        // Self-join: find users sharing the same status.
        // SELECT u1.username AS username
        // FROM t_user u1 INNER JOIN t_user u2 ON u1.status = u2.status
        // WHERE u2.username = :p1
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u1")
                .select(col(TUser::getUsername, "u1"))
                .join(TUser.class, "u2", JoinType.INNER,
                        on -> on.eq(TUser::getStatus, "u1", TUser::getStatus, "u2"))
                .where(w -> w.eq(TUser::getUsername, "u2", "alice"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql)
                .contains("FROM t_user u1")
                .contains("INNER JOIN t_user u2 ON u1.status = u2.status")
                .contains("u2.username = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "alice");
    }

    @Test
    void renderSelect_selfJoin_selectFromBothAliases() {
        // Project columns from both self-join aliases
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u1")
                .select(
                        col(TUser::getUsername, "u1"),
                        col(TUser::getStatus, "u2").as("orderStatus")
                )
                .join(TUser.class, "u2", JoinType.LEFT,
                        on -> on.eq(TUser::getStatus, "u1", TUser::getStatus, "u2"))
                .mapTo(UserOrderDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql)
                .contains("u1.username AS username")
                .contains("u2.status AS orderStatus")
                .contains("LEFT JOIN t_user u2 ON u1.status = u2.status");
    }

    /**
     * Bare {@code col(prop)} without an explicit alias is ambiguous when the same entity class
     * appears as both root and join. Rendering must throw rather than silently pick the wrong alias.
     */
    @Test
    void renderSelect_selfJoin_bareColumnRefThrowsAmbiguousError() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u1")
                // col(TUser::getUsername) — no explicit alias in a self-join context
                .select(col(TUser::getUsername))
                .join(TUser.class, "u2", JoinType.INNER,
                        on -> on.eq(TUser::getStatus, "u1", TUser::getStatus, "u2"))
                .mapTo(UserDto.class);

        assertThatThrownBy(() -> SqlRenderer.renderSelect(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous column reference")
                .hasMessageContaining("TUser.username")
                .hasMessageContaining("col(TUser::getUsername, \"<alias>\")");
    }

    /**
     * The shorthand {@code .select(SFunction...)} also creates bare column expressions;
     * it must fail with the same ambiguity error in a self-join context.
     */
    @Test
    void renderSelect_selfJoin_selectShorthandThrowsAmbiguousError() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u1")
                // .select(SFunction...) shorthand — bare refs, no alias
                .select(TUser::getUsername, TUser::getStatus)
                .join(TUser.class, "u2", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u1", TUser::getId, "u2"))
                .mapTo(UserDto.class);

        assertThatThrownBy(() -> SqlRenderer.renderSelect(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous column reference")
                .hasMessageContaining("TUser.");
    }

    // ------------------------------------------------------------------ //
    //  JOIN (SELECT ...) subquery join
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_leftJoinSubquery_generatesInlineSubquerySql() {
        // LEFT JOIN (SELECT o.user_id AS userId, COUNT(*) AS orderCount FROM t_order o GROUP BY user_id) o
        //   ON o.userId = t.id   — on.eq() auto-resolves to camelCase alias for the subquery side
        SelectSpec<TOrder, TOrder> orderCount = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"), countStar().as("orderCount"))
                .groupBy(col(TOrder::getUserId, "o"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(TUser::getId, TUser::getUsername)
                .leftJoinSubquery(orderCount, TOrder.class, "o",
                        on -> on.eq(TOrder::getUserId, "o", TUser::getId, "t"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql).contains("LEFT JOIN (");
        assertThat(sql).contains("FROM t_order o");
        // subquery side uses property alias (userId), entity side uses column name (id)
        assertThat(sql).contains(") o ON o.userId = t.id");
        assertThat(sql).contains("FROM t_user t");
    }

    @Test
    void renderSelect_innerJoinSubquery_generatesInnerJoin() {
        SelectSpec<TOrder, TOrder> subq = SelectBuilder.from(TOrder.class, "o")
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(TUser::getId, TUser::getUsername)
                .innerJoinSubquery(subq, TOrder.class, "o",
                        on -> on.eq(TOrder::getUserId, "o", TUser::getId, "t"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql).contains("INNER JOIN (");
        // "o" is a subquery alias → property name; "t" is a plain entity → column name
        assertThat(sql).contains("o.userId = t.id");
    }

    @Test
    void renderSelect_joinSubqueryWithRawOn_generatesRawCondition() {
        // raw() is still available as an escape hatch for complex multi-condition ON clauses
        SelectSpec<TOrder, TOrder> subq = SelectBuilder.from(TOrder.class)
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(TUser::getId, TUser::getUsername)
                .joinSubquery(subq, TOrder.class, "o", JoinType.LEFT,
                        on -> on.raw("o.userId = t.id AND o.status = 'PAID'"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();

        assertThat(sql).contains("LEFT JOIN (");
        assertThat(sql).contains("o.userId = t.id AND o.status = 'PAID'");
    }
}
