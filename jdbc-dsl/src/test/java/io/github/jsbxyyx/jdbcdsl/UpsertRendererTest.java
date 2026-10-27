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
        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("INSERT INTO t_user"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("(username, email, age, status)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("VALUES (:username, :email, :age, :status)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        // username is conflict col → excluded from UPDATE
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("username = VALUES(username)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("email = VALUES(email)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("age = VALUES(age)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("status = VALUES(status)"));

        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("username"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "alice", rendered.getParams().get("username"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("email"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "alice@example.com", rendered.getParams().get("email"));
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
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("email = VALUES(email)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("status = VALUES(status)"));
        // age was not in doUpdate → must not appear in the UPDATE part
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("age = VALUES(age)"));
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
        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("INSERT INTO t_user"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON CONFLICT (username) DO UPDATE SET"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("username = EXCLUDED.username"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("email = EXCLUDED.email"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("age = EXCLUDED.age"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("status = EXCLUDED.status"));
    }

    @Test
    void postgres_upsert_multiColumnConflict_rendersAllKeys() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername, TUser::getEmail)
                .doUpdateAll()
                .build();

        LinkedHashMap<String, Object> values = colValues(false);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, values, new PostgresDialect());

        org.junit.jupiter.api.Assertions.assertTrue(
                rendered.getSql().contains("ON CONFLICT (username, email) DO UPDATE SET"));
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("username = EXCLUDED.username"));
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getSql().contains("email = EXCLUDED.email"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("age = EXCLUDED.age"));
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
        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("MERGE INTO t_user t"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("USING (VALUES("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains(")) AS s("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON (t.username = s.username)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN NOT MATCHED THEN INSERT"));
        // Parameters use u_ prefix to avoid collision
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("u_username"));
        org.junit.jupiter.api.Assertions.assertFalse(rendered.getParams().containsKey("username"));
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
        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("MERGE INTO t_user t"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("USING (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("FROM DUAL) s"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON (t.username = s.username)"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN NOT MATCHED THEN INSERT"));
        // Parameters use u_ prefix to avoid collision
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("u_username"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "alice", rendered.getParams().get("u_username"));
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
        org.junit.jupiter.api.Assertions.assertFalse(updateClause.contains("t.username = s.username"));
        org.junit.jupiter.api.Assertions.assertTrue(updateClause.contains("t.email = s.email"));
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
        RenderedSql oracle11 =
                SqlRenderer.renderUpsert(spec, meta, new LinkedHashMap<>(values), new Oracle11gDialect());

        org.junit.jupiter.api.Assertions.assertEquals(oracle12.getSql(), oracle11.getSql());
        org.junit.jupiter.api.Assertions.assertEquals(oracle12.getParams(), oracle11.getParams());
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
        org.junit.jupiter.api.Assertions.assertTrue(sql.startsWith("MERGE INTO t_user AS t"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("USING (SELECT"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains(") AS s ("));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ON t.username = s.username"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("WHEN NOT MATCHED THEN INSERT"));
        // SQL Server terminates the MERGE with a semicolon
        org.junit.jupiter.api.Assertions.assertTrue(sql.endsWith(";"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getParams().containsKey("u_username"));
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
        org.junit.jupiter.api.Assertions.assertFalse(updateClause.contains("t.username = s.username"));
        org.junit.jupiter.api.Assertions.assertTrue(updateClause.contains("t.email = s.email"));
    }

    // ------------------------------------------------------------------ //
    //  Sql2008Dialect — must throw UnsupportedOperationException
    // ------------------------------------------------------------------ //

    @Test
    void sql2008_upsert_throwsUnsupportedOperation() {
        UpsertSpec<TUser> spec =
                UpsertBuilder.into(TUser.class).onConflict(TUser::getUsername).build();

        LinkedHashMap<String, Object> values = colValues(false);
        Throwable assertionThrown3 = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> SqlRenderer.renderUpsert(spec, meta, values, new Sql2008Dialect()));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown3.getMessage().contains("Sql2008Dialect"));
    }

    // ------------------------------------------------------------------ //
    //  resolveUpdateColumns helper
    // ------------------------------------------------------------------ //

    @Test
    void resolveUpdateColumns_emptyUpdateCols_returnsAllNonConflict() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdateAll() // leaves updateColumns empty
                .build();

        java.util.List<String> all = java.util.List.of("username", "email", "age", "status");
        java.util.List<String> resolved = io.github.jsbxyyx.jdbcdsl.dialect.Dialect.resolveUpdateColumns(spec, all);

        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("email", "age", "status")),
                new java.util.HashSet<>(resolved));
        org.junit.jupiter.api.Assertions.assertEquals(3, resolved.size());
        org.junit.jupiter.api.Assertions.assertFalse(resolved.contains("username"));
    }

    @Test
    void resolveUpdateColumns_explicitUpdateCols_returnedAsIs() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getEmail)
                .build();

        java.util.List<String> all = java.util.List.of("username", "email", "age", "status");
        java.util.List<String> resolved = io.github.jsbxyyx.jdbcdsl.dialect.Dialect.resolveUpdateColumns(spec, all);

        org.junit.jupiter.api.Assertions.assertIterableEquals(java.util.Arrays.asList("email"), resolved);
    }

    // ------------------------------------------------------------------ //
    //  UpsertBuilder API
    // ------------------------------------------------------------------ //

    @Test
    void upsertBuilder_onConflict_resolvesColumnNames() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername, TUser::getEmail)
                .build();

        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("username", "email"), spec.getConflictColumns());
    }

    @Test
    void upsertBuilder_doUpdate_resolvesColumnNames() {
        UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
                .onConflict(TUser::getUsername)
                .doUpdate(TUser::getAge, TUser::getStatus)
                .build();

        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("age", "status"), spec.getUpdateColumns());
    }
}
