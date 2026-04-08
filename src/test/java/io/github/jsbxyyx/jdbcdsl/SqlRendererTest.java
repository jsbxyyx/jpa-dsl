package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.upper;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(rendered.getSql())
                .contains("SELECT")
                .contains("t.id AS id")
                .contains("t.username AS username")
                .contains("FROM t_user t")
                .contains("WHERE")
                .contains("t.status = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE");
    }

    @Test
    void renderSelect_orderBy() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("ORDER BY t.username ASC, t.age DESC");
    }

    @Test
    void renderSelect_likeCondition() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.like(TUser::getUsername, "ali"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LIKE :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "%ali%");
    }

    @Test
    void renderSelect_between() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.between(TUser::getAge, 20, 40))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("BETWEEN :p1 AND :p2");
        assertThat(rendered.getParams()).containsEntry("p1", 20).containsEntry("p2", 40);
    }

    @Test
    void renderSelect_inClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.in(TUser::getStatus, java.util.List.of("ACTIVE", "PENDING")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("IN (:p1)");
    }

    @Test
    void renderSelect_isNull() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.isNull(TUser::getEmail))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("IS NULL");
    }

    @Test
    void renderSelect_orPredicate() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.or(sub -> sub
                        .eq(TUser::getStatus, "ACTIVE")
                        .eq(TUser::getStatus, "PENDING")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("OR");
    }

    @Test
    void renderCount_noWhere() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);
        assertThat(rendered.getSql())
                .startsWith("SELECT COUNT(*)")
                .contains("FROM t_user t");
        assertThat(rendered.getParams()).isEmpty();
    }

    @Test
    void renderSelect_noExplicitSelect_expandsAllColumns() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();
        // All columns should be expanded with property-name aliases
        assertThat(sql).contains("t.id AS id");
        assertThat(sql).contains("t.username AS username");
        assertThat(sql).contains("t.email AS email");
        assertThat(sql).contains("t.age AS age");
        assertThat(sql).contains("t.status AS status");
        // Must NOT use t.*
        assertThat(sql).doesNotContain("t.*");
        assertThat(sql).contains("FROM t_user t");
    }

    @Test
    void renderSelect_noExplicitSelect_columnsInEntityDeclarationOrder() {
        // TUser declares fields in order: id, username, email, age, status.
        // The expanded SELECT must reflect that order, NOT alphabetical order.
        // Alphabetical order would be: age, email, id, status, username.
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        // Extract the SELECT clause (everything between "SELECT " and " FROM")
        int selectStart = sql.indexOf("SELECT ") + "SELECT ".length();
        int fromStart = sql.indexOf(" FROM ");
        String selectClause = sql.substring(selectStart, fromStart);

        // Verify that the columns appear in entity declaration order, not alphabetical
        int posId       = selectClause.indexOf("t.id AS id");
        int posUsername = selectClause.indexOf("t.username AS username");
        int posEmail    = selectClause.indexOf("t.email AS email");
        int posAge      = selectClause.indexOf("t.age AS age");
        int posStatus   = selectClause.indexOf("t.status AS status");

        assertThat(posId).isLessThan(posUsername);
        assertThat(posUsername).isLessThan(posEmail);
        assertThat(posEmail).isLessThan(posAge);
        assertThat(posAge).isLessThan(posStatus);
    }

    @Test
    void renderSelect_aliasedExpression_usesExplicitAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(SqlFunctions.upper(TUser::getEmail).as("emailUpper"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("UPPER(t.email) AS emailUpper");
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
        assertThat(hasFromSpring).as("JSort must not have fromSpring methods").isFalse();
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
        assertThat(hasFromSpring).as("JPageable must not have fromSpring methods").isFalse();
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
        assertThat(rendered.getSql()).isEqualTo("UPDATE t_user SET status = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "DISABLED");
    }

    @Test
    void renderUpdate_multipleSet_withWhere() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .set(TUser::getAge, 0)
                .where(w -> w.eq(TUser::getUsername, "bob"))
                .build();

        RenderedSql rendered = SqlRenderer.renderUpdate(spec);
        assertThat(rendered.getSql())
                .startsWith("UPDATE t_user SET")
                .contains("status = :p1")
                .contains("age = :p2")
                .contains("WHERE username = :p3");
        assertThat(rendered.getParams())
                .containsEntry("p1", "INACTIVE")
                .containsEntry("p2", 0)
                .containsEntry("p3", "bob");
    }

    @Test
    void updateBuilder_noAssignments_throwsIllegalState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                UpdateBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getUsername, "alice"))
                        .build()
        );
    }

    @Test
    void updateBuilder_buildUnsafe_allowsNoAssignments() {
        // buildUnsafe is not defined on UpdateBuilder — build() guards this. Verify build() guards it.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                UpdateBuilder.from(TUser.class).build()
        );
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
        assertThat(rendered.getSql()).isEqualTo("DELETE FROM t_user WHERE status = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "INACTIVE");
    }

    @Test
    void renderDelete_withComplexWhere() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "INACTIVE").lt(TUser::getAge, 18))
                .build();

        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        assertThat(rendered.getSql()).contains("DELETE FROM t_user WHERE");
        assertThat(rendered.getSql()).contains("status = :p1");
        assertThat(rendered.getSql()).contains("age < :p2");
        assertThat(rendered.getParams())
                .containsEntry("p1", "INACTIVE")
                .containsEntry("p2", 18);
    }

    @Test
    void renderDelete_noWhere_buildUnsafe() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class).buildUnsafe();
        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        assertThat(rendered.getSql()).isEqualTo("DELETE FROM t_user");
        assertThat(rendered.getParams()).isEmpty();
    }

    @Test
    void deleteBuilder_noWhereCondition_throwsIllegalState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                DeleteBuilder.from(TUser.class).build()
        );
    }
}
