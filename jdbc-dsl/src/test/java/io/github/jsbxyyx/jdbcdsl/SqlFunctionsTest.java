package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
import org.junit.jupiter.api.Test;

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
                .having(h -> h.gt(countStar(), 5))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("GROUP BY t.status");
        assertThat(rendered.getSql()).contains("HAVING COUNT(*) > :p1");
        assertThat(rendered.getParams()).containsEntry("p1", 5);
    }

    @Test
    void having_sumPredicate_rendered() {
        SelectSpec<TOrder, UserDto> spec = SelectBuilder.from(TOrder.class)
                .select(col(TOrder::getStatus), sum(TOrder::getAmount))
                .groupBy(TOrder::getStatus)
                .having(h -> h.gte(sum(TOrder::getAmount), 1000))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("HAVING SUM(t.amount) >= :p1");
        assertThat(rendered.getParams()).containsEntry("p1", 1000);
    }

    @Test
    void groupByAndHaving_fullQuery_structureCorrect() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(col(TUser::getStatus), countStar())
                .where(w -> w.isNotNull(TUser::getEmail))
                .groupBy(TUser::getStatus)
                .having(h -> h.gt(countStar(), 2))
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
}
