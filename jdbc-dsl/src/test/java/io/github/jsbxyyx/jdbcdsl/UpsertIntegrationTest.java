package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcDslExecutor#upsert} using H2.
 *
 * <p>Uses a dedicated schema ({@code upsert-schema.sql}) that adds a UNIQUE constraint on
 * {@code username}, which is required for H2's {@code ON CONFLICT (username) DO UPDATE SET}
 * syntax to work.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/upsert-schema.sql",  executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/upsert-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UpsertIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;

    private final UpsertSpec<TUser> byUsername = UpsertBuilder.into(TUser.class)
            .onConflict(TUser::getUsername)
            .doUpdateAll()
            .build();

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate, new H2Dialect());
    }

    // ------------------------------------------------------------------ //
    //  Insert new row (no conflict)
    // ------------------------------------------------------------------ //

    /**
     * Table is empty; upsert should behave like a plain INSERT.
     */
    @Test
    void upsert_insertNewRow_rowIsInserted() {
        TUser user = new TUser("alice", "alice@example.com", 30, "ACTIVE");

        executor.upsert(byUsername, user);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, email, age, status FROM t_user",
                Collections.emptyMap());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("USERNAME")).isEqualTo("alice");
        assertThat(rows.get(0).get("EMAIL")).isEqualTo("alice@example.com");
    }

    // ------------------------------------------------------------------ //
    //  Conflict on username → update existing row
    // ------------------------------------------------------------------ //

    /**
     * Insert alice first, then upsert with updated email and status.
     * The existing row should be updated (not duplicated).
     */
    @Test
    void upsert_conflictOnUsername_updatesExistingRow() {
        // Pre-insert via direct SQL to avoid dependency on save()
        jdbcTemplate.update(
                "INSERT INTO t_user (username, email, age, status) VALUES ('alice', 'old@example.com', 30, 'INACTIVE')",
                Collections.emptyMap());

        // Upsert with new values for the same username
        TUser updated = new TUser("alice", "new@example.com", 30, "ACTIVE");
        executor.upsert(byUsername, updated);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, email, status FROM t_user WHERE username = 'alice'",
                Collections.emptyMap());
        assertThat(rows).hasSize(1);  // still exactly one row
        assertThat(rows.get(0).get("EMAIL")).isEqualTo("new@example.com");
        assertThat(rows.get(0).get("STATUS")).isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------------ //
    //  doUpdate(specific columns) — only those columns are updated
    // ------------------------------------------------------------------ //

    /**
     * Upsert with {@code doUpdate(TUser::getEmail)} on conflict — only the email column
     * should be updated; age and status must remain as originally inserted.
     */
    @Test
    void upsert_doUpdateSpecificColumn_onlyThatColumnUpdated() {
        jdbcTemplate.update(
                "INSERT INTO t_user (username, email, age, status) VALUES ('bob', 'bob@old.com', 25, 'ACTIVE')",
                Collections.emptyMap());

        UpsertSpec<TUser> emailOnlyUpdate = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getEmail)
                .build();

        TUser updated = new TUser("bob", "bob@new.com", 99, "INACTIVE");
        executor.upsert(emailOnlyUpdate, updated);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, age, status FROM t_user WHERE username = 'bob'",
                Collections.emptyMap());
        assertThat(row.get("EMAIL")).isEqualTo("bob@new.com");   // updated
        assertThat(row.get("AGE")).isEqualTo(25);                // unchanged
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");       // unchanged
    }

    // ------------------------------------------------------------------ //
    //  Multiple upserts — idempotent
    // ------------------------------------------------------------------ //

    /**
     * Calling upsert twice for the same username should leave exactly one row in the table.
     */
    @Test
    void upsert_calledTwice_tableHasExactlyOneRow() {
        TUser first  = new TUser("charlie", "first@example.com", 40, "ACTIVE");
        TUser second = new TUser("charlie", "second@example.com", 41, "INACTIVE");

        executor.upsert(byUsername, first);
        executor.upsert(byUsername, second);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, email, age FROM t_user",
                Collections.emptyMap());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("EMAIL")).isEqualTo("second@example.com");
        assertThat(rows.get(0).get("AGE")).isEqualTo(41);
    }
}
