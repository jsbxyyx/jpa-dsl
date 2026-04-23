package io.github.jsbxyyx.jdbcast.spring;

import io.github.jsbxyyx.jdbcast.expr.AggExpr;
import io.github.jsbxyyx.jdbcast.expr.StarExpr;
import io.github.jsbxyyx.jdbcast.renderer.MetaResolver;
import io.github.jsbxyyx.jdbcast.renderer.RenderedSql;
import io.github.jsbxyyx.jdbcast.renderer.SqlRenderer;
import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Convenience executor that bridges the {@code jdbc-ast} SQL AST with
 * Spring's {@link NamedParameterJdbcTemplate}.
 *
 * <p>The executor renders an AST statement to a {@link RenderedSql} (named-parameter SQL +
 * param map) and delegates execution to the underlying template. You retain full access to
 * the template for operations not covered here.
 *
 * <h3>Typical Spring Boot setup</h3>
 * <pre>{@code
 * @Bean
 * JdbcAstExecutor jdbcAstExecutor(NamedParameterJdbcTemplate template) {
 *     return new JdbcAstExecutor(template, new AnsiSqlRenderer(SpringDataRelationalMetaResolver.INSTANCE));
 * }
 * }</pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * TableRef<TUser> u = TableRef.of(TUser.class, "u");
 *
 * // SELECT → list
 * List<TUser> users = executor.query(
 *     SQL.from(u).select(u.star()).where(u.col(TUser::getStatus).eq("ACTIVE")).build(),
 *     TUser.class);
 *
 * // SELECT → single optional
 * Optional<TUser> user = executor.queryOne(
 *     SQL.from(u).select(u.star()).where(u.col(TUser::getId).eq(1L)).build(),
 *     TUser.class);
 *
 * // SELECT → scalar
 * long count = executor.queryForObject(
 *     SQL.from(u).select(countStar()).build(), Long.class);
 *
 * // INSERT (explicit columns)
 * int rows = executor.insert(
 *     SQL.insertInto(TUser.class).set(TUser::getUsername, "alice").build());
 *
 * // INSERT (entity object — maps all non-null fields)
 * long id = executor.insertAndGetKey(new TUser("alice", "ACTIVE"), Long.class);
 *
 * // UPDATE
 * int rows = executor.update(
 *     SQL.update(TUser.class).set(TUser::getStatus, "INACTIVE")
 *         .where(u.col(TUser::getId).eq(1L)).build());
 *
 * // DELETE
 * int rows = executor.delete(
 *     SQL.deleteFrom(TUser.class).where(u.col(TUser::getStatus).eq("DELETED")).build());
 * }</pre>
 */
