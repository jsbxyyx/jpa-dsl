package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SqlFunctions} and function-expression support in
 * {@link WhereBuilder}, {@link SelectBuilder}, and {@link SqlRenderer}.
 */
class SqlFunctionsTest {

    // ------------------------------------------------------------------ //
    //  SqlFunctions factory shapes
    // ------------------------------------------------------------------ //

    @Test
    void upper_createsFunctionExpression() {
        FunctionExpression<String> expr = upper(TUser::getUsername);
        assertThat(expr.getFunctionName()).isEqualTo("UPPER");
        assertThat(expr.getArgs()).hasSize(1);
    }

    @Test
    void countStar_createsAggregateWithStarLiteral() {
        AggregateExpression<Long> agg = countStar();
        assertThat(agg.getFunctionName()).isEqualTo("COUNT");
        assertThat(agg.getArgs()).hasSize(1);
        assertThat(agg.isDistinct()).isFalse();
    }

    @Test
    void countDistinct_hasDistinctFlag() {
        AggregateExpression<Long> agg = countDistinct(TUser::getStatus);
        assertThat(agg.isDistinct()).isTrue();
    }

    @Test
    void fn_customName_wrapsPropInColumnExpression() {
        FunctionExpression<Object> expr = fn("MY_FUNC", TUser::getId);
        assertThat(expr.getFunctionName()).isEqualTo("MY_FUNC");
        assertThat(expr.getArgs()).hasSize(1);
    }

    // ------------------------------------------------------------------ //
    //  WHERE with function expressions
    // ------------------------------------------------------------------ //

    @Test
    void where_upper_rendersUpperInWhere() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(upper(TUser::getEmail), "TEST@EXAMPLE.COM"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("UPPER(t.email) = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "TEST@EXAMPLE.COM");
    }

    @Test
    void where_lower_rendersLowerInWhere() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(lower(TUser::getUsername), "alice"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LOWER(t.username) = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "alice");
    }

