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

import java.math.BigDecimal;
import java.util.List;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.lit;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.upper;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(result).hasSize(3);
    }

    @Test
    void select_byStatus_returnsFiltered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void select_like_returnsMatching() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "%ali%"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("alice");
    }

    @Test
    void select_between_ageRange() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.between(TUser::getAge, 25, 31))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void select_in_returnsMatchingStatuses() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getStatus, List.of("ACTIVE", "INACTIVE")))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(3);
    }

    @Test
    void select_isNull_email() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.isNull(TUser::getEmail))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void select_orderBy_ascending() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).extracting(UserDto::getUsername)
                .containsExactly("alice", "bob", "charlie");
    }

    @Test
    void select_orderBy_descending() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byDesc(TUser::getUsername))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).extracting(UserDto::getUsername)
                .containsExactly("charlie", "bob", "alice");
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

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactly("alice", "bob");
    }

    @Test
    void selectPage_secondPage_sql2008Dialect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(1, 2);
        Page<UserDto> page = executor.selectPage(spec, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void selectPage_mysqlDialect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2);
        Page<UserDto> page = mysqlExecutor.selectPage(spec, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void selectPage_withPageableSort() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 2, JSort.byAsc(TUser::getUsername));
        Page<UserDto> page = executor.selectPage(spec, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(UserDto::getUsername)
                .containsExactly("alice", "bob");
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
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void findOne_noMatchingRow_returnsNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        UserDto result = executor.findOne(spec);
        assertThat(result).isNull();
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
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("alice"); // first alphabetically
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
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("charlie");
    }

    @Test
    void findOne_withPageable_noMatchReturnsNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 10, JSort.byAsc(TUser::getUsername));
        UserDto result = executor.findOne(spec, pageable);
        assertThat(result).isNull();
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

        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactly("alice", "bob");
    }

    @Test
    void select_withPageable_secondPage() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(1, 2, JSort.byAsc(TUser::getUsername));
        List<UserDto> result = executor.select(spec, pageable);

        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void select_withPageable_noMatchReturnsEmptyList() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .mapTo(UserDto.class);

        JPageable<TUser> pageable = JPageable.of(0, 10);
        List<UserDto> result = executor.select(spec, pageable);
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ //
    //  JOIN
    // ------------------------------------------------------------------ //

    @Test
    void select_withInnerJoin_onUserOrders() {
        SelectSpec<TOrder, OrderDto> spec = SelectBuilder.from(TOrder.class, "o")
                .select(TOrder::getId, TOrder::getOrderNo, TOrder::getAmount)
                .join(TUser.class, "u", JoinType.INNER,
                        ob -> ob.eq(TOrder::getUserId, "o", TUser::getId, "u"))
                .where(w -> w.eq(TUser::getStatus, "u", "ACTIVE"))
                .mapTo(OrderDto.class);

        List<OrderDto> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(OrderDto::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-002");
    }

    // ------------------------------------------------------------------ //
    //  JSort / JPageable output adapters
    // ------------------------------------------------------------------ //

    @Test
    void jSort_toSpringSort_works() {
        JSort<TUser> sort = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        org.springframework.data.domain.Sort springSort = sort.toSpringSort();
        assertThat(springSort.isSorted()).isTrue();
    }

    @Test
    void jPageable_toSpringPageable_works() {
        JPageable<TUser> pageable = JPageable.of(1, 10, JSort.byAsc(TUser::getUsername));
        org.springframework.data.domain.Pageable springPageable = pageable.toSpringPageable();
        assertThat(springPageable.getPageNumber()).isEqualTo(1);
        assertThat(springPageable.getPageSize()).isEqualTo(10);
    }

    // ------------------------------------------------------------------ //
    //  WhereBuilder condition overloads
    // ------------------------------------------------------------------ //

    @Test
    void select_conditionFalse_skips() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "INACTIVE", false)  // skipped
                        .eq(TUser::getStatus, "ACTIVE", true))    // applied
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    // ------------------------------------------------------------------ //
    //  mapToEntity: expand all columns, setter mapping
    // ------------------------------------------------------------------ //

    @Test
    void mapToEntity_noExplicitSelect_returnsFullyPopulatedEntities() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .mapToEntity();

        List<TUser> result = executor.select(spec);
        assertThat(result).hasSize(3);
        // All fields should be non-null (populated via setter/field injection)
        assertThat(result).allSatisfy(u -> {
            assertThat(u.getId()).as("id must not be null").isNotNull();
            assertThat(u.getUsername()).as("username must not be null").isNotNull();
            assertThat(u.getStatus()).as("status must not be null").isNotNull();
        });
    }

    @Test
    void mapToEntity_withWhere_returnsFilteredEntities() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        List<TUser> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(TUser::getStatus)
                .containsOnly("ACTIVE");
        assertThat(result).extracting(TUser::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void mapToEntity_orderEntity_returnsAllFields() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .mapToEntity();

        List<TOrder> result = executor.select(spec);
        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(o -> {
            assertThat(o.getId()).as("id must not be null").isNotNull();
            assertThat(o.getOrderNo()).as("orderNo must not be null").isNotNull();
            assertThat(o.getAmount()).as("amount must not be null").isNotNull();
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
        assertThat(result).hasSize(3)
                .allSatisfy(dto -> {
                    assertThat(dto.getId()).as("id must not be null").isNotNull();
                    assertThat(dto.getUsername()).as("username must not be null").isNotNull();
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
        public String getEmailUpper() { return emailUpper; }
        public void setEmailUpper(String emailUpper) { this.emailUpper = emailUpper; }
    }

    @Test
    void selectWithAlias_functionExpression_mappedToDto() {
        SelectSpec<TUser, EmailUpperDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .select(upper(TUser::getEmail).as("emailUpper"))
                .mapTo(EmailUpperDto.class);

        List<EmailUpperDto> result = executor.select(spec);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmailUpper()).isEqualTo("ALICE@EXAMPLE.COM");
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
                .join(TUser.class, "u", JoinType.INNER,
                        ob -> ob.eq(TOrder::getUserId, "o", TUser::getId, "u"))
                .where(w -> w.eq(TUser::getStatus, "u", "ACTIVE"))
                .mapTo(OrderDto.class);

        List<OrderDto> result = executor.select(spec);
        assertThat(result).hasSize(2)
                .extracting(OrderDto::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-002");
        // id and amount should be populated via setter
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.getId()).isNotNull();
            assertThat(dto.getAmount()).isNotNull();
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
        assertThat(affected).isEqualTo(1);

        // Verify the change
        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void executeUpdate_multipleFields_updatesAllColumns() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SUSPENDED")
                .set(TUser::getAge, 99)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();

        int affected = executor.executeUpdate(spec);
        assertThat(affected).isEqualTo(1);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SUSPENDED");
        assertThat(result.get(0).getAge()).isEqualTo(99);
    }

    @Test
    void executeUpdate_noMatchingRows_returnsZero() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "GHOST")
                .where(w -> w.eq(TUser::getUsername, "nonexistent"))
                .build();

        int affected = executor.executeUpdate(spec);
        assertThat(affected).isEqualTo(0);
    }

    @Test
    void updateBuilder_noAssignments_withWhere_doesNotThrow() {
        // set-empty check has been removed; the database will report an error for empty SET
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        assertThat(spec).isNotNull();
    }

    @Test
    void executeUpdate_setWithConditionTrue_updatesColumn() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "CONDITION_TRUE", true)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        int affected = executor.executeUpdate(spec);
        assertThat(affected).isEqualTo(1);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("CONDITION_TRUE");
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
        assertThat(result).hasSize(1);
        // age updated, status unchanged
        assertThat(result.get(0).getAge()).isEqualTo(77);
        assertThat(result.get(0).getStatus()).isEqualTo(originalStatus);
    }

    @Test
    void updateBuilder_allConditionsFalse_withWhere_doesNotThrow() {
        // set-empty check has been removed; empty assignments are passed through to the database
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SKIPPED", false)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        assertThat(spec).isNotNull();
    }

    @Test
    void updateBuilder_noWhere_throwsIllegalStateByDefault() {
        // build() without WHERE throws by default to prevent accidental full-table updates
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                UpdateBuilder.from(TUser.class)
                        .set(TUser::getStatus, "INACTIVE")
                        .build()
        );
    }

    @Test
    void updateBuilder_noWhere_buildUnsafe_doesNotThrow() {
        // buildUnsafe() bypasses the WHERE guard; verify it does not throw
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .buildUnsafe();
        assertThat(spec).isNotNull();
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
        assertThat(affected).isEqualTo(1);

        // Verify deletion
        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        assertThat(result).isEmpty();
    }

    @Test
    void executeDelete_multipleConditions_deletesOnlyMatching() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").gt(TUser::getAge, 35))
                .build();

        int affected = executor.executeDelete(spec);
        assertThat(affected).isEqualTo(1); // only charlie (age=40, ACTIVE) matches

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class).mapToEntity();
        List<TUser> remaining = executor.select(verify);
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void deleteBuilder_noWhereCondition_throwsIllegalState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                DeleteBuilder.from(TUser.class).build()
        );
    }

    @Test
    void deleteBuilder_buildUnsafe_allowsNoWhere() {
        // buildUnsafe() bypasses the WHERE guard; we verify it doesn't throw
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class).buildUnsafe();
        assertThat(spec).isNotNull();
    }

    // ------------------------------------------------------------------ //
    //  WhereBuilder condition overloads (comprehensive)
    // ------------------------------------------------------------------ //

    @Test
    void whereCondition_allFalse_selectReturnsAllRows() {
        // When all conditions are false the WHERE clause is empty → all rows returned
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE", false)
                        .like(TUser::getUsername, "%ali%", false)
                        .gt(TUser::getAge, 50, false))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(3); // no WHERE → all rows
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
        assertThat(result).isEmpty();
    }

    @Test
    void whereCondition_likeIgnoreCase_matchesCaseInsensitively() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.likeIgnoreCase(TUser::getUsername, "%ALI%"))
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("alice");
    }

    @Test
    void whereCondition_likeIgnoreCaseFalse_skips() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .likeIgnoreCase(TUser::getUsername, "%ALI%", false)  // skipped
                        .eq(TUser::getStatus, "INACTIVE", true))           // applied
                .mapTo(UserDto.class);

        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1)
                .extracting(UserDto::getUsername)
                .containsExactly("bob");
    }

    // ------------------------------------------------------------------ //
    //  JdbcDslExecutor: save / saveNonNull / updateById / deleteById
    // ------------------------------------------------------------------ //

    @Test
    void save_insertsAllColumns() {
        TUser newUser = new TUser("dave", "dave@example.com", 28, "ACTIVE");
        executor.save(newUser);
        // Generated IDENTITY pk must be set back
        assertThat(newUser.getId()).isNotNull();

        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "dave"))
                .mapToEntity();
        List<TUser> result = executor.select(spec);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("dave@example.com");
        assertThat(result.get(0).getAge()).isEqualTo(28);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void save_withInsertSpec_insertsSpecifiedColumns() {
        // Only insert username and status; email and age are omitted from the spec
        InsertSpec<TUser> spec = InsertBuilder.into(TUser.class)
                .columns("username", "status")
                .build();
        TUser newUser = new TUser("eve", "eve@example.com", 33, "ACTIVE");
        executor.save(spec, newUser);

        SelectSpec<TUser, TUser> verify = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "eve"))
                .mapToEntity();
        List<TUser> result = executor.select(verify);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("eve");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        // email and age were not in spec → should be null / default
        assertThat(result.get(0).getEmail()).isNull();
        assertThat(result.get(0).getAge()).isNull();
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
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("eve2");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        // email and age were not in spec → should be null / default
        assertThat(result.get(0).getEmail()).isNull();
        assertThat(result.get(0).getAge()).isNull();
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
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("frank@example.com");
        assertThat(result.get(0).getAge()).isEqualTo(45);
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
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("grace");
        assertThat(result.get(0).getAge()).isEqualTo(22);
        assertThat(result.get(0).getEmail()).isNull(); // omitted from insert
    }

    @Test
    void updateById_updatesAllNonPkColumns() {
        // Fetch alice's id first
        TUser alice = executor.select(
                SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .mapToEntity()).get(0);

        alice.setEmail("alice_new@example.com");
        alice.setAge(99);
        int affected = executor.updateById(alice);
        assertThat(affected).isEqualTo(1);

        TUser updated = executor.select(
                SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .mapToEntity()).get(0);
        assertThat(updated.getEmail()).isEqualTo("alice_new@example.com");
        assertThat(updated.getAge()).isEqualTo(99);
    }

    @Test
    void deleteById_deletesMatchingRow() {
        TUser bob = executor.select(
                SelectBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "bob"))
                        .mapToEntity()).get(0);

        int affected = executor.deleteById(TUser.class, bob.getId());
        assertThat(affected).isEqualTo(1);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .doesNotContain("bob");
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
        assertThat(affected).isEqualTo(1);

        TUser bob = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob")).mapToEntity());
        assertThat(bob.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void updateBuilder_directGt_updatesOnlyOlderRows() {
        // Only charlie (age=40) satisfies age > 35
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "SENIOR")
                .gt(TUser::getAge, 35)
                .build();

        int affected = executor.executeUpdate(spec);
        assertThat(affected).isEqualTo(1);

        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie")).mapToEntity());
        assertThat(charlie.getStatus()).isEqualTo("SENIOR");
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
        assertThat(affected).isEqualTo(1);

        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie")).mapToEntity());
        assertThat(charlie.getStatus()).isEqualTo("VETERAN");

        // alice (ACTIVE, age=25) should be unchanged
        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice")).mapToEntity());
        assertThat(alice.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateBuilder_directEqConditionFalse_skips() {
        // condition=false on the first predicate; condition=true on the second
        // Only eq(username, "alice", true) should be in the WHERE clause
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getAge, 55)
                .eq(TUser::getStatus, "INACTIVE", false)   // skipped
                .eq(TUser::getUsername, "alice", true)      // applied
                .build();

        int affected = executor.executeUpdate(spec);
        assertThat(affected).isEqualTo(1);

        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice")).mapToEntity());
        assertThat(alice.getAge()).isEqualTo(55);

        // bob's age must not be changed
        TUser bob = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob")).mapToEntity());
        assertThat(bob.getAge()).isNotEqualTo(55);
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
        assertThat(affected).isEqualTo(1);

        TUser alice = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice")).mapToEntity());
        assertThat(alice.getAge()).isEqualTo(77);

        // charlie is also ACTIVE but username != alice → must not be updated
        TUser charlie = executor.findOne(SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "charlie")).mapToEntity());
        assertThat(charlie.getAge()).isNotEqualTo(77);
    }

    // ------------------------------------------------------------------ //
    //  DeleteBuilder – direct WHERE shortcut methods
    // ------------------------------------------------------------------ //

    @Test
    void deleteBuilder_directEq_deletesMatchingRow() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getUsername, "bob")
                .build();

        int affected = executor.executeDelete(spec);
        assertThat(affected).isEqualTo(1);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .doesNotContain("bob");
    }

    @Test
    void deleteBuilder_directMultipleConditions_accumulated() {
        // eq(status=ACTIVE) AND gt(age > 35) → only charlie
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "ACTIVE")
                .gt(TUser::getAge, 35)
                .build();

        int affected = executor.executeDelete(spec);
        assertThat(affected).isEqualTo(1);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void deleteBuilder_directEqConditionFalse_skips() {
        // condition=false means the predicate is not added
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "INACTIVE", false)  // skipped
                .eq(TUser::getUsername, "bob", true)       // applied
                .build();

        int affected = executor.executeDelete(spec);
        assertThat(affected).isEqualTo(1);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void deleteBuilder_directAndLambdaWhereAccumulated() {
        // Direct method + lambda where are both AND-ed together
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .eq(TUser::getStatus, "ACTIVE")
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();

        int affected = executor.executeDelete(spec);
        assertThat(affected).isEqualTo(1);

        List<TUser> remaining = executor.select(SelectBuilder.from(TUser.class).mapToEntity());
        // charlie is also ACTIVE but was not deleted (username != alice)
        assertThat(remaining).hasSize(2)
                .extracting(TUser::getUsername)
                .containsExactlyInAnyOrder("bob", "charlie");
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
        assertThat(result).hasSize(1).extracting(UserDto::getUsername).containsExactly("alice");
    }

    @Test
    void like_endsWith_returnsMatching() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "%ice"))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1).extracting(UserDto::getUsername).containsExactly("alice");
    }

    @Test
    void like_noWildcard_exactMatch() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "alice"))
                .mapTo(UserDto.class);
        List<UserDto> result = executor.select(spec);
        assertThat(result).hasSize(1).extracting(UserDto::getUsername).containsExactly("alice");
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
        assertThat(result).hasSize(2)
                .extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "charlie");
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
        assertThat(result).hasSize(1);
        // alice's original age is 30 (from test data), after +10 should be 40
        assertThat(result.get(0).getAge()).isEqualTo(40);
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
        assertThat(result).hasSize(1).extracting(UserDto::getUsername).containsExactly("alice");
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
        assertThat(results).hasSize(2);
        assertThat(results).containsOnly(1);

        // Verify
        SelectSpec<TUser, TUser> alice = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .mapToEntity();
        assertThat(executor.select(alice)).hasSize(1)
                .extracting(TUser::getStatus).containsExactly("INACTIVE");

        SelectSpec<TUser, TUser> bob = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .mapToEntity();
        assertThat(executor.select(bob)).hasSize(1)
                .extracting(TUser::getStatus).containsExactly("ACTIVE");
    }

    @Test
    void executeBatchUpdate_emptyList_returnsEmptyArray() {
        int[] results = executor.executeBatchUpdate(List.of());
        assertThat(results).isEmpty();
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
        assertThat(results).hasSize(2);
        assertThat(results).containsOnly(1);

        // Only charlie should remain
        SelectSpec<TUser, TUser> all = SelectBuilder.from(TUser.class).mapToEntity();
        List<TUser> remaining = executor.select(all);
        assertThat(remaining).hasSize(1)
                .extracting(TUser::getUsername)
                .containsExactly("charlie");
    }

    @Test
    void executeBatchDelete_emptyList_returnsEmptyArray() {
        int[] results = executor.executeBatchDelete(List.of());
        assertThat(results).isEmpty();
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

        UnionSpec<UserDto> union = UnionSpec.of(active).unionAll(inactive)
                .orderBy(JSort.byDesc(TUser::getUsername))
                .build();

        List<UserDto> result = executor.union(union);
        assertThat(result).hasSize(3);
        // Descending: charlie, bob, alice
        assertThat(result.get(0).getUsername()).isEqualTo("charlie");
        assertThat(result.get(result.size() - 1).getUsername()).isEqualTo("alice");
    }
}