public class JdbcAstExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlRenderer                renderer;
    private final MetaResolver               meta;

    /**
     * Creates an executor backed by the given template and renderer.
     * The renderer's {@link MetaResolver} is reused for entity-based insert operations.
     *
     * @param jdbcTemplate the Spring named-parameter template
     * @param renderer     an {@link AnsiSqlRenderer} (or subclass) carrying a {@link MetaResolver}
     */
    public JdbcAstExecutor(NamedParameterJdbcTemplate jdbcTemplate, SqlRenderer renderer) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.renderer     = Objects.requireNonNull(renderer,     "renderer");
        this.meta = (renderer instanceof io.github.jsbxyyx.jdbcast.renderer.AnsiSqlRenderer ar)
                ? ar.getMetaResolver()
                : null;
    }

    // ================================================================== //
    //  SELECT
    // ================================================================== //

    /**
     * Executes a SELECT and maps each row to {@code rowType} using
     * {@link BeanPropertyRowMapper} (matches column names to Java property names).
     */
    public <T> List<T> query(SelectStatement stmt, Class<T> rowType) {
        return query(stmt, BeanPropertyRowMapper.newInstance(rowType));
    }

    /**
     * Executes a SELECT with a custom {@link RowMapper}.
     */
    public <T> List<T> query(SelectStatement stmt, RowMapper<T> rowMapper) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.query(r.sql(), r.params(), rowMapper);
    }

    /**
     * Executes a SELECT and returns at most one row, or {@link Optional#empty()} if no row found.
     *
     * @throws IncorrectResultSizeDataAccessException if more than one row is returned
     */
    public <T> Optional<T> queryOne(SelectStatement stmt, Class<T> rowType) {
        return queryOne(stmt, BeanPropertyRowMapper.newInstance(rowType));
    }

    /**
     * Executes a SELECT with a custom mapper and returns at most one row.
     *
     * @throws IncorrectResultSizeDataAccessException if more than one row is returned
     */
    public <T> Optional<T> queryOne(SelectStatement stmt, RowMapper<T> rowMapper) {
        List<T> list = query(stmt, rowMapper);
        if (list.isEmpty())   return Optional.empty();
        if (list.size() == 1) return Optional.of(list.get(0));
        throw new IncorrectResultSizeDataAccessException(1, list.size());
    }

    /**
     * Executes a SELECT that is expected to return a single scalar value
     * (e.g., {@code COUNT(*)}, {@code MAX(age)}).
     *
     * @param scalarType the expected Java type of the single column
     * @throws IncorrectResultSizeDataAccessException if not exactly one row is returned
     */
    public <T> T queryForObject(SelectStatement stmt, Class<T> scalarType) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.queryForObject(r.sql(), r.params(), scalarType);
    }

    /**
     * Executes a SELECT and returns each row as a {@code Map<columnName, value>}.
     * Useful for ad-hoc projections without a DTO class.
     */
    public List<Map<String, Object>> queryForList(SelectStatement stmt) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.queryForList(r.sql(), r.params());
    }

    // ================================================================== //
    //  PAGINATION
    // ================================================================== //

    /**
     * Executes a paginated SELECT and returns a {@link PageResult} containing the current-page
     * rows and the total row count.
     *
     * <p>Pass the base query <em>without</em> LIMIT / OFFSET — this method applies them
     * automatically based on {@code pageNumber} and {@code pageSize}.
     *
     * <p>The count query is derived automatically by replacing the SELECT list with
     * {@code COUNT(*)} and stripping ORDER BY, LIMIT, OFFSET, and lock hints.
     * For queries with GROUP BY, provide an explicit count statement via
     * {@link #queryPage(SelectStatement, SelectStatement, RowMapper, long, int)}.
     *
     * <pre>{@code
     * TableRef<TUser> u = TableRef.of(TUser.class, "u");
     *
     * PageResult<TUser> page = executor.queryPage(
     *     SQL.from(u).select(u.star())
     *        .where(w -> w.eq(u.col(TUser::getStatus), "ACTIVE"))
     *        .orderBy(u.col(TUser::getAge).desc()),
     *     TUser.class, 0, 20);
     *
     * // page.content()   → List<TUser> (current page)
     * // page.total()     → total matching rows
     * // page.totalPages()→ total pages
     * }</pre>
     *
     * @param baseStmt   the base SELECT statement (no LIMIT/OFFSET)
     * @param rowType    the row type to map to via {@link BeanPropertyRowMapper}
     * @param pageNumber zero-based page number
     * @param pageSize   number of rows per page
     */
    public <T> PageResult<T> queryPage(SelectStatement baseStmt, Class<T> rowType,
                                       long pageNumber, int pageSize) {
        return queryPage(baseStmt, BeanPropertyRowMapper.newInstance(rowType), pageNumber, pageSize);
    }

    /**
     * Executes a paginated SELECT with a custom {@link RowMapper}.
     *
     * @see #queryPage(SelectStatement, Class, long, int)
     */
    public <T> PageResult<T> queryPage(SelectStatement baseStmt, RowMapper<T> rowMapper,
                                       long pageNumber, int pageSize) {
        SelectStatement countStmt = deriveCountStatement(baseStmt);
        return queryPage(baseStmt, countStmt, rowMapper, pageNumber, pageSize);
    }

    /**
     * Executes a paginated SELECT with an explicit count statement.
     *
     * <p>Use this overload for queries involving GROUP BY, DISTINCT, or any case where
     * the auto-derived count is incorrect.
     *
     * <pre>{@code
     * TableRef<TUser> u = TableRef.of(TUser.class, "u");
     *
     * // Manual count query for GROUP BY
     * SelectStatement countStmt = SQL.from(u)
     *     .select(SQL.countStar())
     *     .where(w -> w.eq(u.col(TUser::getStatus), "ACTIVE"))
     *     .build();
     *
     * PageResult<UserStatusCount> page = executor.queryPage(
     *     baseStmt, countStmt, rowMapper, 0, 20);
     * }</pre>
     */
    public <T> PageResult<T> queryPage(SelectStatement baseStmt, SelectStatement countStmt,
                                       RowMapper<T> rowMapper, long pageNumber, int pageSize) {
        // Count
        RenderedSql cr = renderer.render(countStmt);
        Long total = jdbcTemplate.queryForObject(cr.sql(), cr.params(), Long.class);
        if (total == null || total == 0) {
            return new PageResult<>(Collections.emptyList(), 0L, pageNumber, pageSize);
        }

        // Data — apply LIMIT / OFFSET
        SelectStatement pageStmt = withPage(baseStmt, pageNumber, pageSize);
        RenderedSql dr = renderer.render(pageStmt);
        List<T> content = jdbcTemplate.query(dr.sql(), dr.params(), rowMapper);
        return new PageResult<>(content, total, pageNumber, pageSize);
    }

    // ------------------------------------------------------------------ //
    //  Pagination helpers
    // ------------------------------------------------------------------ //

    /**
     * Derives a {@code COUNT(*)} statement from the given SELECT by replacing
     * the SELECT list, clearing ORDER BY / LIMIT / OFFSET / lock hints.
     * GROUP BY and HAVING are also cleared — this is correct for non-grouped queries.
     * For GROUP BY queries, supply an explicit count statement.
     */
    private static SelectStatement deriveCountStatement(SelectStatement s) {
        return new SelectStatement(
                s.with(),
                false,                                            // no DISTINCT
                List.of(AggExpr.of("COUNT", StarExpr.ALL)),      // SELECT COUNT(*)
                s.from(),
                s.joins(),
                s.where(),
                Collections.emptyList(),                          // no GROUP BY
                null,                                             // no HAVING
                Collections.emptyList(),                          // no ORDER BY
                null,                                             // no LIMIT
                null,                                             // no OFFSET
                null,                                             // no lock
                null);                                            // no SET OP
    }

    /** Returns a new {@link SelectStatement} with LIMIT/OFFSET applied for the given page. */
    private static SelectStatement withPage(SelectStatement s, long pageNumber, int pageSize) {
        return new SelectStatement(
                s.with(), s.distinct(), s.select(), s.from(), s.joins(),
                s.where(), s.groupBy(), s.having(), s.orderBy(),
                (long) pageSize,
                pageNumber * pageSize,
                s.lockMode(), s.setOp());
    }

    // ================================================================== //
    //  INSERT (explicit column assignments)
    // ================================================================== //

    /**
     * Executes an INSERT built with explicit {@code .set()} column assignments.
     *
     * @return the number of rows affected
     */
    public int insert(InsertStatement stmt) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.update(r.sql(), r.params());
    }

    /**
     * Executes an INSERT and returns the auto-generated primary key.
     *
     * @param keyType the Java type of the generated key (e.g., {@code Long.class})
     */
    public <K> K insertAndGetKey(InsertStatement stmt, Class<K> keyType) {
        RenderedSql r   = renderer.render(stmt);
        KeyHolder   kh  = new GeneratedKeyHolder();
        jdbcTemplate.update(r.sql(), new MapSqlParameterSource(r.params()), kh);
        return keyType.cast(Objects.requireNonNull(kh.getKey(),
                "No generated key returned — check that the table has an IDENTITY/SERIAL column"));
    }

    // ================================================================== //
    //  INSERT (entity object — maps all non-null fields)
    // ================================================================== //

    /**
     * Inserts an entity object by extracting every field value and mapping it to
     * the corresponding column name via the configured {@link MetaResolver}.
     *
     * <p>Fields with a {@code null} value are included in the INSERT (written as SQL NULL).
     * Exclude them upstream if needed.
     *
     * @param entity the entity to insert
     * @return the number of rows affected
     */
    public int insert(Object entity) {
        requireMeta("insert(entity)");
        EntitySql es = buildEntityInsertSql(entity.getClass(), entity);
        return jdbcTemplate.update(es.sql, es.params);
    }

    /**
     * Inserts an entity object and returns the auto-generated primary key.
     */
    public <K> K insertAndGetKey(Object entity, Class<K> keyType) {
        requireMeta("insertAndGetKey(entity, keyType)");
        EntitySql es = buildEntityInsertSql(entity.getClass(), entity);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(es.sql, new MapSqlParameterSource(es.params), kh);
        return keyType.cast(Objects.requireNonNull(kh.getKey(),
                "No generated key returned"));
    }

    // ================================================================== //
    //  UPDATE
    // ================================================================== //

    /**
     * Executes an UPDATE built with explicit {@code .set()} assignments.
     *
     * @return the number of rows affected
     */
    public int update(UpdateStatement stmt) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.update(r.sql(), r.params());
    }

    // ================================================================== //
    //  DELETE
    // ================================================================== //

    /**
     * Executes a DELETE.
     *
     * @return the number of rows affected
     */
    public int delete(DeleteStatement stmt) {
        RenderedSql r = renderer.render(stmt);
        return jdbcTemplate.update(r.sql(), r.params());
    }

    // ================================================================== //
    //  Raw template access
    // ================================================================== //

    /**
     * Returns the underlying {@link NamedParameterJdbcTemplate} for operations
     * not covered by this executor.
     */
    public NamedParameterJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    // ================================================================== //
    //  Entity-based INSERT helpers
    // ================================================================== //

    private EntitySql buildEntityInsertSql(Class<?> entityClass, Object entity) {
        String table = meta.tableName(entityClass);

        // Collect all fields (including inherited)
        List<Field> fields = new ArrayList<>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                f.setAccessible(true);
                fields.add(f);
            }
            current = current.getSuperclass();
        }

        StringBuilder colPart = new StringBuilder();
        StringBuilder valPart = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        boolean first = true;

        for (Field f : fields) {
            // Skip static, synthetic fields
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (f.isSynthetic()) continue;

            String colName   = meta.columnName(entityClass, f.getName());
            String paramName = "e_" + colName;
            Object value;
            try {
                value = f.get(entity);
            } catch (IllegalAccessException ex) {
                continue;
            }

            if (!first) { colPart.append(", "); valPart.append(", "); }
            colPart.append(colName);
            valPart.append(":").append(paramName);
            params.put(paramName, value);
            first = false;
        }

        String sql = "INSERT INTO " + table + " (" + colPart + ") VALUES (" + valPart + ")";
        return new EntitySql(sql, params);
    }

    private void requireMeta(String method) {
        if (meta == null) {
            throw new IllegalStateException(
                    method + " requires a MetaResolver. Pass an AnsiSqlRenderer to the constructor.");
        }
    }

    private record EntitySql(String sql, Map<String, Object> params) {}
}
