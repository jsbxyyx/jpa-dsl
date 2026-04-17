package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.Oracle11gDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.OracleDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.PostgresDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.SqlServerDialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UPSERT rendering across all dialects in {@link SqlRenderer}.
 * Verifies generated SQL strings and parameter bindings — no database required.
 */
class UpsertRendererTest {

    private final EntityMeta meta = EntityMetaReader.read(TUser.class);

    /**
     * Builds a column-value map for TUser fields without the identity PK — mirroring
     * what {@code JdbcDslExecutor.upsert()} passes to the dialect (identity PK is skipped).
     */
    private LinkedHashMap<String, Object> colValues(boolean includeId) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (includeId) {
            m.put("id", 1L);
        }
        m.put("username", "alice");
        m.put("email", "alice@example.com");
        m.put("age", 30);
        m.put("status", "ACTIVE");
        return m;
    }

    // ------------------------------------------------------------------ //
    //  MySQL — ON DUPLICATE KEY UPDATE
    // ------------------------------------------------------------------ //

    @Test
    void mysql_upsert_onDuplicateKeyUpdate_allNonConflictCols() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new MySqlDialect());

        String sql = rendered.getSql();
        assertThat(sql).startsWith("INSERT INTO t_user");
        assertThat(sql).contains("(username, email, age, status)");
        assertThat(sql).contains("VALUES (:username, :email, :age, :status)");
        assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
        // username is conflict col → excluded from UPDATE
        assertThat(sql).doesNotContain("username = VALUES(username)");
        assertThat(sql).contains("email = VALUES(email)");
        assertThat(sql).contains("age = VALUES(age)");
        assertThat(sql).contains("status = VALUES(status)");

        assertThat(rendered.getParams())
                .containsEntry("username", "alice")
                .containsEntry("email", "alice@example.com");
    }

    @Test
    void mysql_upsert_doUpdate_specificCols() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getEmail, TUser::getStatus)
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new MySqlDialect());

        String sql = rendered.getSql();
        assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(sql).contains("email = VALUES(email)");
        assertThat(sql).contains("status = VALUES(status)");
        // age was not in doUpdate → must not appear in the UPDATE part
        assertThat(sql).doesNotContain("age = VALUES(age)");
    }

    // ------------------------------------------------------------------ //
    //  PostgreSQL — ON CONFLICT DO UPDATE SET
    // ------------------------------------------------------------------ //

    @Test
    void postgres_upsert_onConflict_doUpdate_allNonConflict() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new PostgresDialect());

        String sql = rendered.getSql();
        assertThat(sql).startsWith("INSERT INTO t_user");
        assertThat(sql).contains("ON CONFLICT (username) DO UPDATE SET");
        assertThat(sql).doesNotContain("username = EXCLUDED.username");
        assertThat(sql).contains("email = EXCLUDED.email");
        assertThat(sql).contains("age = EXCLUDED.age");
        assertThat(sql).contains("status = EXCLUDED.status");
    }

    @Test
    void postgres_upsert_multiColumnConflict_rendersAllKeys() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername, TUser::getEmail)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new PostgresDialect());

        assertThat(rendered.getSql()).contains("ON CONFLICT (username, email) DO UPDATE SET");
        assertThat(rendered.getSql()).doesNotContain("username = EXCLUDED.username");
        assertThat(rendered.getSql()).doesNotContain("email = EXCLUDED.email");
        assertThat(rendered.getSql()).contains("age = EXCLUDED.age");
    }

    // ------------------------------------------------------------------ //
    //  H2 — standard SQL MERGE (no PostgreSQL compatibility mode required)
    // ------------------------------------------------------------------ //

    @Test
    void h2_upsert_usesMergeIntoSyntax() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new H2Dialect());

        String sql = rendered.getSql();
        assertThat(sql).startsWith("MERGE INTO t_user t");
        assertThat(sql).contains("USING (VALUES(");
        assertThat(sql).contains(")) AS s(");
        assertThat(sql).contains("ON (t.username = s.username)");
        assertThat(sql).contains("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        // Parameters use u_ prefix to avoid collision
        assertThat(rendered.getParams()).containsKey("u_username");
        assertThat(rendered.getParams()).doesNotContainKey("username");
    }

    // ------------------------------------------------------------------ //
    //  Oracle 12c — MERGE INTO ... USING (SELECT ... FROM DUAL)
    // ------------------------------------------------------------------ //

    @Test
    void oracle_upsert_mergeViaDual_rendersCorrectly() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new OracleDialect());

        String sql = rendered.getSql();
        assertThat(sql).startsWith("MERGE INTO t_user t");
        assertThat(sql).contains("USING (SELECT");
        assertThat(sql).contains("FROM DUAL) s");
        assertThat(sql).contains("ON (t.username = s.username)");
        assertThat(sql).contains("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        // Parameters use u_ prefix to avoid collision
        assertThat(rendered.getParams()).containsKey("u_username");
        assertThat(rendered.getParams().get("u_username")).isEqualTo("alice");
    }

    @Test
    void oracle_upsert_updateColsExcludeConflictKey() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new OracleDialect());

        String sql = rendered.getSql();
        // In WHEN MATCHED THEN UPDATE SET, conflict key must not appear
        int matchedPos = sql.indexOf("WHEN MATCHED THEN UPDATE SET");
        int notMatchedPos = sql.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateClause = sql.substring(matchedPos, notMatchedPos);
        assertThat(updateClause).doesNotContain("t.username = s.username");
        assertThat(updateClause).contains("t.email = s.email");
    }

    // ------------------------------------------------------------------ //
    //  Oracle 11g — delegates to OracleDialect.renderMergeViaDual
    // ------------------------------------------------------------------ //

    @Test
    void oracle11g_upsert_sameAsMergeViaDual() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql oracle12 = SqlRenderer.renderUpsert(spec, meta, values, new OracleDialect());
        RenderedSql oracle11 = SqlRenderer.renderUpsert(spec, meta,
                new LinkedHashMap<>(values), new Oracle11gDialect());

        assertThat(oracle11.getSql()).isEqualTo(oracle12.getSql());
        assertThat(oracle11.getParams()).isEqualTo(oracle12.getParams());
    }

    // ------------------------------------------------------------------ //
    //  SQL Server — MERGE INTO ... USING (SELECT ...) AS s(cols, ...)
    // ------------------------------------------------------------------ //

    @Test
    void sqlServer_upsert_mergeWithAlias_rendersCorrectly() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new SqlServerDialect());

        String sql = rendered.getSql();
        assertThat(sql).startsWith("MERGE INTO t_user AS t");
        assertThat(sql).contains("USING (SELECT");
        assertThat(sql).contains(") AS s (");
        assertThat(sql).contains("ON t.username = s.username");
        assertThat(sql).contains("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT");
        // SQL Server terminates the MERGE with a semicolon
        assertThat(sql).endsWith(";");
        assertThat(rendered.getParams()).containsKey("u_username");
    }

    @Test
    void sqlServer_upsert_updateClauseDoesNotRepeatConflictKey() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new SqlServerDialect());
        String sql = rendered.getSql();

        int matchedPos = sql.indexOf("WHEN MATCHED THEN UPDATE SET");
        int notMatchedPos = sql.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateClause = sql.substring(matchedPos, notMatchedPos);
        assertThat(updateClause).doesNotContain("t.username = s.username");
        assertThat(updateClause).contains("t.email = s.email");
    }

    // ------------------------------------------------------------------ //
    //  Sql2008Dialect — must throw UnsupportedOperationException
    // ------------------------------------------------------------------ //

    @Test
    void sql2008_upsert_throwsUnsupportedOperation() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        assertThatThrownBy(() ->
                SqlRenderer.renderUpsert(spec, meta, values, new Sql2008Dialect()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Sql2008Dialect");
    }

    // ------------------------------------------------------------------ //
    //  resolveUpdateColumns helper
    // ------------------------------------------------------------------ //

    @Test
    void resolveUpdateColumns_emptyUpdateCols_returnsAllNonConflict() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll()   // leaves updateColumns empty
                .build();

        java.util.List<String> all = java.util.List.of("username", "email", "age", "status");
        java.util.List<String> resolved = io.github.jsbxyyx.jdbcdsl.dialect.Dialect
                .resolveUpdateColumns(spec, all);

        assertThat(resolved).containsExactlyInAnyOrder("email", "age", "status");
        assertThat(resolved).doesNotContain("username");
    }

    @Test
    void resolveUpdateColumns_explicitUpdateCols_returnedAsIs() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getEmail)
                .build();

        java.util.List<String> all = java.util.List.of("username", "email", "age", "status");
        java.util.List<String> resolved = io.github.jsbxyyx.jdbcdsl.dialect.Dialect
                .resolveUpdateColumns(spec, all);

        assertThat(resolved).containsExactly("email");
    }

    // ------------------------------------------------------------------ //
    //  UpsertBuilder API
    // ------------------------------------------------------------------ //

    @Test
    void upsertBuilder_onConflict_resolvesColumnNames() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername, TUser::getEmail)
                .build();

        assertThat(spec.getConflictColumns()).containsExactly("username", "email");
    }

    @Test
    void upsertBuilder_doUpdate_resolvesColumnNames() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getAge, TUser::getStatus)
                .build();

        assertThat(spec.getUpdateColumns()).containsExactly("age", "status");
    }
}
