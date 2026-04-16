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
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")
                        .raw("t.age > 18"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("t.status = :p1")
                .contains("t.age > 18");
        assertThat(rendered.getParams()).containsEntry("p1", "ACTIVE");
    }

    @Test
    void renderSelect_rawPredicateConditionalFalse_omitted() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")
                        .raw("t.age > 18", false))  // condition=false → omitted
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("t.status = :p1")
                .doesNotContain("t.age > 18");
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

        assertThat(rendered.getSql()).contains("WHERE YEAR(t.created_at) = :yr");
        assertThat(rendered.getParams()).containsEntry("yr", 2024);
    }

    @Test
    void renderSelect_rawPredicateParams_doNotCollideWithTypedParams() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w
                        .eq(TUser::getStatus, "ACTIVE")       // → :p1
                        .raw("YEAR(t.created_at) = :yr", Map.of("yr", 2024)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getParams())
                .containsEntry("p1", "ACTIVE")
                .containsEntry("yr", 2024);
        assertThat(rendered.getSql())
                .contains("t.status = :p1")
                .contains("YEAR(t.created_at) = :yr");
    }

    // ------------------------------------------------------------------ //
    //  SqlFunctions.raw() — expression escape hatch
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawExpression_embeddedVerbatimWithAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(
                        SqlFunctions.col(TUser::getUsername),
                        raw("CASE WHEN t.age >= 18 THEN 'ADULT' ELSE 'MINOR' END").as("username")
                )
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("CASE WHEN t.age >= 18 THEN 'ADULT' ELSE 'MINOR' END AS username");
    }

    @Test
    void renderSelect_rawExpressionOrderBy_embeddedVerbatim() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(raw("CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END"))))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("ORDER BY CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END ASC");
    }

    // ------------------------------------------------------------------ //
    //  Raw predicate inside OR / NOT / nested AND
    // ------------------------------------------------------------------ //

    @Test
    void renderSelect_rawPredicateInsideOr_wrappedCorrectly() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.or(or -> or
                        .eq(TUser::getStatus, "ACTIVE")
                        .raw("t.age > 40")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);

        assertThat(rendered.getSql())
                .contains("(t.status = :p1 OR t.age > 40)");
    }
}
