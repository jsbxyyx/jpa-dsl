package io.github.jsbxyyx.jdbcdsl.dialect;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for SQL Server pagination requirements. */
class SqlServerDialectTest {

    private final SqlServerDialect dialect = new SqlServerDialect();

    @Test
    void paginationWithoutOrderByAddsLegalFallbackOrder() {
        Map<String, Object> params = new LinkedHashMap<>();

        String sql = dialect.applyPagination("SELECT t.id FROM t_user t", 20, 10, params);

        assertThat(sql)
                .isEqualTo(
                        "SELECT t.id FROM t_user t ORDER BY (SELECT NULL) OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY");
        assertThat(params).containsEntry("_offset", 20L).containsEntry("_limit", 10);
    }

    @Test
    void paginationPreservesExistingTopLevelOrderBy() {
        Map<String, Object> params = new LinkedHashMap<>();

        String sql = dialect.applyPagination("SELECT t.id FROM t_user t ORDER BY t.id DESC", 0, 10, params);

        assertThat(sql)
                .isEqualTo(
                        "SELECT t.id FROM t_user t ORDER BY t.id DESC OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY");
        assertThat(sql).doesNotContain("SELECT NULL");
    }

    @Test
    void nestedOrderByDoesNotSuppressFallbackOrder() {
        String sql = dialect.applyPagination(
                "SELECT t.id FROM (SELECT id FROM t_user ORDER BY id) t", 0, 10, new LinkedHashMap<>());

        assertThat(sql).contains(") t ORDER BY (SELECT NULL) OFFSET");
    }

    @Test
    void orderByInsideLiteralOrCommentDoesNotSuppressFallbackOrder() {
        String literalSql = dialect.applyPagination(
                "SELECT 'ORDER BY' AS note FROM t_user -- ORDER BY id", 0, 10, new LinkedHashMap<>());

        String commentSql =
                dialect.applyPagination("SELECT t.id /* ORDER BY t.id */ FROM t_user", 0, 10, new LinkedHashMap<>());

        assertThat(literalSql).contains("ORDER BY (SELECT NULL) OFFSET");
        assertThat(commentSql).contains("ORDER BY (SELECT NULL) OFFSET");
    }

    @Test
    void orderByWithMixedCaseAndNewlineIsRecognized() {
        String sql = dialect.applyPagination("SELECT t.id FROM t_user t order\n by t.id", 0, 10, new LinkedHashMap<>());

        assertThat(sql).contains("order\n by t.id OFFSET").doesNotContain("SELECT NULL");
    }
}
