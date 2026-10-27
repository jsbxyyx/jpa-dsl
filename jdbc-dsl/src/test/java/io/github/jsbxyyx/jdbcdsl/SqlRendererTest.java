package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.lit;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.max;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.subquery;

/**
 * Unit tests for {@link SqlRenderer} — verifies SQL string generation and parameter binding.
 */
class SqlRendererTest {

    @Test
    void renderSelect_basicEq() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.id AS id"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.username AS username"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM t_user t"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", rendered.getParams().get("p1"));
    }

    @Test
    void renderSelect_orderBy() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("ORDER BY t.username ASC, t.age DESC"));
    }

    @Test
    void renderSelect_likeCondition() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "%ali%"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("LIKE :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "%ali%", rendered.getParams().get("p1"));
    }

    @Test
    void renderSelect_between() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.between(TUser::getAge, 20, 40))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("BETWEEN :p1 AND :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(20, rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(40, rendered.getParams().get("p2"));
    }

    @Test
    void renderSelect_inClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getStatus, java.util.List.of("ACTIVE", "PENDING")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("IN (:p1)"));
    }

    @Test
    void renderSelect_isNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.isNull(TUser::getEmail))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("IS NULL"));
    }

    @Test
    void renderSelect_orPredicate() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.or(sub -> sub.eq(TUser::getStatus, "ACTIVE").eq(TUser::getStatus, "PENDING")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("OR"));
    }

    @Test
    void renderCount_noWhere() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().startsWith("SELECT COUNT(*)"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM t_user t"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().isEmpty());
    }

    @Test
    void renderCount_withJoin_useCountDistinctPk() {
        // When JOINs are present (e.g. user → orders one-to-many), COUNT(*) would
        // count duplicate rows. The fix uses COUNT(DISTINCT alias.pk) instead.
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class, "u")
                .select(TUser::getId, TUser::getUsername)
                .join(TOrder.class, "o", JoinType.LEFT, on -> on.eq(TUser::getId, "u", TOrder::getUserId, "o"))
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().startsWith("SELECT COUNT(DISTINCT u.id)"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("LEFT JOIN t_order o"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE"));
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("ORDER BY"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
    }

    @Test
    void renderCount_withGroupBy_wrapsAsSubquery() {
        // When GROUP BY is present, COUNT(*) counts raw rows, not groups.
        // The fix wraps the grouped query as a derived table.
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(
                        SqlFunctions.col(TUser::getStatus),
                        SqlFunctions.countStar().as("cnt"))
                .groupBy(TUser::getStatus)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().startsWith("SELECT COUNT(*) FROM ("));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("GROUP BY"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("_count_t"));
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("ORDER BY"));
    }

    @Test
    void renderCount_withGroupByAndHaving_wrapsAsSubquery() {
        // HAVING must also be included in the inner query.
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(
                        SqlFunctions.col(TUser::getStatus),
                        SqlFunctions.countStar().as("cnt"))
                .groupBy(TUser::getStatus)
                .having(h -> h.gt(SqlFunctions.countStar(), 1L))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().startsWith("SELECT COUNT(*) FROM ("));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("GROUP BY"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("HAVING"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("_count_t"));
    }

    @Test
    void renderSelect_noExplicitSelect_expandsAllColumns() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();
        // All columns should be expanded with property-name aliases
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("t.id AS id"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("t.username AS username"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("t.email AS email"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("t.age AS age"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("t.status AS status"));
        // Must NOT use t.*
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("t.*"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM t_user t"));
    }

    @Test
    void renderSelect_noExplicitSelect_columnsInEntityDeclarationOrder() {
        // TUser declares fields in order: id, username, email, age, status.
        // The expanded SELECT must reflect that order, NOT alphabetical order.
        // Alphabetical order would be: age, email, id, status, username.
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        // Extract the SELECT clause (everything between "SELECT " and " FROM")
        int selectStart = sql.indexOf("SELECT ") + "SELECT ".length();
        int fromStart = sql.indexOf(" FROM ");
        String selectClause = sql.substring(selectStart, fromStart);

        // Verify that the columns appear in entity declaration order, not alphabetical
        int posId = selectClause.indexOf("t.id AS id");
        int posUsername = selectClause.indexOf("t.username AS username");
        int posEmail = selectClause.indexOf("t.email AS email");
        int posAge = selectClause.indexOf("t.age AS age");
        int posStatus = selectClause.indexOf("t.status AS status");

        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) posId).compareTo(posUsername) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) posUsername).compareTo(posEmail) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) posEmail).compareTo(posAge) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) posAge).compareTo(posStatus) < 0);
    }

    @Test
    void renderSelect_aliasedExpression_usesExplicitAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(SqlFunctions.upper(TUser::getEmail).as("emailUpper"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("UPPER(t.email) AS emailUpper"));
    }

    @Test
    void jSort_noFromSpring_methodDoesNotExist() throws Exception {
        boolean hasFromSpring = false;
        for (java.lang.reflect.Method m : JSort.class.getMethods()) {
            if (m.getName().startsWith("fromSpring")) {
                hasFromSpring = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(hasFromSpring);
    }

    @Test
    void jPageable_noFromSpring_methodDoesNotExist() throws Exception {
        boolean hasFromSpring = false;
        for (java.lang.reflect.Method m : JPageable.class.getMethods()) {
            if (m.getName().startsWith("fromSpring")) {
                hasFromSpring = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(hasFromSpring);
    }

    // ------------------------------------------------------------------ //
    //  renderUpdate
    // ------------------------------------------------------------------ //

    @Test
    void renderUpdate_singleSet_noWhere() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "DISABLED")
                .buildUnsafe();

        RenderedSql rendered = SqlRenderer.renderUpdate(spec);
        org.junit.jupiter.api.Assertions.assertEquals("UPDATE t_user SET status = :p1", rendered.getSql());
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "DISABLED", rendered.getParams().get("p1"));
    }

    @Test
    void renderUpdate_multipleSet_withWhere() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .set(TUser::getAge, 0)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        RenderedSql rendered = SqlRenderer.renderUpdate(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().startsWith("UPDATE t_user SET"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("age = :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE username = :p3"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "INACTIVE", rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(0, rendered.getParams().get("p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p3"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "bob", rendered.getParams().get("p3"));
    }

    @Test
    void updateBuilder_arithmeticColumnOperands_renderColumnReferences() {
        RenderedSql added = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .add(TUser::getAge, TUser::getId)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(added.getSql().contains("SET age = age + id"));
        org.junit.jupiter.api.Assertions.assertTrue(added.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals("alice", added.getParams().get("p1"));

        RenderedSql subtracted = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .subtract(TUser::getAge, TUser::getId)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(subtracted.getSql().contains("SET age = age - id"));

        RenderedSql multiplied = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .multiply(TUser::getAge, TUser::getId)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(multiplied.getSql().contains("SET age = age * id"));

        RenderedSql divided = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .divide(TUser::getAge, TUser::getId)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(divided.getSql().contains("SET age = age / id"));
    }

    @Test
    void updateBuilder_arithmeticConstants_areBoundAsParameters() {
        RenderedSql added = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .add(TUser::getAge, 10)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(added.getSql().contains("SET age = age + :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(added.getSql().contains("WHERE id = :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(added.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(10, added.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(added.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, added.getParams().get("p2"));

        RenderedSql subtracted = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .subtract(TUser::getAge, 5)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(subtracted.getSql().contains("SET age = age - :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(subtracted.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(5, subtracted.getParams().get("p1"));

        RenderedSql multiplied = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .multiply(TUser::getAge, 2)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(multiplied.getSql().contains("SET age = age * :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(multiplied.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(2, multiplied.getParams().get("p1"));

        RenderedSql divided = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .divide(TUser::getAge, 4)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());
        org.junit.jupiter.api.Assertions.assertTrue(divided.getSql().contains("SET age = age / :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(divided.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(4, divided.getParams().get("p1"));
    }

    @Test
    void updateBuilder_multipleArithmeticOperationsOnSameColumn_areComposedInOrder() {
        RenderedSql rendered = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .add(TUser::getAge, 10)
                .multiply(TUser::getAge, 2)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());

        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("SET age = (age + :p1) * :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE id = :p3"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(10, rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(2, rendered.getParams().get("p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p3"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, rendered.getParams().get("p3"));
    }

    @Test
    void updateBuilder_conditionalArithmetic_skipsWhenConditionIsFalse() {
        RenderedSql rendered = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .add(TUser::getAge, 10, false)
                .subtract(TUser::getAge, TUser::getId, false)
                .multiply(TUser::getAge, 2, false)
                .divide(TUser::getAge, TUser::getId, false)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());

        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("SET age ="));
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("p1")),
                rendered.getParams().keySet());
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, rendered.getParams().get("p1"));
    }

    @Test
    void updateBuilder_conditionalArithmetic_appliesWhenConditionIsTrue() {
        RenderedSql rendered = SqlRenderer.renderUpdate(UpdateBuilder.from(TUser.class)
                .add(TUser::getAge, 10, true)
                .where(w -> w.eq(TUser::getId, 1L))
                .build());

        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("SET age = age + :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE id = :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(10, rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, rendered.getParams().get("p2"));
    }

    @Test
    void updateBuilder_arithmeticConstantRejectsNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> UpdateBuilder.from(TUser.class).add(TUser::getAge, (Number) null));
    }

    @Test
    void updateBuilder_noAssignments_build_withWhere_doesNotThrow() {
        // set-empty check has been removed; the database will report an error for empty SET
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getUsername, "alice"))
                .build();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    @Test
    void updateBuilder_noWhere_throwsIllegalState() {
        // build() without WHERE throws by default to prevent accidental full-table updates
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .build());
    }

    @Test
    void updateBuilder_buildUnsafe_allowsNoWhere() {
        // buildUnsafe() bypasses the WHERE guard; verify it does not throw
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .buildUnsafe();
        org.junit.jupiter.api.Assertions.assertNotNull(spec);
    }

    // ------------------------------------------------------------------ //
    //  renderDelete
    // ------------------------------------------------------------------ //

    @Test
    void renderDelete_withWhere() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE"))
                .build();

        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals("DELETE FROM t_user WHERE status = :p1", rendered.getSql());
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "INACTIVE", rendered.getParams().get("p1"));
    }

    @Test
    void renderDelete_withComplexWhere() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE").lt(TUser::getAge, 18))
                .build();

        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("DELETE FROM t_user WHERE"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("age < :p2"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "INACTIVE", rendered.getParams().get("p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p2"));
        org.junit.jupiter.api.Assertions.assertEquals(18, rendered.getParams().get("p2"));
    }

    @Test
    void renderDelete_noWhere_buildUnsafe() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class).buildUnsafe();
        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        org.junit.jupiter.api.Assertions.assertEquals("DELETE FROM t_user", rendered.getSql());
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().isEmpty());
    }

    @Test
    void deleteBuilder_noWhereCondition_throwsIllegalState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> DeleteBuilder.from(TUser.class)
                .build());
    }

    // ------------------------------------------------------------------ //
    //  Feature: LIKE raw pattern (no auto-% wrapping)
    // ------------------------------------------------------------------ //

    @Test
    void like_rawPattern_startsWith() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.like(TUser::getUsername, "ali%"))
                .mapTo(UserDto.class);
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("LIKE :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ali%", rendered.getParams().get("p1"));
    }

    @Test
    void like_rawPattern_endsWith() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.like(TUser::getUsername, "%ice"))
                .mapTo(UserDto.class);
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "%ice", rendered.getParams().get("p1"));
    }

    // ------------------------------------------------------------------ //
    //  Feature: WhereBuilder.not()
    // ------------------------------------------------------------------ //

    @Test
    void whereBuilder_not_rendersNotClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.not(n -> n.eq(TUser::getStatus, "INACTIVE")))
                .mapTo(UserDto.class);
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("NOT ("));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "INACTIVE", rendered.getParams().get("p1"));
    }

    // ------------------------------------------------------------------ //
    //  Feature: UPDATE SET expression assignment
    // ------------------------------------------------------------------ //

    @Test
    void updateBuilder_setExpression_rendersLiteral() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .setExpr(TUser::getAge, lit("age + 1"))
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .build();
        RenderedSql rendered = SqlRenderer.renderUpdate(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("SET age = age + 1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("WHERE status = :p1"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("p1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", rendered.getParams().get("p1"));
    }

    @Test
    void updateBuilder_setExpr_scalarSubquery_rendersInlineAndMergesParams() {
        // UPDATE t_user SET age = (SELECT MAX(o.amount) FROM t_order o WHERE o.user_id = :p1)
        // WHERE t_user.id = :p2
        SelectSpec<TOrder, TOrder> subq = SelectBuilder.from(TOrder.class, "o")
                .select(max(TOrder::getAmount))
                .where(w -> w.eq(TOrder::getUserId, "o", 42L))
                .mapToEntity();

        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .setExpr(TUser::getAge, subquery(subq))
                .where(w -> w.eq(TUser::getId, 1L))
                .build();

        RenderedSql rendered = SqlRenderer.renderUpdate(spec);
        String sql = rendered.getSql();

        // SET clause must contain an inline scalar subquery
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("SET age = (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("MAX(o.amount)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM t_order o"));
        // Subquery WHERE parameter and outer WHERE parameter must both be present
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsValue(42L));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsValue(1L));
    }

    // ------------------------------------------------------------------ //
    //  Feature: Derived-table subquery as FROM
    // ------------------------------------------------------------------ //

    @Test
    void fromSubquery_rendersInlineSubquery() {
        SelectSpec<TUser, TUser> inner = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        SelectSpec<TUser, UserDto> outer = SelectBuilder.fromSubquery(inner, "sub", TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(outer);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("FROM (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains(") sub"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("sub.id AS id"));
    }

    // ------------------------------------------------------------------ //
    //  Feature: FOR UPDATE
    // ------------------------------------------------------------------ //

    @Test
    void forUpdate_appendsForUpdateClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getId, 1L))
                .forUpdate()
                .mapTo(UserDto.class);
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().endsWith("FOR UPDATE"));
    }

    @Test
    void forUpdate_notInSubquery() {
        // FOR UPDATE must not appear inside a subquery rendered via buildSelectSql
        SelectSpec<TUser, UserDto> inner =
                SelectBuilder.from(TUser.class).forUpdate().mapTo(UserDto.class);
        // render as subquery (includeOrderBy=false path)
        RenderContext ctx = new RenderContext();
        String sql = SqlRenderer.buildSelectSql(inner, ctx, false);
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("FOR UPDATE"));
    }

    // ------------------------------------------------------------------ //
    //  Feature: Union ORDER BY
    // ------------------------------------------------------------------ //

    @Test
    void unionSpec_orderBy_appendsOrderByClause() {
        SelectSpec<TUser, UserDto> b1 = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> b2 = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE"))
                .mapTo(UserDto.class);

        UnionSpec<UserDto> union = UnionSpec.of(b1)
                .unionAll(b2)
                .orderBy(JSort.byAsc(TUser::getUsername))
                .build();

        RenderedSql rendered = SqlRenderer.renderUnion(union);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("UNION ALL"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().endsWith("ORDER BY username ASC"));
    }
}
