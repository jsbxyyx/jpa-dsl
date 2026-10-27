package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.dto.OrderDto;
import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.lit;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.upper;

/**
 * Integration tests for {@link JdbcDslExecutor} using H2 in-memory database.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/jdbcdsl-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/jdbcdsl-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JdbcDslIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcDslExecutor executor;
    private JdbcDslExecutor mysqlExecutor;

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(jdbcTemplate);
        mysqlExecutor = new JdbcDslExecutor(jdbcTemplate, new MySqlDialect());
    }

    // ------------------------------------------------------------------ //
    //  Basic SELECT
    // ------------------------------------------------------------------ //

    @Test
    void select_allUsers_returnsAll() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
    }

    @Test
    void select_byStatus_returnsFiltered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "charlie")),
                new java.util.HashSet<>(
                        (result).stream().map(UserDto::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(UserDto::getUsername).toList().size());
    }

    @Test
    void select_like_returnsMatching() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "%ali%"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void select_between_ageRange() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.between(TUser::getAge, 25, 31))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "bob")),
                new java.util.HashSet<>(
                        (result).stream().map(UserDto::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(UserDto::getUsername).toList().size());
    }

    @Test
    void select_in_returnsMatchingStatuses() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getStatus, List.of("ACTIVE", "INACTIVE")))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
    }

    @Test
    void select_isNull_email() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.isNull(TUser::getEmail))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void select_orderBy_ascending() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice", "bob", "charlie"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void select_orderBy_descending() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byDesc(TUser::getUsername))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie", "bob", "alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    // ------------------------------------------------------------------ //
    //  Pagination
    // ------------------------------------------------------------------ //

    @Test
    void selectPage_firstPage_sql2008Dialect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2);
        Page<UserDto> page = executor.selectPage(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getTotalPages());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice", "bob"),
                (page.getContent()).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void selectPage_secondPage_sql2008Dialect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(1, 2);
        Page<UserDto> page = executor.selectPage(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(1, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie"),
                (page.getContent()).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void selectPage_mysqlDialect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2);
        Page<UserDto> page = mysqlExecutor.selectPage(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
    }

    @Test
    void selectPage_withPageableSort() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2, JSort.byAsc(TUser::getUsername));
        Page<UserDto> page = executor.selectPage(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice", "bob"),
                (page.getContent()).stream().map(UserDto::getUsername).toList());
    }

    // ------------------------------------------------------------------ //
    //  findOne
    // ------------------------------------------------------------------ //

    @Test
    void findOne_matchingRow_returnsFirstResult() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapTo(UserDto.class);

        UserDto result = executor.findOne(spec);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals("alice", result.getUsername());
    }

    @Test
    void findOne_noMatchingRow_returnsNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        UserDto result = executor.findOne(spec);
        org.junit.jupiter.api.Assertions.assertNull(result);
    }

    @Test
    void findOne_multipleMatchingRows_returnsFirstOnly() {
        // ACTIVE users: alice and charlie — findOne should return exactly one
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        UserDto result = executor.findOne(spec);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals("alice", result.getUsername()); // first alphabetically
    }

    @Test
    void findOne_withPageable_appliesSortAndReturnsFirst() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        // Sort descending: charlie comes first
        JPageable<TUser> pageable = JPageable.of(0, 10, JSort.byDesc(TUser::getUsername));
        UserDto result = executor.findOne(spec, pageable);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals("charlie", result.getUsername());
    }

    @Test
    void findOne_withPageable_noMatchReturnsNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 10, JSort.byAsc(TUser::getUsername));
        UserDto result = executor.findOne(spec, pageable);
        org.junit.jupiter.api.Assertions.assertNull(result);
    }

    // ------------------------------------------------------------------ //
    //  select with JPageable (pagination without count)
    // ------------------------------------------------------------------ //

    @Test
    void select_withPageable_appliesPaginationWithoutCount() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2, JSort.byAsc(TUser::getUsername));
        List<UserDto> result = executor.select(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice", "bob"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void select_withPageable_secondPage() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(1, 2, JSort.byAsc(TUser::getUsername));
        List<UserDto> result = executor.select(spec, pageable);

        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void select_withUnpagedSort_returnsAllRowsInSortOrder() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec, JPageable.unpaged(JSort.byDesc(TUser::getUsername)));

        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie", "bob", "alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void selectPage_withUnpaged_throwsIllegalArgumentException() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        Throwable assertionThrown1 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> executor.selectPage(spec, JPageable.unpaged()));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown1.getMessage().contains("requires a paged JPageable"));
    }

    @Test
    void select_withPageable_noMatchReturnsEmptyList() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 10);
        List<UserDto> result = executor.select(spec, pageable);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------ //
    //  JOIN
    // ------------------------------------------------------------------ //

    @Test
    void select_withInnerJoin_onUserOrders() {
        SelectSpec<TOrder, OrderDto> spec = SelectBuilder.from(TOrder.class, "o")
                .select(TOrder::getId, TOrder::getOrderNo, TOrder::getAmount)
                .join(TUser.class, "u", JoinType.INNER, ob -> ob.eq(TOrder::getUserId, "o", TUser::getId, "u"))
                .where(w -> w.eq(TUser::getStatus, "u", "ACTIVE"))
                .mapTo(OrderDto.class);

        List<OrderDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("ORD-001", "ORD-002")),
                new java.util.HashSet<>(
                        (result).stream().map(OrderDto::getOrderNo).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(OrderDto::getOrderNo).toList().size());
    }

    // ------------------------------------------------------------------ //
    //  JSort / JPageable output adapters
    // ------------------------------------------------------------------ //

    @Test
    void jSort_toSpringSort_works() {
        JSort<TUser> sort = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        org.springframework.data.domain.Sort springSort = sort.toSpringSort();
        org.junit.jupiter.api.Assertions.assertTrue(springSort.isSorted());
    }

    @Test
    void jPageable_toSpringPageable_works() {
        JPageable<TUser> pageable = JPageable.of(1, 10, JSort.byAsc(TUser::getUsername));
        org.springframework.data.domain.Pageable springPageable = pageable.toSpringPageable();
        org.junit.jupiter.api.Assertions.assertEquals(1, springPageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, springPageable.getPageSize());
    }

    // ------------------------------------------------------------------ //
    //  WhereBuilder condition overloads
    // ------------------------------------------------------------------ //

    @Test
    void select_conditionFalse_skips() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE", false) // skipped
                        .eq(TUser::getStatus, "ACTIVE", true)) // applied
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "charlie")),
                new java.util.HashSet<>(
                        (result).stream().map(UserDto::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(UserDto::getUsername).toList().size());
    }

    // ------------------------------------------------------------------ //
    //  mapToEntity: expand all columns, setter mapping
    // ------------------------------------------------------------------ //

    @Test
    void mapToEntity_noExplicitSelect_returnsFullyPopulatedEntities() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();

        List<TUser> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        // All fields should be non-null (populated via setter/field injection)
        (result).forEach(u -> {
            org.junit.jupiter.api.Assertions.assertNotNull(u.getId());
            org.junit.jupiter.api.Assertions.assertNotNull(u.getUsername());
            org.junit.jupiter.api.Assertions.assertNotNull(u.getStatus());
        });
    }

    @Test
    void mapToEntity_withWhere_returnsFilteredEntities() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        List<TUser> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("ACTIVE")),
                new java.util.HashSet<>((result).stream().map(TUser::getStatus).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "charlie")),
                new java.util.HashSet<>(
                        (result).stream().map(TUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(TUser::getUsername).toList().size());
    }

    @Test
    void mapToEntity_orderEntity_returnsAllFields() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o").mapToEntity();

        List<TOrder> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        (result).forEach(o -> {
            org.junit.jupiter.api.Assertions.assertNotNull(o.getId());
            org.junit.jupiter.api.Assertions.assertNotNull(o.getOrderNo());
            org.junit.jupiter.api.Assertions.assertNotNull(o.getAmount());
        });
    }

    // ------------------------------------------------------------------ //
    //  DTO setter mapping
    // ------------------------------------------------------------------ //

    @Test
    void mapToDto_setterMapping_allFieldsPopulated() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        (result).forEach(dto -> {
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getId());
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getUsername());
        });
    }

    // ------------------------------------------------------------------ //
    //  Expression alias (.as("alias"))
    // ------------------------------------------------------------------ //

    /**
     * DTO that can receive an aliased function expression result.
     * Uses JavaBean style for setter mapping.
     */
    static class EmailUpperDto {
        private String emailUpper;

        public String getEmailUpper() {
            return emailUpper;
        }

        public void setEmailUpper(String emailUpper) {
            this.emailUpper = emailUpper;
        }
    }

    @Test
    void selectWithAlias_functionExpression_mappedToDto() {
        SelectSpec<TUser, EmailUpperDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .select(upper(TUser::getEmail).as("emailUpper"))
                .mapTo(EmailUpperDto.class);

        List<EmailUpperDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ALICE@EXAMPLE.COM", result.get(0).getEmailUpper());
    }

    // ------------------------------------------------------------------ //
    //  Join: note on column conflicts
    // ------------------------------------------------------------------ //

    @Test
    void select_joinConflict_explicitSelectResolves() {
        // Both TOrder and TUser have an "id" column. Explicitly selecting and aliasing avoids conflict.
        // Here we only select order fields to avoid the ambiguity.
        SelectSpec<TOrder, OrderDto> spec = SelectBuilder.from(TOrder.class, "o")
                .select(TOrder::getId, TOrder::getOrderNo, TOrder::getAmount)
                .join(TUser.class, "u", JoinType.INNER, ob -> ob.eq(TOrder::getUserId, "o", TUser::getId, "u"))
                .where(w -> w.eq(TUser::getStatus, "u", "ACTIVE"))
                .mapTo(OrderDto.class);

        List<OrderDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("ORD-001", "ORD-002")),
                new java.util.HashSet<>(
                        (result).stream().map(OrderDto::getOrderNo).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(OrderDto::getOrderNo).toList().size());
        // id and amount should be populated via setter
        (result).forEach(dto -> {
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getId());
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getAmount());
        });
    }

    // ------------------------------------------------------------------ //
    //  UpdateBuilder / executeUpdate
    // ------------------------------------------------------------------ //

    @Test
    void executeUpdate_singleField_updatesCorrectRow() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "DISABLED")
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        // Verify the change
        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("DISABLED", result.get(0).getStatus());
    }

    @Test
    void executeUpdate_multipleFields_updatesAllColumns() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SUSPENDED")
                .set(TUser::getAge, 99)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("SUSPENDED", result.get(0).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(99, result.get(0).getAge());
    }

    @Test
    void executeUpdate_noMatchingRows_returnsZero() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "GHOST")
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(0, affected);
    }

    @Test
    void updateBuilder_noAssignments_withWhere_doesNotThrow() {
        // set-empty check has been removed; the database will report an error for empty SET
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    @Test
    void executeUpdate_setWithConditionTrue_updatesColumn() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "CONDITION_TRUE", true)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "CONDITION_TRUE", result.get(0).getStatus());
    }

    @Test
    void executeUpdate_setWithConditionFalse_skipsColumn() {
        // First capture current status for bob
        SelectSpec<TUser, TUser> before = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        String originalStatus = executor.select(before).get(0).getStatus();

        // condition=false: the set should be a no-op
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getAge, 77)
                .set(TUser::getStatus, "SKIPPED", false)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        executor.executeUpdate(spec);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        // age updated, status unchanged
        org.junit.jupiter.api.Assertions.assertEquals(77, result.get(0).getAge());
        org.junit.jupiter.api.Assertions.assertEquals(
                originalStatus, result.get(0).getStatus());
    }

    @Test
    void updateBuilder_allConditionsFalse_withWhere_doesNotThrow() {
        // set-empty check has been removed; empty assignments are passed through to the database
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SKIPPED", false)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    @Test
    void updateBuilder_noWhere_throwsIllegalStateByDefault() {
        // build() without WHERE throws by default to prevent accidental full-table updates
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .build());
    }

    @Test
    void updateBuilder_noWhere_buildUnsafe_doesNotThrow() {
        // buildUnsafe() bypasses the WHERE guard; verify it does not throw
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .buildUnsafe();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    // ------------------------------------------------------------------ //
    //  DeleteBuilder / executeDelete
    // ------------------------------------------------------------------ //

    @Test
    void executeDelete_singleCondition_deletesMatchingRow() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        // Verify deletion
        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void executeDelete_multipleConditions_deletesOnlyMatching() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").gt(TUser::getAge, 35))
                .build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected); // only charlie (age=40, ACTIVE) matches

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class).mapToEntity();
        List<TUser> remaining = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "bob")),
                new java.util.HashSet<>(
                        (remaining).stream().map(TUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (remaining).stream().map(TUser::getUsername).toList().size());
    }

    @Test
    void deleteBuilder_noWhereCondition_throwsIllegalState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> DeleteBuilder.from(TUser.class)
                .build());
    }

    @Test
    void deleteBuilder_buildUnsafe_allowsNoWhere() {
        // buildUnsafe() bypasses the WHERE guard; we verify it doesn't throw
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class).buildUnsafe();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    // ------------------------------------------------------------------ //
    //  WhereBuilder condition overloads (comprehensive)
    // ------------------------------------------------------------------ //

    @Test
    void whereCondition_allFalse_selectReturnsAllRows() {
        // When all conditions are false the WHERE clause is empty → all rows returned
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE", false)
                        .like(TUser::getUsername, "%ali%", false)
                        .gt(TUser::getAge, 50, false))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size()); // no WHERE → all rows
    }

    @Test
    void whereCondition_trueWithNullValue_addsPredicate() {
        // condition=true and value=null → predicate IS added (even though value is null)
        // This should produce "username = :p1" with p1=null
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, null, true))
                .mapTo(UserDto.class);

        // No user has username=null → 0 results, but the query must execute without error
        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void whereCondition_likeIgnoreCase_matchesCaseInsensitively() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.likeIgnoreCase(TUser::getUsername, "%ALI%"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void whereCondition_likeIgnoreCaseFalse_skips() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.likeIgnoreCase(TUser::getUsername, "%ALI%", false) // skipped
                        .eq(TUser::getStatus, "INACTIVE", true)) // applied
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("bob"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    // ------------------------------------------------------------------ //
    //  JdbcDslExecutor: save / saveNonNull / updateById / deleteById
    // ------------------------------------------------------------------ //

    @Test
    void save_insertsAllColumns() {
        TUser newUser = new TUser("dave", "dave@example.com", 28, "ACTIVE");
        executor.save(newUser);
        // Generated IDENTITY pk must be set back
        org.junit.jupiter.api.Assertions.assertNotNull(newUser.getId());

        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "dave"))
                .mapToEntity();
        List<TUser> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "dave@example.com", result.get(0).getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(28, result.get(0).getAge());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    void save_withInsertSpec_insertsSpecifiedColumns() {
        // Only insert username and status; email and age are omitted from the spec
        InsertSpec<TUser> spec =
                InsertBuilder.into(TUser.class).columns("username", "status").build();
        TUser newUser = new TUser("eve", "eve@example.com", 33, "ACTIVE");
        executor.save(spec, newUser);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "eve"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("eve", result.get(0).getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", result.get(0).getStatus());
        // email and age were not in spec → should be null / default
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).getEmail());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).getAge());
    }

    @Test
    void save_withInsertSpec_typeSafeColumns_insertsSpecifiedColumns() {
        // Same as save_withInsertSpec_insertsSpecifiedColumns but uses type-safe method references
        InsertSpec<TUser> spec = InsertBuilder.into(TUser.class)
                .columns(TUser::getUsername, TUser::getStatus)
                .build();
        TUser newUser = new TUser("eve2", "eve2@example.com", 34, "ACTIVE");
        executor.save(spec, newUser);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "eve2"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("eve2", result.get(0).getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", result.get(0).getStatus());
        // email and age were not in spec → should be null / default
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).getEmail());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).getAge());
    }

    @Test
    void save_withEmptyInsertSpec_insertsAllColumns() {
        InsertSpec<TUser> spec = InsertSpec.of(TUser.class); // no explicit columns
        TUser newUser = new TUser("frank", "frank@example.com", 45, "ACTIVE");
        executor.save(spec, newUser);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "frank"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "frank@example.com", result.get(0).getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(45, result.get(0).getAge());
    }

    @Test
    void saveNonNull_insertsOnlyNonNullColumns() {
        // email is null → should not be inserted (column will have DB default / null)
        TUser newUser = new TUser("grace", null, 22, "ACTIVE");
        executor.saveNonNull(newUser);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "grace"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("grace", result.get(0).getUsername());
        org.junit.jupiter.api.Assertions.assertEquals(22, result.get(0).getAge());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).getEmail()); // omitted from insert
    }

    @Test
    void updateById_updatesAllNonPkColumns() {
        // Fetch alice's id first
        TUser alice = executor.select(SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .mapToEntity())
                .get(0);

        alice.setEmail("alice_new@example.com");
        alice.setAge(99);
        int affected = executor.updateById(alice);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser updated = executor.select(SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .mapToEntity())
                .get(0);
        org.junit.jupiter.api.Assertions.assertEquals("alice_new@example.com", updated.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(99, updated.getAge());
    }

    @Test
    void deleteById_deletesMatchingRow() {
        TUser bob = executor.select(SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "bob"))
                        .mapToEntity())
                .get(0);

        int affected = executor.deleteById(TUser.class, bob.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertFalse(
                (remaining).stream().map(TUser::getUsername).toList().contains("bob"));
    }

    // ------------------------------------------------------------------ //
    //  UpdateBuilder – direct WHERE shortcut methods
    // ------------------------------------------------------------------ //

    @Test
    void updateBuilder_directEq_updatesMatchingRow() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "DISABLED")
                .eq(TUser::getUsername, "bob")
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser bob = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals("DISABLED", bob.getStatus());
    }

    @Test
    void updateBuilder_directGt_updatesOnlyOlderRows() {
        // Only charlie (age=40) satisfies age > 35
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SENIOR")
                .gt(TUser::getAge, 35)
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals("SENIOR", charlie.getStatus());
    }

    @Test
    void updateBuilder_directMultipleConditions_accumulated() {
        // eq + gt combined with AND: status=ACTIVE AND age > 35 → only charlie
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "VETERAN")
                .eq(TUser::getStatus, "ACTIVE")
                .gt(TUser::getAge, 35)
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals("VETERAN", charlie.getStatus());

        // alice (ACTIVE, age=25) should be unchanged
        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", alice.getStatus());
    }

    @Test
    void updateBuilder_directEqConditionFalse_skips() {
        // condition=false on the first predicate; condition=true on the second
        // Only eq(username, "alice", true) should be in the WHERE clause
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getAge, 55)
                .eq(TUser::getStatus, "INACTIVE", false) // skipped
                .eq(TUser::getUsername, "alice", true) // applied
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(55, alice.getAge());

        // bob's age must not be changed
        TUser bob = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertNotEquals(55, bob.getAge());
    }

    @Test
    void updateBuilder_directAndLambdaWhereAccumulated() {
        // Direct method + lambda where are accumulated with AND
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getAge, 77)
                .eq(TUser::getStatus, "ACTIVE")
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();

        int affected = executor.executeUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(77, alice.getAge());

        // charlie is also ACTIVE but username != alice → must not be updated
        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertNotEquals(77, charlie.getAge());
    }

    // ------------------------------------------------------------------ //
    //  DeleteBuilder – direct WHERE shortcut methods
    // ------------------------------------------------------------------ //

    @Test
    void deleteBuilder_directEq_deletesMatchingRow() {
        DeleteSpec<TUser> spec =
                DeleteBuilder.from(TUser.class).eq(TUser::getUsername, "bob").build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertFalse(
                (remaining).stream().map(TUser::getUsername).toList().contains("bob"));
    }

    @Test
    void deleteBuilder_directMultipleConditions_accumulated() {
        // eq(status=ACTIVE) AND gt(age > 35) → only charlie
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "ACTIVE")
                .gt(TUser::getAge, 35)
                .build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "bob")),
                new java.util.HashSet<>(
                        (remaining).stream().map(TUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (remaining).stream().map(TUser::getUsername).toList().size());
    }

    @Test
    void deleteBuilder_directEqConditionFalse_skips() {
        // condition=false means the predicate is not added
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "INACTIVE", false) // skipped
                .eq(TUser::getUsername, "bob", true) // applied
                .build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "charlie")),
                new java.util.HashSet<>(
                        (remaining).stream().map(TUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (remaining).stream().map(TUser::getUsername).toList().size());
    }

    @Test
    void deleteBuilder_directAndLambdaWhereAccumulated() {
        // Direct method + lambda where are both AND-ed together
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "ACTIVE")
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();

        int affected = executor.executeDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, affected);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        // charlie is also ACTIVE but was not deleted (username != alice)
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("bob", "charlie")),
                new java.util.HashSet<>(
                        (remaining).stream().map(TUser::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (remaining).stream().map(TUser::getUsername).toList().size());
    }

    // ------------------------------------------------------------------ //
    //  Feature: LIKE raw pattern (no auto-%% wrapping)
    // ------------------------------------------------------------------ //

    @Test
    void like_startsWith_returnsMatching() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "ali%"))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void like_endsWith_returnsMatching() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "%ice"))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    @Test
    void like_noWildcard_exactMatch() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "alice"))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    // ------------------------------------------------------------------ //
    //  Feature: WhereBuilder.not()
    // ------------------------------------------------------------------ //

    @Test
    void whereNot_excludesMatchingRows() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.not(n -> n.eq(TUser::getStatus, "INACTIVE")))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        // bob is INACTIVE; alice and charlie are ACTIVE → 2 rows returned
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("alice", "charlie")),
                new java.util.HashSet<>(
                        (result).stream().map(UserDto::getUsername).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(UserDto::getUsername).toList().size());
    }

    // ------------------------------------------------------------------ //
    //  Feature: UPDATE SET expression assignment
    // ------------------------------------------------------------------ //

    @Test
    void updateSet_expression_incrementsAge() {
        // SET age = age + 10 WHERE username = 'alice'
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .setExpr(TUser::getAge, lit("age + 10"))
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        executor.executeUpdate(spec);

        List<TUser> result = executor.select(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        // alice's original age is 30 (from test data), after +10 should be 40
        org.junit.jupiter.api.Assertions.assertEquals(40, result.get(0).getAge());
    }

    // ------------------------------------------------------------------ //
    //  Feature: Derived-table subquery as FROM
    // ------------------------------------------------------------------ //

    @Test
    void fromSubquery_filtersViaOuterQuery() {
        // Inner: all ACTIVE users; outer: filter by username
        SelectSpec<TUser, TUser> inner = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.fromSubquery(inner, "sub", TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(outer);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("alice"),
                (result).stream().map(UserDto::getUsername).toList());
    }

    // ------------------------------------------------------------------ //
    //  Feature: Batch UPDATE / Batch DELETE
    // ------------------------------------------------------------------ //

    @Test
    void executeBatchUpdate_multipleSpecs_updatesAllRows() {
        // Update alice status → INACTIVE, bob status → ACTIVE
        UpdateSpec<TUser> spec1 = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        UpdateSpec<TUser> spec2 = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "ACTIVE")
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int[] results = executor.executeBatchUpdate(List.of(spec1, spec2));
        org.junit.jupiter.api.Assertions.assertEquals(2, results.length);
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList(1)),
                java.util.Arrays.stream(results).boxed().collect(java.util.stream.Collectors.toSet()));

        // Verify
        SelectSpec<TUser, TUser> alice = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity();
        org.junit.jupiter.api.Assertions.assertEquals(1, executor.select(alice).size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("INACTIVE"),
                (executor.select(alice)).stream().map(TUser::getStatus).toList());

        SelectSpec<TUser, TUser> bob = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        org.junit.jupiter.api.Assertions.assertEquals(1, executor.select(bob).size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("ACTIVE"),
                (executor.select(bob)).stream().map(TUser::getStatus).toList());
    }

    @Test
    void executeBatchUpdate_emptyList_returnsEmptyArray() {
        int[] results = executor.executeBatchUpdate(List.of());
        org.junit.jupiter.api.Assertions.assertEquals(0, results.length);
    }

    @Test
    void executeBatchDelete_multipleSpecs_deletesMatchingRows() {
        // Delete alice and bob, leave charlie
        DeleteSpec<TUser> spec1 = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        DeleteSpec<TUser> spec2 = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int[] results = executor.executeBatchDelete(List.of(spec1, spec2));
        org.junit.jupiter.api.Assertions.assertEquals(2, results.length);
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList(1)),
                java.util.Arrays.stream(results).boxed().collect(java.util.stream.Collectors.toSet()));

        // Only charlie should remain
        SelectSpec<TUser, TUser> all = SelectBuilder.from(TUser.class).mapToEntity();
        List<TUser> remaining = executor.select(all);
        org.junit.jupiter.api.Assertions.assertEquals(1, remaining.size());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("charlie"),
                (remaining).stream().map(TUser::getUsername).toList());
    }

    @Test
    void executeBatchDelete_emptyList_returnsEmptyArray() {
        int[] results = executor.executeBatchDelete(List.of());
        org.junit.jupiter.api.Assertions.assertEquals(0, results.length);
    }

    // ------------------------------------------------------------------ //
    //  Feature: UNION ORDER BY
    // ------------------------------------------------------------------ //

    @Test
    void unionOrderBy_sortsDescending() {
        SelectSpec<TUser, UserDto> active = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> inactive = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE"))
                .mapTo(UserDto.class);

        UnionSpec<UserDto> union = UnionSpec.of(active)
                .unionAll(inactive)
                .orderBy(JSort.byDesc(TUser::getUsername))
                .build();

        List<UserDto> result = executor.union(union);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        // Descending: charlie, bob, alice
        org.junit.jupiter.api.Assertions.assertEquals("charlie", result.get(0).getUsername());
        org.junit.jupiter.api.Assertions.assertEquals(
                "alice", result.get(result.size() - 1).getUsername());
    }
}
