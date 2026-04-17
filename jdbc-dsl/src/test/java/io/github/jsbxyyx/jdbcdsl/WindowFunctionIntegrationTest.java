package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserOrderCountDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserRnDto;
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

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for window functions and scalar subqueries using H2.
 *
 * <p>Test data:
 * <ul>
 *   <li>alice  (ACTIVE,   age=30) — ORD-001 PAID $100, ORD-002 PENDING $250</li>
 *   <li>bob    (INACTIVE, age=25) — ORD-003 PAID $50</li>
 *   <li>charlie (ACTIVE,  age=40) — no orders</li>
 * </ul>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql",  executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WindowFunctionIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
    }

    // ------------------------------------------------------------------ //
    //  ROW_NUMBER
    // ------------------------------------------------------------------ //

    /**
     * ROW_NUMBER() partitioned by status, ordered by age ASC.
     * ACTIVE group: alice(30)→1, charlie(40)→2.
     * INACTIVE group: bob(25)→1.
     */
    @Test
    void rowNumber_partitionByStatus_orderByAge_assignsCorrectRanks() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rowNumber().over(w -> w
                                .partitionBy(TUser::getStatus)
                                .orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .orderBy(JSort.by(JOrder.asc(TUser::getStatus), JOrder.asc(TUser::getAge)))
                .mapTo(UserRnDto.class);

        List<UserRnDto> rows = executor.select(spec);
        assertThat(rows).hasSize(3);

        // Rows are sorted ACTIVE-alice, ACTIVE-charlie, INACTIVE-bob
        assertThat(rows.get(0).getUsername()).isEqualTo("alice");
        assertThat(rows.get(0).getRn()).isEqualTo(1L);

        assertThat(rows.get(1).getUsername()).isEqualTo("charlie");
        assertThat(rows.get(1).getRn()).isEqualTo(2L);

        assertThat(rows.get(2).getUsername()).isEqualTo("bob");
        assertThat(rows.get(2).getRn()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ //
    //  RANK
    // ------------------------------------------------------------------ //

    /**
     * RANK() ordered by age ASC (no partition).
     * bob(25)→1, alice(30)→2, charlie(40)→3.
     */
    @Test
    void rank_orderByAge_assignsGlobalRank() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        rank().over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .orderBy(JSort.by(JOrder.asc(TUser::getAge)))
                .mapTo(UserRnDto.class);

        List<UserRnDto> rows = executor.select(spec);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getUsername()).isEqualTo("bob");
        assertThat(rows.get(0).getRn()).isEqualTo(1L);
        assertThat(rows.get(1).getUsername()).isEqualTo("alice");
        assertThat(rows.get(1).getRn()).isEqualTo(2L);
        assertThat(rows.get(2).getUsername()).isEqualTo("charlie");
        assertThat(rows.get(2).getRn()).isEqualTo(3L);
    }

    // ------------------------------------------------------------------ //
    //  DENSE_RANK
    // ------------------------------------------------------------------ //

    @Test
    void denseRank_orderByAge_assignsContiguousRanks() {
        SelectSpec<TUser, UserRnDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        denseRank().over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("rn"))
                .orderBy(JSort.by(JOrder.asc(TUser::getAge)))
                .mapTo(UserRnDto.class);

        List<UserRnDto> rows = executor.select(spec);
        assertThat(rows).hasSize(3);
        // No ties, so RANK and DENSE_RANK produce the same result here
        assertThat(rows.get(0).getRn()).isEqualTo(1L);
        assertThat(rows.get(1).getRn()).isEqualTo(2L);
        assertThat(rows.get(2).getRn()).isEqualTo(3L);
    }

    // ------------------------------------------------------------------ //
    //  SUM aggregate window function
    // ------------------------------------------------------------------ //

    /**
     * SUM(amount) OVER () — total across all orders, returned for every row.
     * Total = 100 + 250 + 50 = 400.
     */
    @Test
    void sum_overEmptyWindow_returnsTotalForEveryRow() {
        // Reuse UserRnDto with rn mapped from a CAST to Long; for simplicity, use TOrder entity
        // and check via raw SQL renderer — easier to verify with a custom DTO.
        // We use a simpler check: query count to confirm all rows return the same total.
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(col(TOrder::getOrderNo, "o"),
                        sum(TOrder::getAmount).over().as("runningTotal"))
                .mapToEntity();

        // Can't easily map into TOrder since it has no runningTotal field.
        // Verify via SQL rendering (the SQL was verified in the unit test).
        // Here just confirm no exception is thrown and we get 3 rows.
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("SUM(o.amount) OVER ()");

        // Execute raw to verify actual values
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                rendered.getSql(), rendered.getParams());
        assertThat(rows).hasSize(3);
        rows.forEach(row -> {
            Object total = row.get("RUNNINGTOTAL");
            assertThat(new BigDecimal(total.toString()))
                    .isEqualByComparingTo(new BigDecimal("400.00"));
        });
    }

    // ------------------------------------------------------------------ //
    //  LAG / LEAD
    // ------------------------------------------------------------------ //

    /**
     * LAG(amount, 1) ordered by id — previous row's amount; first row gets NULL.
     */
    @Test
    void lag_orderById_returnsPreviousAmount() {
        RenderedSql rendered = SqlRenderer.renderSelect(
                SelectBuilder.from(TOrder.class, "o")
                        .select(col(TOrder::getOrderNo, "o"),
                                lag(TOrder::getAmount, 1)
                                        .over(w -> w.orderBy(JOrder.asc(TOrder::getId)))
                                        .as("prevAmount"))
                        .orderBy(JSort.by(JOrder.asc(TOrder::getId)))
                        .mapToEntity());

        assertThat(rendered.getSql()).contains("LAG(o.amount, 1) OVER (ORDER BY o.id ASC)");

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                rendered.getSql(), rendered.getParams());
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("PREVAMOUNT")).isNull();  // first row has no predecessor
        assertThat(new BigDecimal(rows.get(1).get("PREVAMOUNT").toString()))
                .isEqualByComparingTo(new BigDecimal("100.00"));  // ORD-002 follows ORD-001
    }

    /**
     * LEAD(amount, 1) ordered by id — next row's amount; last row gets NULL.
     */
    @Test
    void lead_orderById_returnsNextAmount() {
        RenderedSql rendered = SqlRenderer.renderSelect(
                SelectBuilder.from(TOrder.class, "o")
                        .select(col(TOrder::getOrderNo, "o"),
                                lead(TOrder::getAmount, 1)
                                        .over(w -> w.orderBy(JOrder.asc(TOrder::getId)))
                                        .as("nextAmount"))
                        .orderBy(JSort.by(JOrder.asc(TOrder::getId)))
                        .mapToEntity());

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                rendered.getSql(), rendered.getParams());
        assertThat(rows).hasSize(3);
        assertThat(new BigDecimal(rows.get(0).get("NEXTAMOUNT").toString()))
                .isEqualByComparingTo(new BigDecimal("250.00"));  // ORD-001 → ORD-002
        assertThat(rows.get(2).get("NEXTAMOUNT")).isNull();       // last row has no successor
    }

    // ------------------------------------------------------------------ //
    //  Scalar subquery in SELECT clause
    // ------------------------------------------------------------------ //

    /**
     * SELECT t.username, (SELECT COUNT(*) FROM t_order o WHERE o.user_id = t.id) AS orderCount
     * alice→2, bob→1, charlie→0.
     */
    @Test
    void scalarSubquery_countOrdersPerUser_returnsCorrectCounts() {
        // Correlated subquery: WHERE o.user_id = t.id (outer alias = "t", inner alias = "o")
        SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
                .select(countStar())
                .where(w -> w.raw("o.user_id = t.id"))
                .mapToEntity();

        SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        SqlFunctions.<Long>subquery(countSpec).as("orderCount"))
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserOrderCountDto.class);

        List<UserOrderCountDto> rows = executor.select(spec);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getUsername()).isEqualTo("alice");
        assertThat(rows.get(0).getOrderCount()).isEqualTo(2L);
        assertThat(rows.get(1).getUsername()).isEqualTo("bob");
        assertThat(rows.get(1).getOrderCount()).isEqualTo(1L);
        assertThat(rows.get(2).getUsername()).isEqualTo("charlie");
        assertThat(rows.get(2).getOrderCount()).isEqualTo(0L);
    }

    /**
     * Scalar subquery with outer WHERE — params must not collide.
     * Only ACTIVE users: alice→2, charlie→0.
     */
    @Test
    void scalarSubquery_withOuterWhere_paramsDontCollide() {
        // Correlated subquery: WHERE o.user_id = t.id (outer alias = "t", inner alias = "o")
        SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
                .select(countStar())
                .where(w -> w.raw("o.user_id = t.id"))
                .mapToEntity();

        SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getUsername),
                        SqlFunctions.<Long>subquery(countSpec).as("orderCount"))
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserOrderCountDto.class);

        List<UserOrderCountDto> rows = executor.select(spec);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getUsername()).isEqualTo("alice");
        assertThat(rows.get(0).getOrderCount()).isEqualTo(2L);
        assertThat(rows.get(1).getUsername()).isEqualTo("charlie");
        assertThat(rows.get(1).getOrderCount()).isEqualTo(0L);
    }

    // ------------------------------------------------------------------ //
    //  Math functions
    // ------------------------------------------------------------------ //

    @Test
    void abs_rendersAndExecutesCorrectly() {
        // ABS(age) should equal age since all ages are positive
        RenderedSql rendered = SqlRenderer.renderSelect(
                SelectBuilder.from(TUser.class)
                        .select(col(TUser::getUsername), abs(TUser::getAge).as("age"))
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .mapTo(UserRnDto.class));

        assertThat(rendered.getSql()).contains("ABS(t.age)");
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                rendered.getSql(), rendered.getParams());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("AGE")).isEqualTo(30);
    }

    @Test
    void round_rendersAndExecutesCorrectly() {
        RenderedSql rendered = SqlRenderer.renderSelect(
                SelectBuilder.from(TOrder.class, "o")
                        .select(col(TOrder::getOrderNo, "o"), round(TOrder::getAmount).as("rn"))
                        .where(w -> w.eq(TOrder::getOrderNo, "ORD-001"))
                        .mapToEntity());

        assertThat(rendered.getSql()).contains("ROUND(o.amount)");
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                rendered.getSql(), rendered.getParams());
        assertThat(rows).hasSize(1);
        assertThat(new BigDecimal(rows.get(0).get("RN").toString()))
                .isEqualByComparingTo(new BigDecimal("100"));
    }
}
