package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.raw;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for raw SQL escape-hatch rendering in {@link SqlRenderer}.
 * Verifies SQL output and parameter bindings — no database required.
 *
 * <p>Covers three escape-hatch entry points:
 * <ol>
 *   <li>{@code WhereBuilder.raw(String)} — verbatim SQL condition</li>
 *   <li>{@code WhereBuilder.raw(String, Map)} — SQL condition with named params</li>
 *   <li>{@code SqlFunctions.raw(String)} — raw SQL expression in SELECT / ORDER BY</li>
 * </ol>
 */
class RawSqlRendererTest {

    // ------------------------------------------------------------------ //
    //  raw(String) — no params
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawPredicateNoParams_embedsVerbatim() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.age > t.id"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("WHERE t.age > t.id");
        assertThat(rendered.getParams()).isEmpty();
    }

    @Test
    void renderSelect_rawPredicateCombinedWithTypedPredicate_appendsWithAnd() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").raw("t.age > 18"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("t.status = :p1").contains("t.age > 18");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE");
    }

    @Test
    void renderSelect_rawPredicateConditionalFalse_omitted() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").raw("t.age > 18", false)) // condition=false → omitted
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("t.status = :p1").doesNotContain("t.age > 18");
    }

    // ------------------------------------------------------------------ //
    //  raw(String, Map) — with named params
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawPredicateWithParams_mergesIntoParamMap() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("YEAR(t.created_at) = :yr", Map.of("yr", 2024)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("WHERE YEAR(t.created_at) = :raw1_yr");
        assertThat(rendered.getParams()).containsEntry("raw1_yr", 2024);
    }

    @Test
    void renderSelect_rawPredicateParams_doNotCollideWithTypedParams() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE") // → :p1
                        .raw("YEAR(t.created_at) = :yr", Map.of("yr", 2024)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE").containsEntry("raw1_yr", 2024);
        assertThat(rendered.getSql()).contains("t.status = :p1").contains("YEAR(t.created_at) = :raw1_yr");
    }

    @Test
    void renderSelect_rawPredicateParameterNamedLikeDslParameter_doesNotOverwriteDslValue() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").raw("t.age > :p1", Map.of("p1", 18)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("t.status = :p1").contains("t.age > :raw1_p1");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE").containsEntry("raw1_p1", 18);
    }

    @Test
    void renderSelect_twoRawPredicatesWithSameParameterName_keepValuesIndependent() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.age > :value", Map.of("value", 18)).raw("t.score > :value", Map.of("value", 90)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("t.age > :raw1_value").contains("t.score > :raw2_value");
        assertThat(rendered.getParams()).containsEntry("raw1_value", 18).containsEntry("raw2_value", 90);
    }

    @Test
    void renderCount_rawPredicate_keepsRenamedParametersConsistent() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE").raw("t.age > :p1", Map.of("p1", 18)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderCount(spec);

        assertThat(rendered.getSql()).contains("t.status = :p1").contains("t.age > :raw1_p1");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE").containsEntry("raw1_p1", 18);
    }

    @Test
    void renderSelect_rawParameterRewrite_repeatedAndPrefixSimilarNamesStayDistinct() {
        Map<String, Object> rawParams = new java.util.LinkedHashMap<>();
        rawParams.put("id", 7);
        rawParams.put("id2", 11);
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.id = :id OR t.parent_id = :id2 OR t.owner_id = :id", rawParams))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("t.id = :raw1_id")
                .contains("t.parent_id = :raw2_id2")
                .contains("t.owner_id = :raw1_id");
        assertThat(rendered.getParams()).containsEntry("raw1_id", 7).containsEntry("raw2_id2", 11);
    }

    @Test
    void renderSelect_rawParameterRewrite_skipsQuotedStringsAndComments() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.name = ':value' AND t.age > :value /* :value */", Map.of("value", 18)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("t.name = ':value'")
                .contains("t.age > :raw1_value")
                .contains("/* :value */");
        assertThat(rendered.getParams()).containsEntry("raw1_value", 18);
    }

    @Test
    void renderSelect_rawParameterRewrite_handlesCarriageReturnLineComment() {
        Map<String, Object> rawParams = new java.util.LinkedHashMap<>();
        rawParams.put("name", "alice");
        rawParams.put("value", 18);
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.name = :name -- :value\rAND t.age > :value", rawParams))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("t.name = :raw1_name").contains("-- :value\rAND t.age > :raw2_value");
        assertThat(rendered.getParams()).containsEntry("raw1_name", "alice").containsEntry("raw2_value", 18);
    }

    @Test
    void renderSelect_rawParameterRewrite_skipsDialectSpecificQuotedRegionsAndCastSyntax() {
        String rawSql = "payload = $tag$:value$tag$ AND [field:value] = 1 AND created_at::date = :day";
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw(rawSql, Map.of("day", "2026-10-30")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("payload = $tag$:value$tag$")
                .contains("[field:value] = 1")
                .contains("created_at::date = :raw1_day");
        assertThat(rendered.getParams()).containsEntry("raw1_day", "2026-10-30");
    }

    @Test
    void renderUnion_rawParametersAcrossBranches_keepValuesIndependent() {
        SelectSpec<TUser, UserDto> active = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.status = :value", Map.of("value", "ACTIVE")))
                .mapTo(UserDto.class);
        SelectSpec<TUser, UserDto> inactive = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.raw("t.status = :value", Map.of("value", "INACTIVE")))
                .mapTo(UserDto.class);

        RenderedSql rendered =
                SqlRenderer.renderUnion(UnionSpec.of(active).unionAll(inactive).build());

        assertThat(rendered.getSql()).contains("t.status = :raw1_value").contains("t.status = :raw2_value");
        assertThat(rendered.getParams()).containsEntry("raw1_value", "ACTIVE").containsEntry("raw2_value", "INACTIVE");
    }

    // ------------------------------------------------------------------ //
    //  SqlFunctions.raw() — expression escape hatch
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawExpression_embeddedVerbatimWithAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(
                        SqlFunctions.col(TUser::getUsername),
                        raw("CASE WHEN t.age >= 18 THEN 'ADULT' ELSE 'MINOR' END")
                                .as("ageCategory"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("CASE WHEN t.age >= 18 THEN 'ADULT' ELSE 'MINOR' END AS ageCategory");
    }

    @Test
    void renderSelect_rawExpressionOrderBy_embeddedVerbatim() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(raw("CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END"))))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("ORDER BY CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END ASC");
    }

    // ------------------------------------------------------------------ //
    //  Raw predicate inside OR / NOT / nested AND
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawPredicateInsideOr_wrappedCorrectly() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.or(or -> or.eq(TUser::getStatus, "ACTIVE").raw("t.age > 40")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql()).contains("(t.status = :p1 OR t.age > 40)");
    }
}
