package io.github.jsbxyyx.jdbcdsl.dialect;

import io.github.jsbxyyx.jdbcdsl.LockMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies that every dialect enforces the same pagination argument contract. */
class DialectPaginationValidationTest {

    private final List<Dialect> dialects = List.of(
            new MySqlDialect(),
            new PostgresDialect(),
            new H2Dialect(),
            new DmDialect(),
            new KingbaseDialect(),
            new Sql2008Dialect(),
            new OracleDialect(),
            new Oracle11gDialect(),
            new SqlServerDialect());

    @Test
    void allDialectsRejectNegativeOffset() {
        for (Dialect dialect : dialects) {
            assertThatThrownBy(() -> dialect.applyPagination("SELECT 1", -1, 10, new LinkedHashMap<>()))
                    .as(dialect.getClass().getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("offset");
        }
    }

    @Test
    void mysqlPaginatesBeforeEveryKnownLockWithoutLosingParameters() {
        MySqlDialect dialect = new MySqlDialect();
        for (LockMode mode : LockMode.values()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("_limit", 7);
            String sql =
                    dialect.applyPagination("SELECT * FROM t WHERE id = :_offset" + mode.sql(), 4, 2, params, mode);
            assertThat(sql)
                    .isEqualTo("SELECT * FROM t WHERE id = :_offset LIMIT :_limit_1 OFFSET :_offset_1" + mode.sql());
            assertThat(params)
                    .containsEntry("_limit", 7)
                    .containsEntry("_limit_1", 2)
                    .containsEntry("_offset_1", 4L);
        }
    }

    @Test
    void postgresPaginatesBeforeEveryKnownLockWithoutLosingParameters() {
        PostgresDialect dialect = new PostgresDialect();
        for (LockMode mode : LockMode.values()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("_limit", 7);
            String sql =
                    dialect.applyPagination("SELECT * FROM t WHERE id = :_offset" + mode.sql(), 4, 2, params, mode);
            assertThat(sql)
                    .isEqualTo("SELECT * FROM t WHERE id = :_offset LIMIT :_limit_1 OFFSET :_offset_1" + mode.sql());
            assertThat(params)
                    .containsEntry("_limit", 7)
                    .containsEntry("_limit_1", 2)
                    .containsEntry("_offset_1", 4L);
        }
    }

    @Test
    void postgresRejectsMismatchedLockInsteadOfRewritingSql() {
        Map<String, Object> params = new LinkedHashMap<>();
        assertThatThrownBy(() -> new PostgresDialect().applyPagination("SELECT 1", 0, 1, params, LockMode.UPDATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock clause");
        assertThat(params).isEmpty();
    }

    @Test
    void mysqlRejectsMismatchedLockInsteadOfRewritingSql() {
        Map<String, Object> params = new LinkedHashMap<>();
        assertThatThrownBy(() -> new MySqlDialect().applyPagination("SELECT 1", 0, 1, params, LockMode.UPDATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock clause");
        assertThat(params).isEmpty();
    }

    @Test
    void oracleRejectsLockedPaginationBeforeChangingParameters() {
        for (Dialect dialect : List.of(new OracleDialect(), new Oracle11gDialect())) {
            for (LockMode mode : LockMode.values()) {
                Map<String, Object> params = new LinkedHashMap<>();
                assertThatThrownBy(() -> dialect.applyPagination("SELECT 1" + mode.sql(), 0, 1, params, mode))
                        .as(dialect.getClass().getSimpleName() + " " + mode)
                        .isInstanceOf(UnsupportedOperationException.class)
                        .hasMessageContaining("row locks");
                assertThat(params).isEmpty();
            }
            assertThat(dialect.applyPagination("SELECT 1", 0, 1, new LinkedHashMap<>(), null))
                    .isEqualTo(dialect.applyPagination("SELECT 1", 0, 1, new LinkedHashMap<>()));
        }
    }

    @Test
    void legacyDialectPaginationStillWorksWithLockAwareEntryPoint() {
        Dialect legacy = (sql, offset, limit, params) -> sql + " LEGACY";
        assertThat(legacy.applyPagination("SELECT 1 FOR UPDATE", 0, 1, new LinkedHashMap<>(), LockMode.UPDATE))
                .isEqualTo("SELECT 1 FOR UPDATE LEGACY");
    }

    @Test
    void allDialectsRejectNonPositiveLimit() {
        for (Dialect dialect : dialects) {
            assertThatThrownBy(() -> dialect.applyPagination("SELECT 1", 0, 0, new LinkedHashMap<>()))
                    .as(dialect.getClass().getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit");
        }
    }

    @Test
    void allDialectsPreserveExistingPaginationNamedParameters() {
        for (Dialect dialect : dialects) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("_offset", 777L);
            params.put("_limit", 888);
            String sql = dialect.applyPagination("SELECT * FROM t WHERE a = :_offset AND b = :_limit", 10, 5, params);

            org.assertj.core.api.Assertions.assertThat(params)
                    .as(dialect.getClass().getSimpleName())
                    .containsEntry("_offset", 777L)
                    .containsEntry("_limit", 888);
            org.assertj.core.api.Assertions.assertThat(sql)
                    .as(dialect.getClass().getSimpleName())
                    .contains(":_offset_1");
            if (dialect instanceof Oracle11gDialect) {
                org.assertj.core.api.Assertions.assertThat(params).containsEntry("_end", 15L);
                org.assertj.core.api.Assertions.assertThat(sql).contains("ROWNUM <= :_end");
            } else {
                org.assertj.core.api.Assertions.assertThat(params).containsEntry("_limit_1", 5);
                org.assertj.core.api.Assertions.assertThat(sql).contains(":_limit_1");
            }
        }
    }

    @Test
    void allDialectsDetectPaginationNamesPresentOnlyInSql() {
        for (Dialect dialect : dialects) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            String sql = dialect.applyPagination(
                    "SELECT * FROM t WHERE a = :_offset AND b = :_limit AND c = :_end", 10, 5, params);

            assertThat(params).as(dialect.getClass().getSimpleName()).containsEntry("_offset_1", 10L);
            assertThat(sql).as(dialect.getClass().getSimpleName()).contains(":_offset_1");
            if (dialect instanceof Oracle11gDialect) {
                assertThat(params).containsEntry("_end_1", 15L);
                assertThat(sql).contains(":_end_1");
            } else {
                assertThat(params).containsEntry("_limit_1", 5);
                assertThat(sql).contains(":_limit_1");
            }
        }
    }

    @Test
    void oracle11gPreservesExistingEndParameter() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("_end", 999L);
        String sql = new Oracle11gDialect().applyPagination("SELECT * FROM t WHERE a = :_end", 2, 3, params);

        org.assertj.core.api.Assertions.assertThat(params)
                .containsEntry("_end", 999L)
                .containsEntry("_end_1", 5L);
        org.assertj.core.api.Assertions.assertThat(sql).contains("ROWNUM <= :_end_1");
    }

    @Test
    void parameterScannerIgnoresCastsQuotesCommentsAndDollarQuotedStrings() {
        String sql = "SELECT value::text, ':_offset', \" :_limit \", "
                + "$tag$: _offset and :_limit $tag$ "
                + "FROM [table]]:_end] -- :_offset_1\n"
                + "/* :_limit_1 */ WHERE id = :_offset_2";

        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_offset"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_limit"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_end"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_offset_1"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_limit_1"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(Dialect.containsNamedParameter(sql, "_offset_2"))
                .isTrue();
    }

    @Test
    void parameterScannerDistinguishesRealTokensFromTextAndPrefixes() {
        assertThat(Dialect.containsNamedParameter("SELECT :p1", "_limit")).isFalse();
        assertThat(Dialect.containsNamedParameter("SELECT :_limit_more, :_limit$other", "_limit"))
                .isFalse();
        assertThat(Dialect.containsNamedParameter("SELECT value::_limit", "_limit"))
                .isFalse();
        assertThat(Dialect.containsNamedParameter("SELECT ':_limit', :_limit", "_limit"))
                .isTrue();
        assertThat(Dialect.containsNamedParameter("SELECT $$:_limit$$, :_limit", "_limit"))
                .isTrue();
        assertThat(Dialect.containsNamedParameter("SELECT /* :_limit */ :_limit", "_limit"))
                .isTrue();
        assertThat(Dialect.containsNamedParameter("SELECT ':_limit", "_limit")).isFalse();
        assertThat(Dialect.uniquePaginationParamName("_limit", "SELECT ':_limit', :_limit", Map.of()))
                .isEqualTo("_limit_1");
    }

    @Test
    void paginationParameterAllocationSkipsEveryExistingSuffix() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("_offset", 1L);
        params.put("_offset_1", 2L);
        String allocated =
                Dialect.uniquePaginationParamName("_offset", "SELECT * FROM t WHERE a = :_offset_2_extra", params);

        org.assertj.core.api.Assertions.assertThat(allocated).isEqualTo("_offset_2");
    }

    @Test
    void oracle11gRejectsOffsetPlusLimitOverflow() {
        assertThatThrownBy(() ->
                        new Oracle11gDialect().applyPagination("SELECT 1", Long.MAX_VALUE, 1, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }
}
