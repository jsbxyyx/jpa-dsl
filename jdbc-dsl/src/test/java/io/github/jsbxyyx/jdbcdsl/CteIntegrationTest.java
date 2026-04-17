package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CTE ({@code WITH} clause) using H2.
 *
 * <p>Test data:
 * <ul>
 *   <li>alice  — ACTIVE,   age=30</li>
 *   <li>bob    — INACTIVE, age=25</li>
 *   <li>charlie — ACTIVE,  age=40</li>
 * </ul>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql",  executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CteIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
    }

    // ------------------------------------------------------------------ //
    //  Basic CTE filter
    // ------------------------------------------------------------------ //

    /**
     * WITH active_users AS (SELECT … FROM t_user t WHERE t.status = 'ACTIVE')
     * SELECT t.id AS id, t.username AS username FROM active_users t
     *
     * Expected: alice and charlie (ACTIVE users only).
     */
    @Test
    void cteQuery_activeUsers_returnsOnlyActiveUsers() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserDto.class);

        List<UserDto> rows = executor.select(spec);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getUsername()).isEqualTo("alice");
        assertThat(rows.get(1).getUsername()).isEqualTo("charlie");
    }

    // ------------------------------------------------------------------ //
    //  CTE with outer WHERE
    // ------------------------------------------------------------------ //

    /**
     * WITH senior AS (SELECT … FROM t_user t WHERE t.age > 28)
     * SELECT … FROM senior t WHERE t.status = 'ACTIVE'
     *
     * Expected: alice (age=30, ACTIVE) and charlie (age=40, ACTIVE).
     * bob (age=25) is excluded by the CTE; no one is INACTIVE among the senior set.
     */
    @Test
    void cteQuery_withOuterWhere_paramsMergedCorrectly() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.gt(TUser::getAge, 28))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("senior_users", TUser.class)
                .withCte("senior_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserDto.class);

        List<UserDto> rows = executor.select(spec);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getUsername()).isEqualTo("alice");
        assertThat(rows.get(1).getUsername()).isEqualTo("charlie");
    }

    // ------------------------------------------------------------------ //
    //  CTE + ORDER BY in outer query
    // ------------------------------------------------------------------ //

    /**
     * WITH all_users AS (SELECT … FROM t_user t)
     * SELECT … FROM all_users t ORDER BY t.age DESC
     *
     * Expected: charlie(40), alice(30), bob(25) — all users, ordered by age descending.
     */
    @Test
    void cteQuery_outerOrderBy_resultsSortedCorrectly() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("all_users", TUser.class)
                .withCte("all_users", cteBody)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.desc(TUser::getAge)))
                .mapTo(UserDto.class);

        List<UserDto> rows = executor.select(spec);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getUsername()).isEqualTo("charlie");  // age=40
        assertThat(rows.get(1).getUsername()).isEqualTo("alice");    // age=30
        assertThat(rows.get(2).getUsername()).isEqualTo("bob");      // age=25
    }

    // ------------------------------------------------------------------ //
    //  CTE column reference via alias
    // ------------------------------------------------------------------ //

    /**
     * Custom alias: FROM active_users u (not the default "t").
     * Verifies that the custom alias is correctly propagated in the rendered SQL.
     */
    @Test
    void cteQuery_customAlias_columnsMappedCorrectly() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("inactive_users", TUser.class, "u")
                .withCte("inactive_users", cteBody)
                .select(col(TUser::getId, "u"), col(TUser::getUsername, "u"))
                .mapTo(UserDto.class);

        List<UserDto> rows = executor.select(spec);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUsername()).isEqualTo("bob");
    }
}
