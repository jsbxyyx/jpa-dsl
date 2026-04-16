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
import java.util.Map;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.raw;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the RawSql escape-hatch API using H2 in-memory database.
 *
 * <p>Test data (from jdbcdsl-data.sql):
 * <ul>
 *   <li>alice  (ACTIVE,   age=30) — 2 orders</li>
 *   <li>bob    (INACTIVE, age=25) — 1 order</li>
 *   <li>charlie (ACTIVE,  age=40) — no orders</li>
 * </ul>
 *
 * <p>Covers three escape-hatch entry points:
 * <ol>
 *   <li>{@code WhereBuilder.raw()} — raw SQL predicate in a type-safe DSL query</li>
 *   <li>{@code SqlFunctions.raw()} — raw SQL expression in SELECT / ORDER BY</li>
 *   <li>{@code JdbcDslExecutor.query() / queryOne() / update()} — fully raw SQL execution</li>
 * </ol>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",   executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RawSqlIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
    }

    // ------------------------------------------------------------------ //
    //  WhereBuilder.raw() — raw SQL predicate in DSL query
    // ------------------------------------------------------------------ //

    @Test
    void rawPredicate_noParams_filtersCorrectly() {
        // WHERE t.age > 25  →  alice (30) + charlie (40)
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.age > 25"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void rawPredicate_withParams_filtersCorrectly() {
        // WHERE t.age > :minAge  →  charlie (40) when minAge=35
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.age > :minAge", Map.of("minAge", 35)))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void rawPredicate_combinedWithTypedPredicate_appliesBothConditions() {
        // status = ACTIVE AND age > 25  →  charlie (40) only (alice is 30 but not > 25?… wait alice is 30>25)
        // alice (ACTIVE, 30>25 ✓), charlie (ACTIVE, 40>25 ✓) — both qualify
        // Let's use age > 30 to get only charlie
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")
                        .raw("t.age > 30"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void rawPredicate_conditionalFalse_predicateOmitted() {
        // The raw condition is disabled — should return all users
        boolean applyRaw = false;
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.age > 100", applyRaw))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(3);
    }

    // ------------------------------------------------------------------ //
    //  SqlFunctions.raw() — raw expression in SELECT / ORDER BY
    // ------------------------------------------------------------------ //

    @Test
    void rawExpression_inSelect_embeddedVerbatimAndMappedToDto() {
        // SELECT t.id, UPPER(t.username) AS username FROM t_user
        // The raw expression renders verbatim; the DTO mapper uses the column label.
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(
                        SqlFunctions.col(TUser::getId),
                        raw("UPPER(t.username)").as("username")
                )
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername)))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        assertThat(result).hasSize(3)
                .extracting(UserDto::getUsername)
                .containsExactly("ALICE", "BOB", "CHARLIE");
    }

    @Test
    void rawExpression_inOrderBy_sortsCorrectly() {
        // ORDER BY CASE WHEN status='ACTIVE' THEN 0 ELSE 1 END — ACTIVE users first
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(
                        JOrder.asc(raw("CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END")),
                        JOrder.asc(TUser::getUsername)
                ))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);

        // ACTIVE: alice, charlie; INACTIVE: bob
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
        assertThat(result.get(1).getUsername()).isEqualTo("charlie");
        assertThat(result.get(2).getUsername()).isEqualTo("bob");
    }

    // ------------------------------------------------------------------ //
    //  JdbcDslExecutor.query() — fully raw SELECT
    // ------------------------------------------------------------------ //

    @Test
    void query_rawSql_mapsToDto() {
        List<UserDto> result = executor.query(
                "SELECT id, username FROM t_user WHERE status = :status ORDER BY username",
                Map.of("status", "ACTIVE"),
                UserDto.class);

        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactly("alice", "charlie");
    }

    @Test
    void query_rawSql_noParams_returnsAll() {
        List<UserDto> result = executor.query(
                "SELECT id, username FROM t_user ORDER BY username",
                Map.of(),
                UserDto.class);

        assertThat(result).hasSize(3)
                .extracting(UserDto::getUsername)
                .containsExactly("alice", "bob", "charlie");
    }

    @Test
    void queryOne_rawSql_returnsFirstRow() {
        UserDto result = executor.queryOne(
                "SELECT id, username FROM t_user WHERE username = :name",
                Map.of("name", "bob"),
                UserDto.class);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("bob");
    }

    @Test
    void queryOne_rawSql_noMatch_returnsNull() {
        UserDto result = executor.queryOne(
                "SELECT id, username FROM t_user WHERE username = :name",
                Map.of("name", "nobody"),
                UserDto.class);

        assertThat(result).isNull();
    }

    // ------------------------------------------------------------------ //
    //  JdbcDslExecutor.update() — fully raw DML
    // ------------------------------------------------------------------ //

    @Test
    void update_rawSql_updatesRows() {
        int affected = executor.update(
                "UPDATE t_user SET status = :newStatus WHERE status = :oldStatus",
                Map.of("newStatus", "DISABLED", "oldStatus", "INACTIVE"));

        assertThat(affected).isEqualTo(1); // only bob

        // Verify with a subsequent query
        List<UserDto> active = executor.query(
                "SELECT id, username FROM t_user WHERE status = :s",
                Map.of("s", "DISABLED"),
                UserDto.class);
        assertThat(active).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("bob");
    }

    @Test
    void update_rawSql_insertsRow() {
        int affected = executor.update(
                "INSERT INTO t_user (username, email, age, status) VALUES (:u, :e, :a, :s)",
                Map.of("u", "dave", "e", "dave@example.com", "a", 28, "s", "ACTIVE"));

        assertThat(affected).isEqualTo(1);

        List<UserDto> all = executor.query(
                "SELECT id, username FROM t_user ORDER BY username",
                Map.of(),
                UserDto.class);
        assertThat(all).hasSize(4)
                .extracting(UserDto::getUsername)
                .contains("dave");
    }
}
