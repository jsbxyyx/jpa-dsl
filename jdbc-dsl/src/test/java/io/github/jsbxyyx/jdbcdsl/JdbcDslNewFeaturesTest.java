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
@Sql(scripts = "/jdbcdsl-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
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
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, List.of()).build();
        int[] result = executor.executeBatchInsert(spec);
        org.junit.jupiter.api.Assertions.assertEquals(0, result.length);
    }

    @Test
    void executeBatchInsert_multipleRows_insertsAll() {
        List<TAuditUser> users = Arrays.asList(
                new TAuditUser("batch1", "ACTIVE"),
                new TAuditUser("batch2", "ACTIVE"),
                new TAuditUser("batch3", "INACTIVE"));
        BatchInsertSpec<TAuditUser> spec =
                BatchInsertBuilder.of(TAuditUser.class, users).build();
        int[] affected = executor.executeBatchInsert(spec);

        org.junit.jupiter.api.Assertions.assertEquals(3, affected.length);
        org.junit.jupiter.api.Assertions.assertTrue(
                java.util.Arrays.stream(affected).allMatch(value -> value == 1));

        SelectSpec<TAuditUser, TAuditUser> query = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getStatus, List.of("ACTIVE", "INACTIVE")))
                .mapToEntity();
        // Disable auto-filter for this read (deleted is 0 for all, no issue)
        List<TAuditUser> result = executor.select(query);
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("batch1", "batch2", "batch3")),
                new java.util.HashSet<>(
                        (result).stream().map(TAuditUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                3, (result).stream().map(TAuditUser::getUsername).toList().size());
    }

    @Test
    void executeBatchInsert_autoFillsTimestamps() {
        List<TAuditUser> users = List.of(new TAuditUser("ts1", "ACTIVE"), new TAuditUser("ts2", "ACTIVE"));
        executor.executeBatchInsert(
                BatchInsertBuilder.of(TAuditUser.class, users).build());

        SelectSpec<TAuditUser, TAuditUser> query = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getUsername, List.of("ts1", "ts2")))
                .mapToEntity();
        List<TAuditUser> result = executor.select(query);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        (result).forEach(u -> {
            org.junit.jupiter.api.Assertions.assertEquals(FIXED_TIME, u.getCreatedAt());
            org.junit.jupiter.api.Assertions.assertEquals(FIXED_TIME, u.getUpdatedAt());
        });
    }

    // ------------------------------------------------------------------ //
    //  Feature 3 – Logical Delete
    // ------------------------------------------------------------------ //

    @Test
    void executeLogicalDelete_setsDeletedFlag() {
        TAuditUser user = new TAuditUser("toDelete", "ACTIVE");
        executor.save(user);
        org.junit.jupiter.api.Assertions.assertNotNull(user.getId());

        // Logical delete: set deleted = 1
        DeleteSpec<TAuditUser> del = DeleteBuilder.from(TAuditUser.class)
                .eq(TAuditUser::getId, user.getId())
                .build();
        int affected = executor.executeLogicalDelete(del);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        // Verify the flag is set in DB
        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getId, user.getId()).eq(TAuditUser::getDeleted, 1))
                .mapToEntity();
        // Use raw query without the logical-delete auto-filter
        JdbcDslConfig.setLogicalDeleteAutoFilter(false);
        try {
            List<TAuditUser> found = executor.select(verify);
            org.junit.jupiter.api.Assertions.assertEquals(1, found.size());
            org.junit.jupiter.api.Assertions.assertEquals(1, found.get(0).getDeleted());
        } finally {
            JdbcDslConfig.setLogicalDeleteAutoFilter(true);
        }
    }

    @Test
    void executeLogicalDelete_noAnnotation_throwsIllegalArgument() {
        // TOrder has no @LogicalDelete
        DeleteSpec<io.github.jsbxyyx.jdbcdsl.entity.TOrder> del = DeleteBuilder.from(
                        io.github.jsbxyyx.jdbcdsl.entity.TOrder.class)
                .eq(io.github.jsbxyyx.jdbcdsl.entity.TOrder::getId, 1L)
                .build();
        Throwable assertionThrown1 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> executor.executeLogicalDelete(del));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown1.getMessage().contains("No @LogicalDelete field found"));
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
                .eq(TAuditUser::getId, deleted.getId())
                .build());

        // SELECT with auto-filter (default true): should only return normalUser
        SelectSpec<TAuditUser, TAuditUser> spec = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.in(TAuditUser::getUsername, List.of("normalUser", "deletedUser")))
                .mapToEntity();
        List<TAuditUser> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("normalUser"),
                (result).stream().map(TAuditUser::getUsername).toList());
    }

    @Test
    void select_logicalDeleteAutoFilterDisabled_returnsAllRows() {
        TAuditUser normal = new TAuditUser("u1", "ACTIVE");
        TAuditUser deleted = new TAuditUser("u2", "ACTIVE");
        executor.save(normal);
        executor.save(deleted);
        executor.executeLogicalDelete(DeleteBuilder.from(TAuditUser.class)
                .eq(TAuditUser::getId, deleted.getId())
                .build());

        JdbcDslConfig.setLogicalDeleteAutoFilter(false);
        try {
            SelectSpec<TAuditUser, TAuditUser> spec = SelectBuilder.from(TAuditUser.class)
                    .where(w -> w.in(TAuditUser::getUsername, List.of("u1", "u2")))
                    .mapToEntity();
            List<TAuditUser> result = executor.select(spec);
            org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
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
        org.junit.jupiter.api.Assertions.assertEquals(FIXED_TIME, saved.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertEquals(FIXED_TIME, saved.getUpdatedAt());
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
        org.junit.jupiter.api.Assertions.assertEquals(laterTime, updated.getUpdatedAt());
        org.junit.jupiter.api.Assertions.assertEquals(FIXED_TIME, updated.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertEquals("INACTIVE", updated.getStatus());
    }

    @Test
    void executeUpdate_updatedAtAlreadySet_doesNotOverwrite() {
        TAuditUser user = new TAuditUser("noOverwrite", "ACTIVE");
        executor.save(user);

        LocalDateTime explicitTime = FIXED_TIME.plusDays(10);
        executor.setTimeProvider(() -> FIXED_TIME.plusHours(5)); // different from explicit

        UpdateSpec<TAuditUser> update = UpdateBuilder.from(TAuditUser.class)
                .set(TAuditUser::getStatus, "SUSPENDED")
                .set(TAuditUser::getUpdatedAt, explicitTime) // explicit value → must NOT be overwritten
                .eq(TAuditUser::getId, user.getId())
                .build();
        executor.executeUpdate(update);

        SelectSpec<TAuditUser, TAuditUser> verify = SelectBuilder.from(TAuditUser.class)
                .where(w -> w.eq(TAuditUser::getId, user.getId()))
                .mapToEntity();
        TAuditUser updated = executor.findOne(verify);
        org.junit.jupiter.api.Assertions.assertEquals(explicitTime, updated.getUpdatedAt());
    }
}
