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
                .where(w -> w.like(TUser::getUsername, "ali"))
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
}
