package io.github.jsbxyyx.jdbcdsl.internal;

import io.github.jsbxyyx.jdbcdsl.DeleteBuilder;
import io.github.jsbxyyx.jdbcdsl.EntityMetaReader;
import io.github.jsbxyyx.jdbcdsl.InsertSpec;
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;
import io.github.jsbxyyx.jdbcdsl.UnionSpec;
import io.github.jsbxyyx.jdbcdsl.UpdateBuilder;
import io.github.jsbxyyx.jdbcdsl.UpsertBuilder;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the jdbc-dsl to jdbc-ast SELECT adapter directly, without its legacy fallback. */
class JdbcAstSelectEngineTest {

    @Test
    void rendersCommonSelectThroughJdbcAst() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(where -> where.eq(TUser::getStatus, "ACTIVE"))
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        var rendered = JdbcAstSelectEngine.tryRender(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().contains("SELECT"));
        assertTrue(rendered.getSql().contains("t.status = :p1"));
        assertTrue(rendered.getSql().contains("ORDER BY"));
        assertEquals("ACTIVE", rendered.getParams().get("p1"));
    }

    @Test
    void rendersUpdateThroughJdbcAstWithSetParametersBeforeWhereParameters() {
        var spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "DISABLED")
                .set(TUser::getAge, 42)
                .where(where -> where.eq(TUser::getUsername, "alice"))
                .build();

        var rendered = JdbcAstSelectEngine.tryRenderUpdate(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().contains("UPDATE t_user SET status = :p1, age = :p2 WHERE username = :p3"));
        assertEquals("DISABLED", rendered.getParams().get("p1"));
        assertEquals(42, rendered.getParams().get("p2"));
        assertEquals("alice", rendered.getParams().get("p3"));
    }

    @Test
    void rendersDeleteThroughJdbcAst() {
        var spec = DeleteBuilder.from(TUser.class)
                .where(where -> where.eq(TUser::getUsername, "alice"))
                .build();

        var rendered = JdbcAstSelectEngine.tryRenderDelete(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().contains("DELETE FROM t_user WHERE username = :p1"));
        assertEquals("alice", rendered.getParams().get("p1"));
    }

    @Test
    void rendersInsertThroughJdbcAstWithEntityColumnMappingAndReturning() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("username", "alice");
        values.put("age", 31);

        var rendered = JdbcAstSelectEngine.tryRenderInsert(
                InsertSpec.of(TUser.class), EntityMetaReader.read(TUser.class), values, List.of("username", "age"));

        assertNotNull(rendered);
        assertEquals("INSERT INTO t_user (username, age) VALUES (:p1, :p2) RETURNING username, age", rendered.getSql());
        assertEquals("alice", rendered.getParams().get("p1"));
        assertEquals(31, rendered.getParams().get("p2"));
    }

    @Test
    void rendersEmptyInAsAlwaysFalseWithoutParameters() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(where -> where.in(TUser::getStatus, List.of()))
                .mapToEntity();

        var rendered = JdbcAstSelectEngine.tryRender(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().contains("1 = 0"));
        assertTrue(rendered.getParams().isEmpty());
    }

    @Test
    void rendersEmptyNotInAsAlwaysTrueWithoutParameters() {
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(where -> where.notIn(TUser::getStatus, List.of()))
                .mapToEntity();

        var rendered = JdbcAstSelectEngine.tryRender(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().contains("1 = 1"));
        assertTrue(rendered.getParams().isEmpty());
    }

    @Test
    void rendersUnionWithIndependentParameterNamespaces() {
        SelectSpec<TUser, UserDto> active = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(where -> where.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> inactive = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(where -> where.eq(TUser::getStatus, "INACTIVE"))
                .mapTo(UserDto.class);

        var rendered = JdbcAstSelectEngine.renderUnion(
                UnionSpec.of(active).unionAll(inactive).build());

        assertTrue(rendered.getSql().contains("UNION ALL"));
        assertEquals("ACTIVE", rendered.getParams().get("u0_p1"));
        assertEquals("INACTIVE", rendered.getParams().get("u1_p1"));
    }

    @Test
    void rejectsH2UpsertWithoutConflictColumnsInsteadOfRenderingInvalidSql() {
        var spec = UpsertBuilder.into(TUser.class).doNothing().build();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("username", "alice");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JdbcAstSelectEngine.renderUpsert(
                        spec, EntityMetaReader.read(TUser.class), values, new H2Dialect()));
        assertTrue(exception.getMessage().contains("requires conflict columns"));
    }

    @Test
    void rendersCteThroughJdbcAst() {
        SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class).mapToEntity();
        SelectSpec<TUser, TUser> spec = SelectBuilder.fromCte("active_users", TUser.class)
                .withCte("active_users", cteBody)
                .mapToEntity();

        var rendered = JdbcAstSelectEngine.tryRender(spec);

        assertNotNull(rendered);
        assertTrue(rendered.getSql().startsWith("WITH active_users AS (SELECT"));
        assertTrue(rendered.getSql().contains("FROM active_users t"));
    }
}