    @Test
    void where_functionGt_renders() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.gt(length(TUser::getUsername), 5))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LENGTH(t.username) > :p1");
        assertThat(rendered.getParams()).containsEntry("p1", 5);
    }

    @Test
    void where_year_renders() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(year(TUser::getEmail), 2024))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("YEAR(t.email) = :p1");
    }

    // ------------------------------------------------------------------ //
    //  SELECT with function expressions
    // ------------------------------------------------------------------ //

    @Test
    void select_functionExpression_renderedInSelectClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getId), upper(TUser::getUsername))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        // ColumnExpression gets property-name alias; FunctionExpression gets no alias
        assertThat(rendered.getSql()).contains("t.id AS id");
        assertThat(rendered.getSql()).contains("UPPER(t.username)");
        assertThat(rendered.getSql()).doesNotContain("UPPER(t.username) AS");
    }

    @Test
    void select_aggregateExpression_renderedInSelectClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getStatus), countStar())
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("t.status AS status");
        assertThat(rendered.getSql()).contains("COUNT(*)");
    }

    @Test
    void select_countDistinct_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(countDistinct(TUser::getStatus))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("COUNT(DISTINCT t.status)");
    }

    @Test
    void select_coalesce_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getId), coalesce(TUser::getEmail, "'N/A'"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("t.id AS id");
        assertThat(rendered.getSql()).contains("COALESCE(t.email, 'N/A')");
    }

    // ------------------------------------------------------------------ //
    //  GROUP BY / HAVING
    // ------------------------------------------------------------------ //

    @Test
    void groupBy_singleColumn_renderedInGroupByClause() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getStatus), countStar())
                .groupBy(TUser::getStatus)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("GROUP BY t.status");
    }

    @Test
    void groupBy_functionExpression_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(upper(TUser::getStatus), countStar())
                .groupBy(upper(TUser::getStatus))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("GROUP BY UPPER(t.status)");
    }

    @Test
    void having_aggregatePredicate_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getStatus), countStar())
                .groupBy(TUser::getStatus)
                .having(h -> h.gt(countStar(), 5L))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("GROUP BY t.status");
        assertThat(rendered.getSql()).contains("HAVING COUNT(*) > :p1");
        assertThat(rendered.getParams()).containsEntry("p1", 5L);
    }

    @Test
    void having_sumPredicate_rendered() {
        SelectSpec<TOrder, UserDto> spec = SelectBuilder.from(TOrder.class)
                .select(col(TOrder::getStatus), sum(TOrder::getAmount))
                .groupBy(TOrder::getStatus)
                .having(h -> h.gte(sum(TOrder::getAmount), BigDecimal.valueOf(1000)))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("HAVING SUM(t.amount) >= :p1");
        assertThat(rendered.getParams()).containsEntry("p1", BigDecimal.valueOf(1000));
    }

    @Test
    void groupByAndHaving_fullQuery_structureCorrect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getStatus), countStar())
                .where(w -> w.isNotNull(TUser::getEmail))
                .groupBy(TUser::getStatus)
                .having(h -> h.gt(countStar(), 2L))
                .orderBy(JSort.byDesc(TUser::getStatus))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        String sql = rendered.getSql();
        // Verify clause order
        int whereIdx   = sql.indexOf("WHERE");
        int groupByIdx = sql.indexOf("GROUP BY");
        int havingIdx  = sql.indexOf("HAVING");
        int orderByIdx = sql.indexOf("ORDER BY");
        assertThat(whereIdx).isLessThan(groupByIdx);
        assertThat(groupByIdx).isLessThan(havingIdx);
        assertThat(havingIdx).isLessThan(orderByIdx);
    }

    // ------------------------------------------------------------------ //
    //  ORDER BY with function expressions
    // ------------------------------------------------------------------ //

    @Test
    void orderBy_functionExpression_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(lower(TUser::getUsername))))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("ORDER BY LOWER(t.username) ASC");
    }

    @Test
    void orderBy_functionDescending_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.desc(length(TUser::getUsername))))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("ORDER BY LENGTH(t.username) DESC");
    }

    // ------------------------------------------------------------------ //
    //  Nested / composed functions
    // ------------------------------------------------------------------ //

    @Test
    void nestedFunctions_upperOfTrim_rendered() {
        // UPPER(TRIM(t.username))
        FunctionExpression<String> nested = fn("UPPER", trim(TUser::getUsername));
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(nested)
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        // Function expressions get no alias by default
        assertThat(rendered.getSql()).contains("UPPER(TRIM(t.username))");
        assertThat(rendered.getSql()).doesNotContain("UPPER(TRIM(t.username)) AS");
    }

    // ------------------------------------------------------------------ //
    //  Backward compatibility: existing SFunction-based API still works
    // ------------------------------------------------------------------ //

    @Test
    void backwardCompat_sfunctionSelectAndWhere_unchanged() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE")
                             .gt(TUser::getAge, 18))
                .orderBy(JSort.byAsc(TUser::getUsername))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql())
                .contains("t.id AS id")
                .contains("t.username AS username")
                .contains("t.status = :p1")
                .contains("t.age > :p2")
                .contains("ORDER BY t.username ASC");
    }

    // ------------------------------------------------------------------ //
    //  Explicit alias via .as()
    // ------------------------------------------------------------------ //

    @Test
    void aliasedExpression_functionWithAs_rendersExplicitAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(upper(TUser::getEmail).as("emailUpper"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("UPPER(t.email) AS emailUpper");
    }

    @Test
    void aliasedExpression_aggregateWithAs_rendersExplicitAlias() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(countStar().as("cnt"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("COUNT(*) AS cnt");
    }

    @Test
    void aliasedExpression_inWhereClause_usesInnerExpression() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId)
                .where(w -> w.eq(upper(TUser::getEmail).as("emailUpper"), "TEST@EXAMPLE.COM"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        // WHERE should use the inner expression, not the alias
        assertThat(rendered.getSql()).contains("UPPER(t.email) = :p1");
    }

    // ------------------------------------------------------------------ //
    //  CAST
    // ------------------------------------------------------------------ //

    @Test
    void cast_column_rendersCastWithAsKeyword() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(cast(TUser::getAge, "VARCHAR(10)"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("CAST(t.age AS VARCHAR(10))");
    }

    @Test
    void cast_withExplicitAlias_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(cast(TUser::getAge, "VARCHAR(10)").as("ageStr"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("CAST(t.age AS VARCHAR(10)) AS ageStr");
    }

    @Test
    void cast_nestedExpression_rendered() {
        // CAST(UPPER(t.email) AS VARCHAR(200))
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(cast(upper(TUser::getEmail), "VARCHAR(200)"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("CAST(UPPER(t.email) AS VARCHAR(200))");
    }

    @Test
    void cast_inWhereClause_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId)
                .where(w -> w.eq(cast(TUser::getAge, "VARCHAR(10)"), "25"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("CAST(t.age AS VARCHAR(10)) = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "25");
    }

    // ------------------------------------------------------------------ //
    //  SUBSTRING / SUBSTR
    // ------------------------------------------------------------------ //

    @Test
    void substring_posAndLen_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(substring(TUser::getUsername, 1, 3))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("SUBSTRING(t.username, 1, 3)");
    }

    @Test
    void substring_posOnly_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(substring(TUser::getEmail, 5))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("SUBSTRING(t.email, 5)");
    }

    @Test
    void substr_oracle_posAndLen_rendered() {
        // Oracle users call substr() instead of substring()
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(substr(TUser::getUsername, 1, 3))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("SUBSTR(t.username, 1, 3)");
    }

    @Test
    void substring_inWhereClause_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId)
                .where(w -> w.eq(substring(TUser::getEmail, 1, 3), "adm"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("SUBSTRING(t.email, 1, 3) = :p1");
        assertThat(rendered.getParams()).containsEntry("p1", "adm");
    }

    // ------------------------------------------------------------------ //
    //  NULLIF
    // ------------------------------------------------------------------ //

    @Test
    void nullif_withValueLiteral_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(nullif(TUser::getAge, "0").as("age"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLIF(t.age, 0) AS age");
    }

    @Test
    void nullif_withStringLiteral_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(nullif(TUser::getStatus, "'N/A'").as("status"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLIF(t.status, 'N/A') AS status");
    }

    @Test
    void nullif_expressionOverload_rendered() {
        // NULLIF(TRIM(t.username), '')
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(nullif(trim(TUser::getUsername), lit("''")).as("username"))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLIF(TRIM(t.username), '') AS username");
    }

    @Test
    void nullif_inWhereClause_rendered() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId)
                .where(w -> w.isNotNull(nullif(TUser::getStatus, "''")))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLIF(t.status, '') IS NOT NULL");
    }
}
