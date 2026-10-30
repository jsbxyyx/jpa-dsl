package io.github.jsbxyyx.jdbcdsl.dialect;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

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
