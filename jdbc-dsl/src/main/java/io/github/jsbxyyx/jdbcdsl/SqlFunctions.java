package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression;
import io.github.jsbxyyx.jdbcdsl.expr.CaseExpression;
import io.github.jsbxyyx.jdbcdsl.expr.CastExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression;
import io.github.jsbxyyx.jdbcdsl.expr.LiteralExpression;
import io.github.jsbxyyx.jdbcdsl.expr.ScalarSubqueryExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Static factory methods for building SQL function and aggregate expressions.
 *
 * <p>Import statically for the most readable usage:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * // WHERE UPPER(t.email) = :p1
 * .where(w -> w.eq(upper(TUser::getEmail), "ADMIN@EXAMPLE.COM"))
 *
 * // SELECT t.status, COUNT(*) AS c1 ... GROUP BY t.status HAVING COUNT(*) > :p1
 * .select(col(TUser::getStatus), countStar())
 * .groupBy(TUser::getStatus)
 * .having(h -> h.gt(countStar(), 5))
 *
 * // ORDER BY LOWER(t.username) ASC
 * .orderBy(JSort.by(JOrder.asc(lower(TUser::getUsername))))
 * }</pre>
 */
public final class SqlFunctions {

    private SqlFunctions() {
    }

    // ------------------------------------------------------------------ //
    //  Literal / CASE
    // ------------------------------------------------------------------ //

    /**
     * Embeds a raw SQL fragment verbatim (NOT bound as a JDBC parameter).
     *
     * <p>Use for SQL keywords, quoted string literals, or numeric constants:
     * <pre>{@code
     * lit("'Active'")  // → 'Active'
     * lit("0")         // → 0
     * }</pre>
     */
    public static <V> LiteralExpression<V> lit(String sql) {
        return LiteralExpression.of(sql);
    }

    /**
     * Starts building a searched {@code CASE WHEN ... THEN ... END} expression.
     *
     * <pre>{@code
     * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
     * import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
     *
     * // CASE WHEN t.status = 1 THEN 'Active' ELSE 'Inactive' END AS statusLabel
     * case_()
     *     .when(LeafPredicate.ofExpr(col(User::getStatus), LeafPredicate.Op.EQ, 1), lit("'Active'"))
     *     .otherwise(lit("'Inactive'"))
     *     .as("statusLabel")
     * }</pre>
     */
    public static CaseExpression.Builder case_() {
        return CaseExpression.builder();
    }

    // ------------------------------------------------------------------ //
    //  Column reference
    // ------------------------------------------------------------------ //

    /** Wraps a method reference as a {@link ColumnExpression}; alias resolved at render time. */
    public static <T, V> ColumnExpression<V> col(SFunction<T, V> prop) {
        return ColumnExpression.of(prop);
    }

    /** Wraps a method reference with an explicit table alias as a {@link ColumnExpression}. */
    public static <T, V> ColumnExpression<V> col(SFunction<T, V> prop, String tableAlias) {
        return ColumnExpression.of(prop, tableAlias);
    }

    // ------------------------------------------------------------------ //
    //  String functions
    // ------------------------------------------------------------------ //

    /** {@code UPPER(col)} */
    public static <T> FunctionExpression<String> upper(SFunction<T, ?> prop) {
        return fn("UPPER", prop);
    }

    /** {@code LOWER(col)} */
    public static <T> FunctionExpression<String> lower(SFunction<T, ?> prop) {
        return fn("LOWER", prop);
    }

    /** {@code TRIM(col)} */
    public static <T> FunctionExpression<String> trim(SFunction<T, ?> prop) {
        return fn("TRIM", prop);
    }

    /** {@code LENGTH(col)} */
    public static <T> FunctionExpression<Integer> length(SFunction<T, ?> prop) {
        return fn("LENGTH", prop);
    }

    /** {@code CONCAT(col1, col2, ...)} */
    @SafeVarargs
    public static <T> FunctionExpression<String> concat(SFunction<T, ?>... props) {
        List<SqlExpression<?>> args = new ArrayList<>(props.length);
        for (SFunction<T, ?> p : props) {
            args.add(ColumnExpression.of(p));
        }
        return new FunctionExpression<>("CONCAT", args);
    }

