package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserOrderDto;
import io.github.jsbxyyx.jdbcdsl.entity.TAuditUser;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static org.assertj.core.api.Assertions.assertThat;

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
}
