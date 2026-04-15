package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TAuditUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the new jdbc-dsl features:
 * <ul>
 *   <li>Feature 2 – Batch INSERT ({@link JdbcDslExecutor#executeBatchInsert})</li>
 *   <li>Feature 3 – Logical Delete ({@link JdbcDslExecutor#executeLogicalDelete})</li>
 *   <li>Feature 4 – Auto-fill ({@link org.springframework.data.annotation.CreatedDate} /
 *       {@link org.springframework.data.annotation.LastModifiedDate})</li>
 * </ul>
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql",   executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JdbcDslNewFeaturesTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
        executor.setTimeProvider(() -> FIXED_TIME);
    }

    // ------------------------------------------------------------------ //
    //  Feature 2 – Batch INSERT
    // ------------------------------------------------------------------ //

    @Test
    void executeBatchInsert_emptyList_returnsEmptyArray() {
        BatchInsertSpec<TAuditUser> spec = BatchInsertBuilder
                .of(TAuditUser.class, List.of()).build();
        int[] result = executor.executeBatchInsert(spec);
        assertThat(result).isEmpty();
    }

    @Test
    void executeBatchInsert_multipleRows_insertsAll() {
        List<TAuditUser> users = Arrays.asList(
                new TAuditUser("batch1", "ACTIVE"),
                new TAuditUser("batch2", "ACTIVE"),
                new TAuditUser("batch3", "INACTIVE")
        );
        BatchInsertSpec<TAuditUser> spec = BatchInsertBuilder.of(TAuditUser.class, users).build();
        int[] affected = executor.executeBatchInsert(spec);

        assertThat(affected).hasSize(3).containsOnly(1);

        SelectSpec<TAuditUser, TAuditUser> query = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getStatus, List.of("ACTIVE", "INACTIVE")))
                .mapToEntity();
        // Disable auto-filter for this read (deleted is 0 for all, no issue)
        List<TAuditUser> result = executor.select(query);
        assertThat(result).extracting(TAuditUser::getUsername)
                .containsExactlyInAnyOrder("batch1", "batch2", "batch3");
    }

    @Test
    void executeBatchInsert_autoFillsTimestamps() {
        List<TAuditUser> users = List.of(
                new TAuditUser("ts1", "ACTIVE"),
                new TAuditUser("ts2", "ACTIVE")
        );
        executor.executeBatchInsert(BatchInsertBuilder.of(TAuditUser.class, users).build());

        SelectSpec<TAuditUser, TAuditUser> query = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getUsername, List.of("ts1", "ts2")))
                .mapToEntity();
        List<TAuditUser> result = executor.select(query);
        assertThat(result).hasSize(2).allSatisfy(u -> {
            assertThat(u.getCreatedAt()).as("createdAt").isEqualTo(FIXED_TIME);
            assertThat(u.getUpdatedAt()).as("updatedAt").isEqualTo(FIXED_TIME);
        });
    }

    // ------------------------------------------------------------------ //
    //  Feature 3 – Logical Delete
    // ------------------------------------------------------------------ //

    @Test
    void executeLogicalDelete_setsDeletedFlag() {
        TAuditUser user = new TAuditUser("toDelete", "ACTIVE");
        executor.save(user);
        assertThat(user.getId()).isNotNull();

        // Logical delete: set deleted = 1
        DeleteSpec<TAuditUser> del = DeleteBuilder.from(TAuditUser.class)
                .eq(TAuditUser::getId, user.getId())
                .build();
        int affected = executor.executeLogicalDelete(del);
        assertThat(affected).isEqualTo(1);

        // Verify the flag is set in DB
        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getId, user.getId())
                              .eq(TAuditUser::getDeleted, 1))
                .mapToEntity();
        // Use raw query without the logical-delete auto-filter
        JdbcDslConfig.setLogicalDeleteAutoFilter(false);
        try {
            List<TAuditUser> found = executor.select(verify);
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getDeleted()).isEqualTo(1);
        } finally {
            JdbcDslConfig.setLogicalDeleteAutoFilter(true);
        }
    }

    @Test
    void executeLogicalDelete_noAnnotation_throwsIllegalArgument() {
        // TOrder has no @LogicalDelete
        DeleteSpec<io.github.jsbxyyx.jdbcdsl.entity.TOrder> del =
                DeleteBuilder.from(io.github.jsbxyyx.jdbcdsl.entity.TOrder.class)
                        .eq(io.github.jsbxyyx.jdbcdsl.entity.TOrder::getId, 1L)
                        .build();
        assertThatThrownBy(() -> executor.executeLogicalDelete(del))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No @LogicalDelete field found");
    }

    @Test
    void select_logicalDeleteAutoFilter_excludesDeletedRows() {
        // Insert two users: one normal, one pre-deleted
        TAuditUser normal = new TAuditUser("normalUser", "ACTIVE");
        executor.save(normal);

        TAuditUser deleted = new TAuditUser("deletedUser", "ACTIVE");
        executor.save(deleted);
        // Manually mark deletedUser as deleted
        executor.executeLogicalDelete(DeleteBuilder.from(TAuditUser.class)
                .eq(TAuditUser::getId, deleted.getId()).build());

        // SELECT with auto-filter (default true): should only return normalUser
        SelectSpec<TAuditUser, TAuditUser> spec = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getUsername, List.of("normalUser", "deletedUser")))
                .mapToEntity();
        List<TAuditUser> result = executor.select(spec);
        assertThat(result).hasSize(1)
                .extracting(TAuditUser::getUsername)
                .containsExactly("normalUser");
    }

    @Test
    void select_logicalDeleteAutoFilterDisabled_returnsAllRows() {
        TAuditUser normal  = new TAuditUser("u1", "ACTIVE");
        TAuditUser deleted = new TAuditUser("u2", "ACTIVE");
        executor.save(normal);
        executor.save(deleted);
        executor.executeLogicalDelete(DeleteBuilder.from(TAuditUser.class)
                .eq(TAuditUser::getId, deleted.getId()).build());

        JdbcDslConfig.setLogicalDeleteAutoFilter(false);
        try {
            SelectSpec<TAuditUser, TAuditUser> spec = SelectBuilder.from(TAuditUser.class)
                    .where(w -> w.in(TAuditUser::getUsername, List.of("u1", "u2")))
                    .mapToEntity();
            List<TAuditUser> result = executor.select(spec);
            assertThat(result).hasSize(2);
        } finally {
            JdbcDslConfig.setLogicalDeleteAutoFilter(true);
        }
    }

    // ------------------------------------------------------------------ //
    //  Feature 4 – Auto-fill (INSERT auto-fills @CreatedDate + @LastModifiedDate)
    // ------------------------------------------------------------------ //

    @Test
    void save_autoFillsCreatedAtAndUpdatedAt() {
        TAuditUser user = new TAuditUser("autoFill", "ACTIVE");
        executor.save(user);

        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getUsername, "autoFill"))
                .mapToEntity();
        TAuditUser saved = executor.findOne(verify);
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void executeUpdate_autoFillsUpdatedAtOnly() {
        TAuditUser user = new TAuditUser("updateFill", "ACTIVE");
        executor.save(user);
        LocalDateTime createdTs = user.getCreatedAt();

        // Advance the clock
        LocalDateTime laterTime = FIXED_TIME.plusHours(1);
        executor.setTimeProvider(() -> laterTime);

        UpdateSpec<TAuditUser> update = UpdateBuilder.from(TAuditUser.class)
                .set(TAuditUser::getStatus, "INACTIVE")
                .eq(TAuditUser::getId, user.getId())
                .build();
        executor.executeUpdate(update);

        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getId, user.getId()))
                .mapToEntity();
        TAuditUser updated = executor.findOne(verify);
        // updated_at should be the later time; created_at unchanged
        assertThat(updated.getUpdatedAt()).isEqualTo(laterTime);
        assertThat(updated.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(updated.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void executeUpdate_updatedAtAlreadySet_doesNotOverwrite() {
        TAuditUser user = new TAuditUser("noOverwrite", "ACTIVE");
        executor.save(user);

        LocalDateTime explicitTime = FIXED_TIME.plusDays(10);
        executor.setTimeProvider(() -> FIXED_TIME.plusHours(5)); // different from explicit

        UpdateSpec<TAuditUser> update = UpdateBuilder.from(TAuditUser.class)
                .set(TAuditUser::getStatus, "SUSPENDED")
                .set(TAuditUser::getUpdatedAt, explicitTime)   // explicit value → must NOT be overwritten
                .eq(TAuditUser::getId, user.getId())
                .build();
        executor.executeUpdate(update);

        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getId, user.getId()))
                .mapToEntity();
        TAuditUser updated = executor.findOne(verify);
        assertThat(updated.getUpdatedAt()).isEqualTo(explicitTime);
    }
}