    /**
     * {@code COALESCE(col, fallback)} where {@code fallback} is a raw SQL literal.
     *
     * <p>Example: {@code coalesce(TUser::getEmail, "'N/A'")}
     * produces {@code COALESCE(t.email, 'N/A')}.
     */
    public static <T> FunctionExpression<String> coalesce(SFunction<T, ?> prop, String fallbackLiteral) {
        return new FunctionExpression<>("COALESCE",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(fallbackLiteral)));
    }

    /**
     * {@code CAST(col AS targetType)} — converts a column to the given SQL type.
     *
     * <p>{@code targetType} is a SQL type name embedded verbatim, e.g.
     * {@code "VARCHAR(100)"}, {@code "SIGNED"}, {@code "DECIMAL(10,2)"}.
     * Never put user-controlled input in {@code targetType}.
     *
     * <p>Supported by all major databases (MySQL, PostgreSQL, SQL Server, Oracle, H2).
     *
     * <p>Examples:
     * <pre>{@code
     * cast(TUser::getAge, "VARCHAR(10)")      // → CAST(t.age AS VARCHAR(10))
     * cast(TUser::getScore, "DECIMAL(10,2)")  // → CAST(t.score AS DECIMAL(10,2))
     * }</pre>
     */
    public static <T, V> CastExpression<V> cast(SFunction<T, ?> prop, String targetType) {
        return new CastExpression<>(ColumnExpression.of(prop), targetType);
    }

    /**
     * {@code CAST(expr AS targetType)} — overload for nested expressions.
     *
     * <pre>{@code
     * cast(upper(TUser::getEmail), "VARCHAR(200)")  // → CAST(UPPER(t.email) AS VARCHAR(200))
     * }</pre>
     */
    public static <V> CastExpression<V> cast(SqlExpression<?> expr, String targetType) {
        return new CastExpression<>(expr, targetType);
    }

    /**
     * {@code SUBSTRING(col, pos)} — extracts from {@code pos} to the end of the string.
     *
     * <p>Supported by MySQL, PostgreSQL, SQL Server, H2.
     * Oracle does not support {@code SUBSTRING} — use {@link #substr(SFunction, int)} instead.
     */
    public static <T> FunctionExpression<String> substring(SFunction<T, ?> prop, int pos) {
        return new FunctionExpression<>("SUBSTRING",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(pos))));
    }

    /**
     * {@code SUBSTRING(col, pos, len)} — extracts {@code len} characters starting at {@code pos}.
     *
     * <p>Supported by MySQL, PostgreSQL, SQL Server, H2.
     * Oracle does not support {@code SUBSTRING} — use {@link #substr(SFunction, int, int)} instead.
     */
    public static <T> FunctionExpression<String> substring(SFunction<T, ?> prop, int pos, int len) {
        return new FunctionExpression<>("SUBSTRING",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(pos)),
                        LiteralExpression.of(String.valueOf(len))));
    }

    /**
     * {@code SUBSTR(col, pos)} — Oracle-compatible alias for {@link #substring(SFunction, int)}.
     *
     * <p>Supported by MySQL, PostgreSQL, Oracle, H2.
     * SQL Server does not support {@code SUBSTR} — use {@link #substring(SFunction, int)} instead.
     */
    public static <T> FunctionExpression<String> substr(SFunction<T, ?> prop, int pos) {
        return new FunctionExpression<>("SUBSTR",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(pos))));
    }

    /**
     * {@code SUBSTR(col, pos, len)} — Oracle-compatible alias for {@link #substring(SFunction, int, int)}.
     *
     * <p>Supported by MySQL, PostgreSQL, Oracle, H2.
     * SQL Server does not support {@code SUBSTR} — use {@link #substring(SFunction, int, int)} instead.
     */
    public static <T> FunctionExpression<String> substr(SFunction<T, ?> prop, int pos, int len) {
        return new FunctionExpression<>("SUBSTR",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(pos)),
                        LiteralExpression.of(String.valueOf(len))));
    }

    /**
     * {@code NULLIF(col, valueLiteral)} — returns {@code NULL} if {@code col} equals
     * {@code valueLiteral}, otherwise returns {@code col}.
     *
     * <p>Standard SQL (SQL:1992), supported by all major databases.
     *
     * <p>Examples:
     * <pre>{@code
     * nullif(TUser::getAge, "0")      // → NULLIF(t.age, 0)
     * nullif(TUser::getStatus, "'N/A'") // → NULLIF(t.status, 'N/A')
     * }</pre>
     *
     * @param valueLiteral a raw SQL literal (use quotes for strings, e.g. {@code "'N/A'"})
     */
    public static <T, V> FunctionExpression<V> nullif(SFunction<T, ?> prop, String valueLiteral) {
        return new FunctionExpression<>("NULLIF",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(valueLiteral)));
    }

    /**
     * {@code NULLIF(expr1, expr2)} — expression overload of {@link #nullif(SFunction, String)}.
     *
     * <pre>{@code
     * nullif(trim(TUser::getUsername), lit("''"))  // → NULLIF(TRIM(t.username), '')
     * }</pre>
     */
    public static <V> FunctionExpression<V> nullif(SqlExpression<?> expr1, SqlExpression<?> expr2) {
        return new FunctionExpression<>("NULLIF", List.of(expr1, expr2));
    }

    // ------------------------------------------------------------------ //
    //  Date / time functions
    // ------------------------------------------------------------------ //

    /** {@code DATE(col)} */
    public static <T> FunctionExpression<Object> date(SFunction<T, ?> prop) {
        return fn("DATE", prop);
    }

    /** {@code YEAR(col)} */
    public static <T> FunctionExpression<Integer> year(SFunction<T, ?> prop) {
        return fn("YEAR", prop);
    }

    /** {@code MONTH(col)} */
    public static <T> FunctionExpression<Integer> month(SFunction<T, ?> prop) {
        return fn("MONTH", prop);
    }

    /** {@code DAY(col)} */
    public static <T> FunctionExpression<Integer> day(SFunction<T, ?> prop) {
        return fn("DAY", prop);
    }

    // ------------------------------------------------------------------ //
    //  Aggregate functions
    // ------------------------------------------------------------------ //

    /** {@code COUNT(col)} */
    public static <T> AggregateExpression<Long> count(SFunction<T, ?> prop) {
        return AggregateExpression.of("COUNT", List.of(ColumnExpression.of(prop)));
    }

    /** {@code COUNT(*)} */
    public static AggregateExpression<Long> countStar() {
        return AggregateExpression.of("COUNT", List.of(LiteralExpression.of("*")));
    }

    /** {@code COUNT(DISTINCT col)} */
    public static <T> AggregateExpression<Long> countDistinct(SFunction<T, ?> prop) {
        return AggregateExpression.ofDistinct("COUNT", List.of(ColumnExpression.of(prop)));
    }

    /** {@code SUM(col)} — preserves the numeric column type {@code V}. */
    public static <T, V extends Number> AggregateExpression<V> sum(SFunction<T, V> prop) {
        return AggregateExpression.of("SUM", List.of(ColumnExpression.of(prop)));
    }

    /** {@code MAX(col)} — preserves the column type {@code V}. */
    public static <T, V> AggregateExpression<V> max(SFunction<T, V> prop) {
        return AggregateExpression.of("MAX", List.of(ColumnExpression.of(prop)));
    }

    /** {@code MIN(col)} — preserves the column type {@code V}. */
    public static <T, V> AggregateExpression<V> min(SFunction<T, V> prop) {
        return AggregateExpression.of("MIN", List.of(ColumnExpression.of(prop)));
    }

    /** {@code AVG(col)} */
    public static <T> AggregateExpression<Double> avg(SFunction<T, ?> prop) {
        return AggregateExpression.of("AVG", List.of(ColumnExpression.of(prop)));
    }

    // ------------------------------------------------------------------ //
    //  Generic / custom functions
    // ------------------------------------------------------------------ //

    /**
     * Creates a scalar function expression with a single column argument.
     *
     * <p>Example: {@code fn("MY_FUNC", TUser::getId)} produces {@code MY_FUNC(t.id)}.
     */
    @SuppressWarnings("unchecked")
    public static <T, V> FunctionExpression<V> fn(String name, SFunction<T, ?> prop) {
        return new FunctionExpression<>(name, List.of(ColumnExpression.of(prop)));
    }

    /**
     * Creates a scalar function expression with arbitrary SQL expression arguments.
     *
     * <p>Example: {@code fn("COALESCE", col(TUser::getEmail), LiteralExpression.of("'N/A'"))}
     * produces {@code COALESCE(t.email, 'N/A')}.
     */
    @SuppressWarnings("unchecked")
    public static <V> FunctionExpression<V> fn(String name, SqlExpression<?>... args) {
        return new FunctionExpression<>(name, List.of(args));
    }

    // ------------------------------------------------------------------ //
    //  Window functions
    // ------------------------------------------------------------------ //

    /**
     * {@code ROW_NUMBER()} — assigns a unique sequential integer to each row.
     * Chain {@code .over(...)} to specify the window.
     *
     * <p>Example: {@code rowNumber().over(w -> w.partitionBy(TUser::getStatus).orderBy(JOrder.asc(TUser::getId))).as("rn")}
     */
    public static FunctionExpression<Long> rowNumber() {
        return new FunctionExpression<>("ROW_NUMBER", List.of());
    }

    /**
     * {@code RANK()} — assigns a rank within the window; gaps appear after ties.
     * Chain {@code .over(...)} to specify the window.
     */
    public static FunctionExpression<Long> rank() {
        return new FunctionExpression<>("RANK", List.of());
    }

    /**
     * {@code DENSE_RANK()} — assigns a rank within the window; no gaps after ties.
     * Chain {@code .over(...)} to specify the window.
     */
    public static FunctionExpression<Long> denseRank() {
        return new FunctionExpression<>("DENSE_RANK", List.of());
    }

    /**
     * {@code NTILE(n)} — divides the result set into {@code n} equal buckets.
     * Chain {@code .over(...)} to specify the window.
     *
     * <p>Example: {@code ntile(4).over(w -> w.orderBy(JOrder.asc(TUser::getAge))).as("quartile")}
     */
    public static FunctionExpression<Long> ntile(int n) {
        return new FunctionExpression<>("NTILE", List.of(LiteralExpression.of(String.valueOf(n))));
    }

    /**
     * {@code LAG(col, offset)} — returns the value from a preceding row within the window.
     *
     * <p>Example: {@code lag(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("prevAge")}
     */
    public static <T, V> FunctionExpression<V> lag(SFunction<T, V> prop, int offset) {
        return new FunctionExpression<>("LAG",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(offset))));
    }

    /**
     * {@code LAG(col)} — returns the value from the immediately preceding row (offset=1).
     */
    public static <T, V> FunctionExpression<V> lag(SFunction<T, V> prop) {
        return lag(prop, 1);
    }

    /**
     * {@code LEAD(col, offset)} — returns the value from a following row within the window.
     *
     * <p>Example: {@code lead(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("nextAge")}
     */
    public static <T, V> FunctionExpression<V> lead(SFunction<T, V> prop, int offset) {
        return new FunctionExpression<>("LEAD",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(offset))));
    }

    /**
     * {@code LEAD(col)} — returns the value from the immediately following row (offset=1).
     */
    public static <T, V> FunctionExpression<V> lead(SFunction<T, V> prop) {
        return lead(prop, 1);
    }

    /**
     * {@code FIRST_VALUE(col)} — returns the first value in the window frame.
     * Chain {@code .over(...)} to specify the window.
     */
    public static <T, V> FunctionExpression<V> firstValue(SFunction<T, V> prop) {
        return fn("FIRST_VALUE", prop);
    }

    /**
     * {@code LAST_VALUE(col)} — returns the last value in the window frame.
     * Chain {@code .over(...)} to specify the window.
     */
    public static <T, V> FunctionExpression<V> lastValue(SFunction<T, V> prop) {
        return fn("LAST_VALUE", prop);
    }

    // ------------------------------------------------------------------ //
    //  SELECT scalar subquery
    // ------------------------------------------------------------------ //

    /**
     * Wraps a {@link SelectSpec} as a scalar subquery expression for use in the SELECT clause.
     *
     * <p>The subquery must return exactly one row and one column. Use {@link SqlExpression#as(String)}
     * to map the result to a DTO property.
     *
     * <p>Example — count orders per user inline:
     * <pre>{@code
     * SelectSpec<TOrder, TOrder> countSpec = SelectBuilder.from(TOrder.class, "o")
     *         .select(countStar())
     *         .where(w -> w.raw("o.user_id = t.id"))
     *         .mapToEntity();
     *
     * SelectSpec<TUser, UserOrderCountDto> spec = SelectBuilder.from(TUser.class)
     *         .select(col(TUser::getUsername), subquery(countSpec).as("orderCount"))
     *         .mapTo(UserOrderCountDto.class);
     * }</pre>
     *
     * @param selectSpec the inner SELECT returning a single scalar value
     */
    public static <V> ScalarSubqueryExpression<V> subquery(SelectSpec<?, ?> selectSpec) {
        return new ScalarSubqueryExpression<>(selectSpec);
    }

    // ------------------------------------------------------------------ //
    //  Math functions
    // ------------------------------------------------------------------ //

    /** {@code FLOOR(col)} — largest integer ≤ col */
    public static <T> FunctionExpression<Number> floor(SFunction<T, ?> prop) {
        return fn("FLOOR", prop);
    }

    /** {@code CEIL(col)} — smallest integer ≥ col (alias: CEILING) */
    public static <T> FunctionExpression<Number> ceil(SFunction<T, ?> prop) {
        return fn("CEIL", prop);
    }

    /** {@code ROUND(col)} — rounds to nearest integer */
    public static <T> FunctionExpression<Number> round(SFunction<T, ?> prop) {
        return fn("ROUND", prop);
    }

    /** {@code ROUND(col, scale)} — rounds to {@code scale} decimal places */
    public static <T> FunctionExpression<Number> round(SFunction<T, ?> prop, int scale) {
        return new FunctionExpression<>("ROUND",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(scale))));
    }

    /** {@code ABS(col)} — absolute value */
    public static <T> FunctionExpression<Number> abs(SFunction<T, ?> prop) {
        return fn("ABS", prop);
    }

    /** {@code MOD(col, divisor)} — remainder of col / divisor */
    public static <T> FunctionExpression<Number> mod(SFunction<T, ?> prop, int divisor) {
        return new FunctionExpression<>("MOD",
                List.of(ColumnExpression.of(prop), LiteralExpression.of(String.valueOf(divisor))));
    }

    /**
     * {@code GREATEST(col1, col2, ...)} — returns the greatest value among the arguments.
     * Supported by MySQL, PostgreSQL, Oracle, H2. Not available in SQL Server.
     */
    @SafeVarargs
    public static <T> FunctionExpression<Object> greatest(SFunction<T, ?>... props) {
        List<SqlExpression<?>> args = new ArrayList<>(props.length);
        for (SFunction<T, ?> p : props) args.add(ColumnExpression.of(p));
        return new FunctionExpression<>("GREATEST", args);
    }

    /**
     * {@code LEAST(col1, col2, ...)} — returns the smallest value among the arguments.
     * Supported by MySQL, PostgreSQL, Oracle, H2. Not available in SQL Server.
     */
    @SafeVarargs
    public static <T> FunctionExpression<Object> least(SFunction<T, ?>... props) {
        List<SqlExpression<?>> args = new ArrayList<>(props.length);
        for (SFunction<T, ?> p : props) args.add(ColumnExpression.of(p));
        return new FunctionExpression<>("LEAST", args);
    }

    // ------------------------------------------------------------------ //
    //  Additional string functions
    // ------------------------------------------------------------------ //

    /**
     * {@code REPLACE(col, searchLiteral, replaceLiteral)} — replaces occurrences of
     * {@code searchLiteral} with {@code replaceLiteral} in {@code col}.
     *
     * <p>Literals must be properly quoted for strings, e.g. {@code "'old'"}, {@code "'new'"}.
     *
     * <p>Example: {@code replace(TUser::getEmail, "'@old.com'", "'@new.com'")}
     * → {@code REPLACE(t.email, '@old.com', '@new.com')}
     */
    public static <T> FunctionExpression<String> replace(SFunction<T, ?> prop,
                                                          String searchLiteral,
                                                          String replaceLiteral) {
        return new FunctionExpression<>("REPLACE",
                List.of(ColumnExpression.of(prop),
                        LiteralExpression.of(searchLiteral),
                        LiteralExpression.of(replaceLiteral)));
    }

    /**
     * {@code LPAD(col, length, padLiteral)} — left-pads {@code col} to {@code length} characters.
     *
     * <p>Example: {@code lpad(TUser::getUsername, 10, "'0'")} → {@code LPAD(t.username, 10, '0')}
     */
    public static <T> FunctionExpression<String> lpad(SFunction<T, ?> prop, int length, String padLiteral) {
        return new FunctionExpression<>("LPAD",
                List.of(ColumnExpression.of(prop),
                        LiteralExpression.of(String.valueOf(length)),
                        LiteralExpression.of(padLiteral)));
    }

    /**
     * {@code RPAD(col, length, padLiteral)} — right-pads {@code col} to {@code length} characters.
     */
    public static <T> FunctionExpression<String> rpad(SFunction<T, ?> prop, int length, String padLiteral) {
        return new FunctionExpression<>("RPAD",
                List.of(ColumnExpression.of(prop),
                        LiteralExpression.of(String.valueOf(length)),
                        LiteralExpression.of(padLiteral)));
    }

    // ------------------------------------------------------------------ //
    //  Date / time functions (additional)
    // ------------------------------------------------------------------ //

    /**
     * {@code CURRENT_TIMESTAMP} — returns the current date and time.
     * Standard SQL, supported by all major databases.
     * Use {@code .as("alias")} to map to a DTO property.
     */
    public static LiteralExpression<Object> now() {
        return LiteralExpression.of("CURRENT_TIMESTAMP");
    }

    /**
     * {@code CURRENT_DATE} — returns the current date (without time).
     * Standard SQL, supported by all major databases.
     */
    public static LiteralExpression<Object> currentDate() {
        return LiteralExpression.of("CURRENT_DATE");
    }

    /** {@code HOUR(col)} */
    public static <T> FunctionExpression<Integer> hour(SFunction<T, ?> prop) {
        return fn("HOUR", prop);
    }

    /** {@code MINUTE(col)} */
    public static <T> FunctionExpression<Integer> minute(SFunction<T, ?> prop) {
        return fn("MINUTE", prop);
    }

    /** {@code SECOND(col)} */
    public static <T> FunctionExpression<Integer> second(SFunction<T, ?> prop) {
        return fn("SECOND", prop);
    }

    // ------------------------------------------------------------------ //
    //  Raw SQL expression (escape hatch)
    // ------------------------------------------------------------------ //

    /**
     * Wraps an arbitrary SQL fragment as a {@link LiteralExpression} that is embedded verbatim
     * in the SELECT, ORDER BY, GROUP BY, or HAVING clause.
     *
     * <p>Use this escape hatch when the DSL cannot express a complex expression — for example,
     * a {@code CASE WHEN} expression or a database-specific function.
     * Combine with {@link SqlExpression#as(String)} to give the expression a column alias:
     * <pre>{@code
     * .select(raw("CASE WHEN t.age >= 18 THEN 'ADULT' ELSE 'MINOR' END").as("ageGroup"))
     * }</pre>
     *
     * <p><strong>Warning:</strong> never pass user-controlled data in {@code sql}.
     * Bind user input as named parameters in the WHERE clause.
     *
     * @param sql the raw SQL fragment to embed verbatim
     * @return a {@link LiteralExpression} for use in SELECT / ORDER BY / GROUP BY / HAVING
     */
    public static <V> LiteralExpression<V> raw(String sql) {
        return LiteralExpression.of(sql);
    }
}
