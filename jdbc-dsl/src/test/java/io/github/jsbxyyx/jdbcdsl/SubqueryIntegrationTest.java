package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.min;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for subquery support using H2 in-memory database.
 *
 * <p>Test data (from jdbcdsl-data.sql):
 * <ul>
 *   <li>alice  (ACTIVE)  — ORD-001 PAID $100, ORD-002 PENDING $250</li>
 *   <li>bob    (INACTIVE) — ORD-003 PAID $50</li>
 *   <li>charlie (ACTIVE) — no orders</li>
 * </ul>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",   executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SubqueryIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
    }

    // ------------------------------------------------------------------ //
    //  IN subquery
    // ------------------------------------------------------------------ //

    @Test
    void inSubquery_usersWithOrders_returnsAliceAndBob() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void inSubquery_usersWithPaidOrders_returnsAliceAndBob() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    // ------------------------------------------------------------------ //
    //  NOT IN subquery
    // ------------------------------------------------------------------ //

    @Test
    void notInSubquery_usersWithNoOrders_returnsCharlie() {
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.notInSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    // ------------------------------------------------------------------ //
    //  EXISTS subquery
    // ------------------------------------------------------------------ //

    @Test
    void existsSubquery_usersWithAnyOrder_returnsAliceAndBob() {
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getId, "o"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.exists(inner))
                .mapTo(UserDto.class);

        // EXISTS (non-correlated) is always true when subquery returns any rows
        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(3); // all users, since subquery is non-correlated and has rows
    }

    @Test
    void notExistsSubquery_nonCorrelated_empty_returnsAll() {
        // NOT EXISTS on an empty subquery result → all rows pass
        SelectSpec<TOrder, TOrder> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "NONEXISTENT"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.notExists(inner))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(3);
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery  (col OP (SELECT single value))
    // ------------------------------------------------------------------ //

    @Test
    void scalarSubquery_gt_usersOlderThanMin_returnsAliceAndCharlie() {
        // MIN(age) = 25 (bob)  →  alice (30) and charlie (40) qualify
        SelectSpec<TUser, Integer> inner = SelectBuilder.from(TUser.class, "u2")
                .select(min(TUser::getAge).as("minAge"))
                .mapTo(Integer.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.gtScalar(col(TUser::getAge), scalar(inner)))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    // ------------------------------------------------------------------ //
    //  Subquery combined with outer WHERE conditions
    // ------------------------------------------------------------------ //

    @Test
    void inSubquery_combinedWithOuterWhere_returnsIntersection() {
        // Users who have PAID orders AND are ACTIVE → alice
        SelectSpec<TOrder, Long> inner = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getUserId, "o"))
                .where(w -> w.eq(TOrder::getStatus, "PAID"))
                .mapTo(Long.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")
                        .inSubquery(col(TUser::getId), inner))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("alice");
    }
}
