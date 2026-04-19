package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserOrderDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JOIN enhancements using H2 in-memory database.
 *
 * <p>Test data (from jdbcdsl-data.sql):
 * <ul>
 *   <li>alice  (ACTIVE)  — ORD-001 PAID $100, ORD-002 PENDING $250</li>
 *   <li>bob    (INACTIVE) — ORD-003 PAID $50</li>
 *   <li>charlie (ACTIVE) — no orders</li>
 * </ul>
 *
 * <p>Covers three JOIN-enhancement scenarios:
 * <ol>
 *   <li>Multi-table chained {@code .join().join()} (item 7)</li>
 *   <li>Cross-table field projection into a combined DTO (item 8)</li>
 *   <li>Self-join with explicit alias management (item 9)</li>
 * </ol>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",   executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JoinIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
    }

    // ------------------------------------------------------------------ //
    //  Item 7: Multi-table chained JOIN
    // ------------------------------------------------------------------ //

    @Test
    void multiJoin_innerJoinTwice_returnsOnlyRowsMatchingBothJoins() {
        // t_user u
        //   INNER JOIN t_order o1 ON u.id = o1.user_id  (PAID orders)
        //   INNER JOIN t_order o2 ON u.id = o2.user_id  (PENDING orders)
        // → only alice has both a PAID and a PENDING order
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(col(TUser::getId, "u"), col(TUser::getUsername, "u"))
                .join(TOrder.class, "o1", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o1"))
                .join(TOrder.class, "o2", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o2"))
                .where(w -> w
                        .eq(TOrder::getStatus, "o1", "PAID")
                        .eq(TOrder::getStatus, "o2", "PENDING"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result)
                .hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("alice");
    }

    @Test
    void multiJoin_count_deduplicatesWithDistinct() {
        // COUNT for a multi-join spec must use COUNT(DISTINCT pk) to avoid inflated counts
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(col(TUser::getId, "u"), col(TUser::getUsername, "u"))
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .mapTo(UserDto.class);

        long count = executor.count(spec);

        // alice + bob have orders; charlie has none → 2 distinct users
        assertThat(count).isEqualTo(2);
    }

    // ------------------------------------------------------------------ //
    //  Item 8: Cross-table field projection
    // ------------------------------------------------------------------ //

    @Test
    void crossTableProjection_innerJoin_returnsAllMatchedRows() {
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(
                        col(TUser::getUsername, "u"),
                        col(TOrder::getOrderNo, "o"),
                        col(TOrder::getAmount, "o"),
                        col(TOrder::getStatus, "o").as("orderStatus")
                )
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .orderBy(JSort.by(JOrder.asc(col(TOrder::getOrderNo, "o"))))
                .mapTo(UserOrderDto.class);

        List<UserOrderDto> result = executor.select(spec);

        // alice has 2 orders, bob has 1 → 3 rows total
        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserOrderDto::getOrderNo)
                .containsExactly("ORD-001", "ORD-002", "ORD-003");
        assertThat(result).extracting(UserOrderDto::getUsername)
                .containsExactly("alice", "alice", "bob");
    }

    @Test
    void crossTableProjection_leftJoin_includesUsersWithNoOrders() {
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(
                        col(TUser::getUsername, "u"),
                        col(TOrder::getOrderNo, "o"),
                        col(TOrder::getAmount, "o")
                )
                .join(TOrder.class, "o", JoinType.LEFT,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .orderBy(JSort.by(JOrder.asc(col(TUser::getUsername, "u"))))
                .mapTo(UserOrderDto.class);

        List<UserOrderDto> result = executor.select(spec);

        // alice 2 orders + bob 1 order + charlie 0 orders (1 row with nulls) = 4 rows
        assertThat(result).hasSize(4);

        UserOrderDto charlieRow = result.stream()
                .filter(r -> "charlie".equals(r.getUsername()))
                .findFirst()
                .orElseThrow();
        assertThat(charlieRow.getOrderNo()).isNull();
        assertThat(charlieRow.getAmount()).isNull();
    }

    @Test
    void crossTableProjection_withWhereOnJoinedColumn_filtersCorrectly() {
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(
                        col(TUser::getUsername, "u"),
                        col(TOrder::getOrderNo, "o"),
                        col(TOrder::getAmount, "o")
                )
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "o", "PAID"))
                .mapTo(UserOrderDto.class);

        List<UserOrderDto> result = executor.select(spec);

        // ORD-001 (alice, PAID) + ORD-003 (bob, PAID) → 2 rows
        assertThat(result).hasSize(2)
                .extracting(UserOrderDto::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-003");
    }

    @Test
    void crossTableProjection_sumAmount_groupByUser() {
        // SELECT u.username, SUM(o.amount) AS amount
        // FROM t_user u INNER JOIN t_order o ON u.id = o.user_id
        // GROUP BY u.username
        SelectSpec<TUser, UserOrderDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(
                        col(TUser::getUsername, "u"),
                        SqlFunctions.sum(TOrder::getAmount).as("amount")
                )
                .join(TOrder.class, "o", JoinType.INNER,
                        on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .groupBy(col(TUser::getUsername, "u"))
                .orderBy(JSort.by(JOrder.asc(col(TUser::getUsername, "u"))))
                .mapTo(UserOrderDto.class);

        List<UserOrderDto> result = executor.select(spec);

        // alice: 100 + 250 = 350, bob: 50
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
        assertThat(result.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(result.get(1).getUsername()).isEqualTo("bob");
        assertThat(result.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ------------------------------------------------------------------ //
    //  Item 9: Self-join with explicit alias management
    // ------------------------------------------------------------------ //

    @Test
    void selfJoin_sameStatusUsers_returnsMatchingUsers() {
        // Find all users (u1) who share the same status as 'alice' (ACTIVE).
        // t_user u1 INNER JOIN t_user u2 ON u1.status = u2.status
        // WHERE u2.username = 'alice'
        // → alice (ACTIVE matches ACTIVE) and charlie (ACTIVE matches ACTIVE)
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u1")
                .select(
                        col(TUser::getId, "u1"),
                        col(TUser::getUsername, "u1")
                )
                .join(TUser.class, "u2", JoinType.INNER,
                        on -> on.eq(TUser::getStatus, "u1", TUser::getStatus, "u2"))
                .where(w -> w.eq(TUser::getUsername, "u2", "alice"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void selfJoin_count_deduplicatesWithDistinct() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u1")
                .select(col(TUser::getId, "u1"), col(TUser::getUsername, "u1"))
                .join(TUser.class, "u2", JoinType.INNER,
                        on -> on.eq(TUser::getStatus, "u1", TUser::getStatus, "u2"))
                .where(w -> w.eq(TUser::getUsername, "u2", "alice"))
                .mapTo(UserDto.class);

        long count = executor.count(spec);

        // alice + charlie share ACTIVE status with alice → 2 distinct users
        assertThat(count).isEqualTo(2);
    }

    // ------------------------------------------------------------------ //
    //  JOIN (SELECT ...) subquery join
    // ------------------------------------------------------------------ //

    @Test
    void leftJoinSubquery_orderCountPerUser_includesUsersWithNoOrders() {
        // LEFT JOIN (SELECT user_id AS userId, COUNT(*) AS orderCount FROM t_order GROUP BY user_id) o
        //   ON o.userId = t.id   — on.eq() auto-uses camelCase alias for the subquery side
        // → alice: 2 orders, bob: 1 order, charlie: no matching row (LEFT JOIN keeps it)
        SelectSpec<TOrder, TOrder> orderCount = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"), countStar().as("orderCount"))
                .groupBy(col(TOrder::getUserId, "o"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(col(TUser::getId, "t"), col(TUser::getUsername, "t"))
                .leftJoinSubquery(orderCount, TOrder.class, "o",
                        on -> on.eq(TOrder::getUserId, "o", TUser::getId, "t"))
                .orderBy(JSort.by(JOrder.asc(col(TUser::getUsername, "t"))))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        // all 3 users: LEFT JOIN keeps charlie even though he has no orders
        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserDto::getUsername)
                .containsExactly("alice", "bob", "charlie");
    }

    @Test
    void innerJoinSubquery_paidOrdersOnly_returnsOnlyUsersWithPaidOrders() {
        // INNER JOIN (SELECT * FROM t_order WHERE status = 'PAID') o
        //   ON o.userId = t.id   — on.eq() auto-uses camelCase alias for the subquery side
        // → alice and bob have PAID orders; charlie has none → only alice and bob returned
        SelectSpec<TOrder, TOrder> paidOrders = SelectBuilder.from(TOrder.class)
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(col(TUser::getId, "t"), col(TUser::getUsername, "t"))
                .innerJoinSubquery(paidOrders, TOrder.class, "o",
                        on -> on.eq(TOrder::getUserId, "o", TUser::getId, "t"))
                .orderBy(JSort.by(JOrder.asc(col(TUser::getUsername, "t"))))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::getUsername)
                .containsExactly("alice", "bob");
    }

    @Test
    void joinSubquery_rawOnCondition_multiConditionOnClause() {
        // raw() is still available for complex multi-condition ON clauses
        // INNER JOIN (SELECT * FROM t_order) o ON o.userId = t.id AND o.status = 'PAID'
        // WHERE t.username = 'alice'
        // → alice has 1 PAID order and 1 PENDING; INNER JOIN with status filter returns 1 row
        SelectSpec<TOrder, TOrder> allOrders = SelectBuilder.from(TOrder.class)
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "t")
                .select(col(TUser::getId, "t"), col(TUser::getUsername, "t"))
                .joinSubquery(allOrders, TOrder.class, "o", JoinType.INNER,
                        on -> on.raw("o.userId = t.id AND o.status = 'PAID'"))
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        // alice has exactly 1 PAID order → 1 row
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }
}
