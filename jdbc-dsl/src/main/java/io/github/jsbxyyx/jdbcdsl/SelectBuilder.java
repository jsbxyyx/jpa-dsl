package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link SelectSpec}.
 *
 * <p>Usage example (plain column selection):
 * <pre>{@code
 * SelectSpec<User, UserDto> spec = SelectBuilder
 *     .from(User.class)
 *     .select(User::getId, User::getName)
 *     .where(w -> w.eq(User::getStatus, "ACTIVE"))
 *     .orderBy(JSort.byAsc(User::getName))
 *     .mapTo(UserDto.class);
 * }</pre>
 *
 * <p>Usage example (function expressions):
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * SelectSpec<User, UserDto> spec = SelectBuilder
 *     .from(User.class)
 *     .select(col(User::getId), upper(User::getEmail))
 *     .where(w -> w.eq(upper(User::getEmail), "ADMIN@EXAMPLE.COM"))
 *     .mapTo(UserDto.class);
 * }</pre>
 *
 * <p>Usage example (GROUP BY / HAVING):
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * SelectSpec<User, StatusCountDto> spec = SelectBuilder
 *     .from(User.class)
 *     .select(col(User::getStatus), countStar())
 *     .groupBy(User::getStatus)
 *     .having(h -> h.gt(countStar(), 5))
 *     .mapTo(StatusCountDto.class);
 * }</pre>
 *
 * <p>Usage example (CTE):
 * <pre>{@code
 * SelectSpec<TUser, TUser> cteBody = SelectBuilder.from(TUser.class)
 *     .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
 *     .mapToEntity();
 *
 * SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
 *     .withCte("active_users", cteBody)
 *     .select(TUser::getId, TUser::getUsername)
 *     .mapTo(UserDto.class);
 * // → WITH active_users AS (...) SELECT … FROM active_users t
 * }</pre>
 */
public final class SelectBuilder<T> {

    private final Class<T> entityClass;
    private final String alias;
    private boolean distinct = false;
    private final List<SqlExpression<?>> selectedExpressions = new ArrayList<>();
    private PredicateNode where;
    private final List<JoinSpec> joins = new ArrayList<>();
    private JSort<T> sort = JSort.unsorted();
    private final List<SqlExpression<?>> groupByExpressions = new ArrayList<>();
    private PredicateNode having;
    private final List<CteDef> cteDefs = new ArrayList<>();
    private String tableNameOverride = null;

    private SelectBuilder(Class<T> entityClass, String alias) {
        this.entityClass = entityClass;
        this.alias = alias;
    }

    /** Starts building a select from the given entity class with default alias {@code "t"}. */
    public static <T> SelectBuilder<T> from(Class<T> entityClass) {
        return new SelectBuilder<>(entityClass, "t");
    }

    /** Starts building a select from the given entity class with a custom alias. */
    public static <T> SelectBuilder<T> from(Class<T> entityClass, String alias) {
        return new SelectBuilder<>(entityClass, alias);
    }

    /**
     * Starts building a select whose FROM clause uses {@code cteName} as the table name,
     * while {@code entityClass} provides the column mappings for type-safe property references.
     *
     * <p>Use this together with {@link #withCte(String, SelectSpec)} to query from a CTE:
     * <pre>{@code
     * SelectBuilder.fromCte("active_users", TUser.class)
     *     .withCte("active_users", cteBody)
     *     .select(TUser::getId, TUser::getUsername)
     *     .mapTo(UserDto.class)
     * // → FROM active_users t  (not FROM t_user t)
     * }</pre>
     *
     * <p>The default alias is {@code "t"}.
     *
     * @param cteName     the CTE name to use in the FROM clause
     * @param entityClass entity whose column mappings describe the CTE's columns
     */
    public static <T> SelectBuilder<T> fromCte(String cteName, Class<T> entityClass) {
        return fromCte(cteName, entityClass, "t");
    }

    /**
     * Starts building a select from a CTE name with a custom alias.
     *
     * @param cteName     the CTE name to use in the FROM clause
     * @param entityClass entity whose column mappings describe the CTE's columns
     * @param alias       the SQL alias for the CTE table reference
     */
    public static <T> SelectBuilder<T> fromCte(String cteName, Class<T> entityClass, String alias) {
        SelectBuilder<T> b = new SelectBuilder<>(entityClass, alias);
        b.tableNameOverride = cteName;
        return b;
    }

    /** Adds {@code DISTINCT} to the SELECT clause, eliminating duplicate rows. */
    public SelectBuilder<T> distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * Specifies which properties to include in the SELECT clause (in order).
     * Each {@link SFunction} is wrapped as a {@link ColumnExpression}.
     */
    @SafeVarargs
    public final SelectBuilder<T> select(SFunction<T, ?>... props) {
        for (SFunction<T, ?> prop : props) {
            selectedExpressions.add(ColumnExpression.of(prop));
        }
        return this;
    }

