package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.OracleDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.PostgresDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.SqlServerDialect;
import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import io.github.jsbxyyx.jdbcdsl.expr.WindowExpression;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the five new DSL features (no database required):
 * <ol>
 *   <li>INTERSECT / EXCEPT set operations</li>
 *   <li>INSERT DO NOTHING (upsert skip-duplicate)</li>
 *   <li>FOR UPDATE extensions (NOWAIT / SKIP LOCKED / SHARE)</li>
 *   <li>Window frame clause (ROWS/RANGE BETWEEN … AND …)</li>
 *   <li>RETURNING clause on INSERT / UPDATE / DELETE</li>
 * </ol>
 */
class NewFeaturesRendererTest {

    // ===================================================================== //
    //  Feature #3: INTERSECT / EXCEPT
    // ===================================================================== //

    @Test
    void intersect_rendersTwoSelectsWithIntersect() {
        SelectSpec<TUser, UserDto> a = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        SelectSpec<TUser, UserDto> b = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getAge, 30))
                .mapTo(UserDto.class);

        UnionSpec<UserDto> spec = UnionSpec.of(a).intersect(b).build();
        RenderedSql rendered = SqlRenderer.renderUnion(spec);
        String sql = rendered.getSql();

        assertThat(sql).contains("INTERSECT");
        assertThat(sql).doesNotContain("UNION");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE");
        assertThat(rendered.getParams()).containsEntry("p2", 30);
    }

    @Test
    void intersectAll_rendersTwoSelectsWithIntersectAll() {
        SelectSpec<TUser, UserDto> a = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        SelectSpec<TUser, UserDto> b = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        UnionSpec<UserDto> spec = UnionSpec.of(a).intersectAll(b).build();
        String sql = SqlRenderer.renderUnion(spec).getSql();

        assertThat(sql).contains("INTERSECT ALL");
    }

    @Test
    void except_rendersTwoSelectsWithExcept() {
        SelectSpec<TUser, UserDto> a = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapTo(UserDto.class);

        SelectSpec<TUser, UserDto> b = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "BANNED"))
                .mapTo(UserDto.class);

        UnionSpec<UserDto> spec = UnionSpec.of(a).except(b).build();
        RenderedSql rendered = SqlRenderer.renderUnion(spec);
        String sql = rendered.getSql();

        assertThat(sql).contains(" EXCEPT ");
        assertThat(sql).doesNotContain("UNION");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE");
        assertThat(rendered.getParams()).containsEntry("p2", "BANNED");
    }

    @Test
    void exceptAll_rendersTwoSelectsWithExceptAll() {
        SelectSpec<TUser, UserDto> a = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        SelectSpec<TUser, UserDto> b = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        UnionSpec<UserDto> spec = UnionSpec.of(a).exceptAll(b).build();
        String sql = SqlRenderer.renderUnion(spec).getSql();

        assertThat(sql).contains("EXCEPT ALL");
    }

    @Test
    void chainedSetOps_unionThenIntersectThenExcept() {
        SelectSpec<TUser, UserDto> a = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername).mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> b = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername).mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> c = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername).mapTo(UserDto.class);

        UnionSpec<UserDto> spec = UnionSpec.of(a).union(b).intersect(c).build();
        String sql = SqlRenderer.renderUnion(spec).getSql();

        assertThat(sql).contains(" UNION ");
        assertThat(sql).contains(" INTERSECT ");
    }

    // ===================================================================== //
    //  Feature #8: INSERT DO NOTHING
    // ===================================================================== //

    private final EntityMeta userMeta = EntityMetaReader.read(TUser.class);

    private LinkedHashMap<String, Object> colValues() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("username", "alice");
        m.put("email", "alice@example.com");
        m.put("age", 30);
        m.put("status", "ACTIVE");
        return m;
    }

    @Test
    void doNothing_postgres_rendersOnConflictDoNothing() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doNothing()
                .build();

        RenderedSql rendered = new PostgresDialect().renderUpsert(spec, userMeta, colValues());
        assertThat(rendered.getSql()).contains("ON CONFLICT (username) DO NOTHING");
        assertThat(rendered.getSql()).doesNotContain("DO UPDATE");
    }

    @Test
    void doNothing_postgres_noConflictTarget_rendersOnConflictDoNothing() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .doNothing()
                .build();

        RenderedSql rendered = new PostgresDialect().renderUpsert(spec, userMeta, colValues());
        assertThat(rendered.getSql()).contains("ON CONFLICT DO NOTHING");
    }

    @Test
    void doNothing_mysql_rendersInsertIgnore() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .doNothing()
                .build();

        RenderedSql rendered = new MySqlDialect().renderUpsert(spec, userMeta, colValues());
        assertThat(rendered.getSql()).startsWith("INSERT IGNORE INTO t_user");
        assertThat(rendered.getSql()).doesNotContain("ON DUPLICATE KEY UPDATE");
    }

    @Test
    void doNothing_h2_rendersMergeWithoutWhenMatched() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doNothing()
                .build();

        RenderedSql rendered = new H2Dialect().renderUpsert(spec, userMeta, colValues());
        String sql = rendered.getSql();

        assertThat(sql).contains("MERGE INTO t_user");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        assertThat(sql).doesNotContain("WHEN MATCHED");
    }

    @Test
    void doNothing_sqlServer_rendersMergeWithoutWhenMatched() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doNothing()
                .build();

        RenderedSql rendered = new SqlServerDialect().renderUpsert(spec, userMeta, colValues());
        String sql = rendered.getSql();

        assertThat(sql).contains("MERGE INTO t_user");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        assertThat(sql).doesNotContain("WHEN MATCHED THEN UPDATE");
    }

    @Test
    void doNothing_oracle_rendersMergeWithoutWhenMatched() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doNothing()
                .build();

        RenderedSql rendered = new OracleDialect().renderUpsert(spec, userMeta, colValues());
        String sql = rendered.getSql();

        assertThat(sql).contains("MERGE INTO t_user");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        assertThat(sql).doesNotContain("WHEN MATCHED THEN UPDATE");
    }

    // ===================================================================== //
    //  Feature #10: FOR UPDATE extensions
    // ===================================================================== //

    @Test
    void forUpdate_noArg_rendersForUpdate() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate()
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).endsWith("FOR UPDATE");
    }

    @Test
    void forUpdate_lockModeUpdate_rendersForUpdate() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate(LockMode.UPDATE)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).endsWith("FOR UPDATE");
    }

    @Test
    void forUpdate_nowait_rendersForUpdateNowait() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate(LockMode.UPDATE_NOWAIT)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).endsWith("FOR UPDATE NOWAIT");
    }

    @Test
    void forUpdate_skipLocked_rendersForUpdateSkipLocked() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate(LockMode.UPDATE_SKIP_LOCKED)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).endsWith("FOR UPDATE SKIP LOCKED");
    }

    @Test
    void forUpdate_share_rendersForShare() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate(LockMode.SHARE)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).endsWith("FOR SHARE");
    }

    @Test
    void noLock_doesNotAppendLockClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).doesNotContain("FOR UPDATE");
        assertThat(sql).doesNotContain("FOR SHARE");
    }

    @Test
    void forUpdate_notEmittedInsideSubquery() {
        // FOR UPDATE must not appear in the inner SELECT when used as a subquery
        SelectSpec<TUser, UserDto> inner = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .forUpdate(LockMode.UPDATE_NOWAIT)
                .mapTo(UserDto.class);

        SelectSpec<TUser, UserDto> outer = SelectBuilder.fromSubquery(inner, "sub", TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .mapTo(UserDto.class);

        String sql = SqlRenderer.renderSelect(outer).getSql();
        // The outer query wraps inner as a derived table; locking only on the inner which
        // is rendered without includeOrderBy (and thus without the lock clause)
        assertThat(sql).doesNotContain("FOR UPDATE NOWAIT");
    }

    // ===================================================================== //
    //  Feature #4: Window Frame clause
    // ===================================================================== //

    @Test
    void windowFrame_rowsBetweenUnboundedPrecedingAndCurrentRow() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(
                        col(TOrder::getUserId, "o"),
                        sum(TOrder::getAmount).over(w -> w
                                .partitionBy(TOrder::getUserId)
                                .orderBy(JOrder.asc(TOrder::getId))
                                .rowsBetween(
                                        WindowExpression.FrameBound.unboundedPreceding(),
                                        WindowExpression.FrameBound.currentRow())
                        ).as("runningTotal")
                )
                .mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW");
    }

    @Test
    void windowFrame_rangeBetweenNPrecedingAndMFollowing() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(
                        col(TOrder::getUserId, "o"),
                        sum(TOrder::getAmount).over(w -> w
                                .orderBy(JOrder.asc(TOrder::getId))
                                .rangeBetween(
                                        WindowExpression.FrameBound.preceding(3),
                                        WindowExpression.FrameBound.following(1))
                        ).as("slidingTotal")
                )
                .mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).contains("RANGE BETWEEN 3 PRECEDING AND 1 FOLLOWING");
    }

    @Test
    void windowFrame_rowsBetweenUnboundedPrecedingAndUnboundedFollowing() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(
                        sum(TOrder::getAmount).over(w -> w
                                .partitionBy(TOrder::getUserId)
                                .rowsBetween(
                                        WindowExpression.FrameBound.unboundedPreceding(),
                                        WindowExpression.FrameBound.unboundedFollowing())
                        ).as("partitionTotal")
                )
                .mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).contains("ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING");
    }

    @Test
    void windowFrame_noFrame_doesNotAppendFrameClause() {
        SelectSpec<TOrder, TOrder> spec = SelectBuilder.from(TOrder.class, "o")
                .select(
                        sum(TOrder::getAmount).over(w -> w
                                .partitionBy(TOrder::getUserId)
                                .orderBy(JOrder.asc(TOrder::getId))
                        ).as("noFrame")
                )
                .mapToEntity();

        String sql = SqlRenderer.renderSelect(spec).getSql();
        assertThat(sql).doesNotContain("ROWS");
        assertThat(sql).doesNotContain("RANGE");
        assertThat(sql).doesNotContain("BETWEEN");
    }

    // ===================================================================== //
    //  Feature #9: RETURNING clause on UPDATE / DELETE (INSERT tested inline)
    // ===================================================================== //

    @Test
    void renderUpdateReturning_appendsReturningClause() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "INACTIVE")
                .where(w -> w.eq(TUser::getId, 1L))
                .build();

        RenderedSql rendered = SqlRenderer.renderUpdateReturning(spec,
                java.util.List.of("id", "username", "status"));
        String sql = rendered.getSql();

        assertThat(sql).contains("UPDATE t_user SET");
        assertThat(sql).contains("WHERE");
        assertThat(sql).endsWith("RETURNING id, username, status");
        assertThat(rendered.getParams()).containsKey("p1"); // status value
        assertThat(rendered.getParams()).containsKey("p2"); // id value
    }

    @Test
    void renderDeleteReturning_appendsReturningClause() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "BANNED"))
                .build();

        RenderedSql rendered = SqlRenderer.renderDeleteReturning(spec,
                java.util.List.of("id", "username"));
        String sql = rendered.getSql();

        assertThat(sql).startsWith("DELETE FROM t_user");
        assertThat(sql).endsWith("RETURNING id, username");
        assertThat(rendered.getParams()).containsEntry("p1", "BANNED");
    }

    @Test
    void renderInsertReturning_appendsReturningClause() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        LinkedHashMap<String, Object> cols = new LinkedHashMap<>();
        cols.put("username", "bob");
        cols.put("email", "bob@example.com");
        cols.put("age", 25);
        cols.put("status", "ACTIVE");

        RenderedSql rendered = SqlRenderer.renderInsertReturning(
                InsertSpec.of(TUser.class), meta, cols, java.util.List.of("id", "username"));
        String sql = rendered.getSql();

        assertThat(sql).startsWith("INSERT INTO t_user");
        assertThat(sql).contains("VALUES");
        assertThat(sql).endsWith("RETURNING id, username");
    }

    @Test
    void renderUpdateReturning_emptyReturningCols_returnsSameAsPlanUpdate() {
        UpdateSpec<TUser> spec = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "X")
                .where(w -> w.eq(TUser::getId, 1L))
                .build();

        RenderedSql base = SqlRenderer.renderUpdate(spec);
        RenderedSql withReturning = SqlRenderer.renderUpdateReturning(spec, java.util.List.of());

        assertThat(withReturning.getSql()).isEqualTo(base.getSql());
    }

    @Test
    void renderDeleteReturning_emptyReturningCols_returnsSameAsPlainDelete() {
        DeleteSpec<TUser> spec = DeleteBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getId, 1L))
                .build();

        RenderedSql base = SqlRenderer.renderDelete(spec);
        RenderedSql withReturning = SqlRenderer.renderDeleteReturning(spec, java.util.List.of());

        assertThat(withReturning.getSql()).isEqualTo(base.getSql());
    }
}
