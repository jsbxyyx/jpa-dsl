package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.expr.ColumnExpression;
import io.github.jsbxyyx.jdbcdsl.expr.SqlExpression;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
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
    private SelectSpec<?, ?> subqueryFrom = null;
    private LockMode lockMode = null;

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

    /**
     * Starts building a SELECT whose FROM clause is a derived-table subquery.
     *
     * <p>The generated SQL renders the inner query inline:
     * <pre>{@code
     * FROM (SELECT ...) alias
     * }</pre>
     *
     * <p>The {@code entityClass} provides the column mappings used for type-safe property
     * references in {@code select()}, {@code where()}, etc. It must match the shape of the
     * subquery's projected columns.
     *
     * <p>Example:
     * <pre>{@code
     * SelectSpec<TUser, UserDto> inner = SelectBuilder.from(TUser.class)
     *     .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
     *     .mapToEntity();
     *
     * SelectSpec<TUser, UserDto> outer = SelectBuilder.fromSubquery(inner, "sub", TUser.class)
     *     .select(TUser::getId, TUser::getUsername)
     *     .mapTo(UserDto.class);
     * // → SELECT sub.id AS id, sub.username AS username FROM (SELECT ...) sub
     * }</pre>
     *
     * @param subquery    the inner SELECT spec rendered as the derived table
     * @param alias       the SQL alias for the derived table
     * @param entityClass entity providing the column mappings
     */
    public static <T> SelectBuilder<T> fromSubquery(SelectSpec<?, ?> subquery, String alias, Class<T> entityClass) {
        SelectBuilder<T> b = new SelectBuilder<>(entityClass, alias);
        b.subqueryFrom = subquery;
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

    /**
     * Selects every mapped property of the root or a uniquely matching joined entity.
     * The entity's table alias is resolved from the current query's FROM/JOIN clauses.
     *
     * <p>If the same entity class occurs more than once (for example, a self-join), use
     * {@link #select(Class, String)} to identify the desired table alias explicitly.
     */
    public <J> SelectBuilder<T> select(Class<J> selectedEntityClass) {
        // Resolve the alias during rendering so callers may declare SELECT before JOIN.
        return selectEntityColumns(selectedEntityClass, null);
    }

    /**
     * Selects every mapped property of the root or joined entity identified by {@code tableAlias}.
     * The alias must belong to the supplied entity class in this query.
     */
    public <J> SelectBuilder<T> select(Class<J> selectedEntityClass, String tableAlias) {
        String selectedAlias = resolveEntityAlias(selectedEntityClass, tableAlias);
        return selectEntityColumns(selectedEntityClass, selectedAlias);
    }

    private <J> SelectBuilder<T> selectEntityColumns(Class<J> selectedEntityClass, String selectedAlias) {
        EntityMeta meta = EntityMetaReader.read(selectedEntityClass);
        for (String propertyName : meta.getPropertyToColumn().keySet()) {
            selectedExpressions.add(
                    ColumnExpression.of(new PropertyRef(selectedEntityClass, propertyName), selectedAlias));
        }
        return this;
    }

    /** Adds a WHERE predicate for the root entity. Repeated WHERE calls are combined with AND. */
    public SelectBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        WhereBuilder<T> wb = new WhereBuilder<>(entityClass, alias);
        consumer.accept(wb);
        appendWhere(wb.buildNode());
        return this;
    }

    /**
     * Adds a WHERE predicate for the root or a uniquely matching joined entity.
     * Repeated WHERE calls are combined with AND.
     *
     * <p>If the same entity class occurs more than once, use {@link #where(Class, String, Consumer)}.
     */
    public <J> SelectBuilder<T> where(Class<J> filteredEntityClass, Consumer<WhereBuilder<J>> consumer) {
        // Defer alias resolution until rendering so WHERE may be declared before JOIN.
        WhereBuilder<J> wb = new WhereBuilder<>(filteredEntityClass, null);
        consumer.accept(wb);
        appendWhere(wb.buildNode());
        return this;
    }

    /** Adds a WHERE predicate for the root or joined entity identified by {@code tableAlias}. */
    public <J> SelectBuilder<T> where(
            Class<J> filteredEntityClass, String tableAlias, Consumer<WhereBuilder<J>> consumer) {
        String filteredAlias = resolveEntityAlias(filteredEntityClass, tableAlias);
        WhereBuilder<J> wb = new WhereBuilder<>(filteredEntityClass, filteredAlias);
        consumer.accept(wb);
        appendWhere(wb.buildNode());
        return this;
    }

    /** Adds a pre-built WHERE predicate, combining it with any existing predicate using AND. */
    public SelectBuilder<T> where(PredicateNode node) {
        appendWhere(node);
        return this;
    }

    private void appendWhere(PredicateNode node) {
        if (node == null) {
            return;
        }
        if (where == null) {
            where = node;
        } else {
            List<PredicateNode> children = new ArrayList<>();
            if (where instanceof AndPredicate and) {
                children.addAll(and.getChildren());
            } else {
                children.add(where);
            }
            if (node instanceof AndPredicate and) {
                children.addAll(and.getChildren());
            } else {
                children.add(node);
            }
            where = new AndPredicate(children);
        }
    }

    private String resolveEntityAlias(Class<?> targetEntityClass, String requestedAlias) {
        List<String> matchingAliases = new ArrayList<>();
        if (entityClass.equals(targetEntityClass)) {
            matchingAliases.add(alias);
        }
        for (JoinSpec join : joins) {
            if (join.getJoinEntityClass().equals(targetEntityClass)) {
                matchingAliases.add(join.getAlias());
            }
        }
        if (requestedAlias != null) {
            if (!matchingAliases.contains(requestedAlias)) {
                throw new IllegalArgumentException("Alias '" + requestedAlias + "' does not identify entity "
                        + targetEntityClass.getName() + " in this query");
            }
            return requestedAlias;
        }
        if (matchingAliases.isEmpty()) {
            throw new IllegalArgumentException("Entity " + targetEntityClass.getName()
                    + " is neither the root entity nor a joined entity in this query");
        }
        if (matchingAliases.size() > 1) {
            throw new IllegalStateException("Ambiguous entity " + targetEntityClass.getName()
                    + " appears under multiple aliases " + matchingAliases
                    + "; specify the table alias explicitly");
        }
        return matchingAliases.get(0);
    }

    /** Adds a JOIN clause. */
    public <J> SelectBuilder<T> join(
            Class<J> joinEntity, String joinAlias, JoinType type, Consumer<OnBuilder> onConsumer) {
        OnBuilder ob = new OnBuilder();
        onConsumer.accept(ob);
        joins.add(new JoinSpec(joinEntity, joinAlias, type, ob.getConditions()));
        return this;
    }

    /**
     * Appends a row-level locking clause to the generated SELECT SQL.
     *
     * <p>The exact SQL keyword depends on the {@link LockMode}:
     * <ul>
     *   <li>{@link LockMode#UPDATE} → {@code FOR UPDATE}</li>
     *   <li>{@link LockMode#UPDATE_NOWAIT} → {@code FOR UPDATE NOWAIT}</li>
     *   <li>{@link LockMode#UPDATE_SKIP_LOCKED} → {@code FOR UPDATE SKIP LOCKED}</li>
     *   <li>{@link LockMode#SHARE} → {@code FOR SHARE}</li>
     * </ul>
     *
     * <p>Not all databases support all modes. Check your database documentation.
     *
     * @param mode the row-level locking mode
     */
    public SelectBuilder<T> forUpdate(LockMode mode) {
        this.lockMode = mode;
        return this;
    }

    /**
     * Appends {@code FOR UPDATE} to the generated SELECT SQL (exclusive row-level locking).
     *
     * <p>Shorthand for {@code forUpdate(LockMode.UPDATE)}.
     */
    public SelectBuilder<T> forUpdate() {
        return forUpdate(LockMode.UPDATE);
    }

    /** Adds a CROSS JOIN clause (no ON condition required). */
    public <J> SelectBuilder<T> crossJoin(Class<J> joinEntity, String joinAlias) {
        joins.add(new JoinSpec(joinEntity, joinAlias, JoinType.CROSS, List.of()));
        return this;
    }

    /**
     * Adds a {@code JOIN (SELECT ...) alias ON ...} derived-table join.
     *
     * <p>The {@code entityClass} provides column-name resolution for ON-condition property
     * references — it should match the shape of the subquery's projected columns.
     *
     * <p>Example:
     * <pre>{@code
     * // JOIN (SELECT user_id, COUNT(*) AS order_count FROM t_order GROUP BY user_id) o
     * //   ON o.user_id = t.id
     * SelectSpec<TOrder, TOrder> orderCount = SelectBuilder.from(TOrder.class, "o")
     *     .select(col(TOrder::getUserId), countStar().as("orderCount"))
     *     .groupBy(TOrder::getUserId)
     *     .mapToEntity();
     *
     * SelectBuilder.from(TUser.class)
     *     .joinSubquery(orderCount, TOrder.class, "o", JoinType.LEFT,
     *         on -> on.eq(TOrder::getUserId, "o", TUser::getId, "t"))
     *     .select(...)
     * }</pre>
     *
     * @param subquery    the inner SELECT spec rendered as the JOIN target
     * @param entityClass entity providing column-name resolution for ON conditions
     * @param joinAlias   the SQL alias for the derived table
     * @param type        the JOIN type (INNER, LEFT, RIGHT, FULL)
     * @param onConsumer  configures the ON clause
     */
    public <J> SelectBuilder<T> joinSubquery(
            SelectSpec<?, ?> subquery,
            Class<J> entityClass,
            String joinAlias,
            JoinType type,
            Consumer<OnBuilder> onConsumer) {
        OnBuilder ob = new OnBuilder();
        onConsumer.accept(ob);
        joins.add(JoinSpec.ofSubquery(subquery, entityClass, joinAlias, type, ob.getConditions()));
        return this;
    }

    /** {@code LEFT JOIN (SELECT ...) alias ON ...} convenience overload. */
    public <J> SelectBuilder<T> leftJoinSubquery(
            SelectSpec<?, ?> subquery, Class<J> entityClass, String joinAlias, Consumer<OnBuilder> onConsumer) {
        return joinSubquery(subquery, entityClass, joinAlias, JoinType.LEFT, onConsumer);
    }

    /** {@code INNER JOIN (SELECT ...) alias ON ...} convenience overload. */
    public <J> SelectBuilder<T> innerJoinSubquery(
            SelectSpec<?, ?> subquery, Class<J> entityClass, String joinAlias, Consumer<OnBuilder> onConsumer) {
        return joinSubquery(subquery, entityClass, joinAlias, JoinType.INNER, onConsumer);
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
        return new SelectSpec<>(
                entityClass,
                alias,
                distinct,
                selectedExpressions,
                where,
                joins,
                sort,
                dtoClass,
                groupByExpressions,
                having,
                List.copyOf(cteDefs),
                tableNameOverride,
                subqueryFrom,
                lockMode);
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
        return new SelectSpec<>(
                entityClass,
                alias,
                distinct,
                selectedExpressions,
                where,
                joins,
                sort,
                entityClass,
                groupByExpressions,
                having,
                List.copyOf(cteDefs),
                tableNameOverride,
                subqueryFrom,
                lockMode);
    }
}