    /**
     * Specifies which SQL expressions to include in the SELECT clause (in order).
     * Accepts any combination of {@link ColumnExpression}, {@link io.github.jsbxyyx.jdbcdsl.expr.FunctionExpression},
     * {@link io.github.jsbxyyx.jdbcdsl.expr.AggregateExpression}, and
     * {@link io.github.jsbxyyx.jdbcdsl.expr.LiteralExpression}.
     */
    public SelectBuilder<T> select(SqlExpression<?>... expressions) {
        for (SqlExpression<?> expr : expressions) {
            selectedExpressions.add(expr);
        }
        return this;
    }

    /** Adds a WHERE predicate via a nested builder. */
    public SelectBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        this.where = wb.buildNode();
        return this;
    }

    /** Sets the WHERE predicate directly using a pre-built AST node. */
    public SelectBuilder<T> where(PredicateNode node) {
        this.where = node;
        return this;
    }

    /** Adds a JOIN clause. */
    public <J> SelectBuilder<T> join(Class<J> joinEntity, String joinAlias,
                                     JoinType type, Consumer<OnBuilder> onConsumer) {
        OnBuilder ob = new OnBuilder();
        onConsumer.accept(ob);
        joins.add(new JoinSpec(joinEntity, joinAlias, type, ob.getConditions()));
        return this;
    }

    /** Adds a CROSS JOIN clause (no ON condition required). */
    public <J> SelectBuilder<T> crossJoin(Class<J> joinEntity, String joinAlias) {
        joins.add(new JoinSpec(joinEntity, joinAlias, JoinType.CROSS, List.of()));
        return this;
    }

    /** Sets the ORDER BY clause. */
    public SelectBuilder<T> orderBy(JSort<T> sort) {
        this.sort = sort != null ? sort : JSort.unsorted();
        return this;
    }

    /**
     * Adds GROUP BY columns specified as method references.
     * Each {@link SFunction} is wrapped as a {@link ColumnExpression}.
     */
    @SafeVarargs
    public final SelectBuilder<T> groupBy(SFunction<T, ?>... props) {
        for (SFunction<T, ?> prop : props) {
            groupByExpressions.add(ColumnExpression.of(prop));
        }
        return this;
    }

    /**
     * Adds GROUP BY expressions (columns, functions, etc.).
     */
    public SelectBuilder<T> groupBy(SqlExpression<?>... expressions) {
        for (SqlExpression<?> expr : expressions) {
            groupByExpressions.add(expr);
        }
        return this;
    }

    /**
     * Adds a HAVING predicate via a nested builder.
     * The builder supports both column-based predicates and aggregate-expression predicates.
     */
    public SelectBuilder<T> having(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        this.having = wb.buildNode();
        return this;
    }

    /**
     * Declares a Common Table Expression (CTE) to be prepended as a {@code WITH} clause.
     * Can be called multiple times; CTEs are rendered in declaration order.
     *
     * <p>The CTE body is rendered without ORDER BY (ORDER BY inside a CTE is not valid in
     * SQL-92 and is ignored even when the body spec has one).
     *
     * <p>To reference this CTE as the outer query's FROM source, use
     * {@link #fromCte(String, Class)} instead of {@link #from(Class)}.
     * For CTE references inside WHERE / IN / EXISTS, use {@link WhereBuilder#raw(String)}.
     *
     * @param name the CTE name used in SQL and referenced in FROM / JOIN clauses
     * @param body the SELECT spec defining the CTE body
     */
    public SelectBuilder<T> withCte(String name, SelectSpec<?, ?> body) {
        cteDefs.add(new CteDef(name, body));
        return this;
    }

    /**
     * Finalizes the spec by specifying the DTO class to project results into.
     *
     * <p>The DTO must be a JavaBean with a no-arg constructor and setter methods for each
     * property that should be populated from the query result.
     *
     * @param dtoClass the result type; must have a no-arg constructor and setters
     */
    public <R> SelectSpec<T, R> mapTo(Class<R> dtoClass) {
        return new SelectSpec<>(entityClass, alias, distinct, selectedExpressions, where, joins, sort,
                dtoClass, groupByExpressions, having, List.copyOf(cteDefs), tableNameOverride);
    }

    /**
     * Finalizes the spec by mapping results directly to the entity class.
     *
     * <p>This is a convenience shorthand for {@code mapTo(entityClass)}. When no explicit
     * {@link #select(SFunction[])} is specified, all entity columns are automatically expanded
     * in the SELECT clause (sorted by property name) with property-name aliases, so the entity
     * fields are populated via setter injection.
     *
     * <p>The entity must have a no-arg constructor and setter methods for each property
     * that should be populated from the query result.
     *
     * @return a spec where the result type {@code R} equals the entity type {@code T}
     */
    public SelectSpec<T, T> mapToEntity() {
        return new SelectSpec<>(entityClass, alias, distinct, selectedExpressions, where, joins, sort,
                entityClass, groupByExpressions, having, List.copyOf(cteDefs), tableNameOverride);
    }
}
