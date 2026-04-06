package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

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
                .contains("t.id AS c0")
                .contains("t.username AS c1")
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
}
