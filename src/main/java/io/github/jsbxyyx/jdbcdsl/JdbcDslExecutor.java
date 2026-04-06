package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes jdbc-dsl select queries using {@link NamedParameterJdbcTemplate}.
 *
 * <p>DTO mapping uses constructor projection: columns are selected as {@code c0, c1, ...} and
 * injected into the matching constructor in declaration order.
 *
 * <p>The default {@link Dialect} is {@link Sql2008Dialect}.
 * Pass a custom dialect (e.g., {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect}) as needed.
 */
public final class JdbcDslExecutor {

    private final NamedParameterJdbcTemplate jdbc;
    private final Dialect dialect;

    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new Sql2008Dialect());
    }

    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc, Dialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    /**
     * Executes a SELECT and maps results to {@code R}.
     */
    public <T, R> List<R> select(SelectSpec<T, R> spec) {
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        RowMapper<R> mapper = buildRowMapper(spec.getDtoClass(), spec.getSelectedExpressions().size());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a paginated SELECT and returns a Spring {@link Page}.
     *
     * <p>Note: when the spec contains JOINs, the COUNT query uses {@code COUNT(*)} which may
     * count duplicate rows. See the README for details.
     */
    public <T, R> Page<R> selectPage(SelectSpec<T, R> spec, JPageable<T> pageable) {
        // Merge sort from JPageable into the spec's sort (JPageable sort takes precedence if provided)
        SelectSpec<T, R> effectiveSpec = mergeSort(spec, pageable);

        // Render and execute count query
        RenderedSql countSql = SqlRenderer.renderCount(effectiveSpec);
        Long total = jdbc.queryForObject(countSql.getSql(), countSql.getParams(), Long.class);
        long totalCount = total != null ? total : 0L;

        // Render SELECT with pagination
        RenderedSql selectSql = SqlRenderer.renderSelect(effectiveSpec);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(selectSql.getParams());
        String paginatedSql = dialect.applyPagination(
                selectSql.getSql(), pageable.offset(), pageable.getSize(), paginatedParams);

        RowMapper<R> mapper = buildRowMapper(effectiveSpec.getDtoClass(),
                effectiveSpec.getSelectedExpressions().size());
        List<R> content = jdbc.query(paginatedSql, paginatedParams, mapper);

        return new PageImpl<>(content, pageable.toSpringPageable(), totalCount);
    }

    // ------------------------------------------------------------------ //
    //  RowMapper: constructor projection
    // ------------------------------------------------------------------ //

    private static <R> RowMapper<R> buildRowMapper(Class<R> dtoClass, int columnCount) {
        Constructor<R> ctor = findConstructor(dtoClass, columnCount);
        return (rs, rowNum) -> mapRow(rs, ctor);
    }

    @SuppressWarnings("unchecked")
    private static <R> Constructor<R> findConstructor(Class<R> dtoClass, int paramCount) {
        Constructor<?>[] ctors = dtoClass.getConstructors();
        for (Constructor<?> c : ctors) {
            if (c.getParameterCount() == paramCount) {
                return (Constructor<R>) c;
            }
        }
        // Fall back: try getDeclaredConstructors (e.g., for package-private DTOs)
        Constructor<?>[] declared = dtoClass.getDeclaredConstructors();
        for (Constructor<?> c : declared) {
            if (c.getParameterCount() == paramCount) {
                c.setAccessible(true);
                return (Constructor<R>) c;
            }
        }
        throw new IllegalArgumentException(
                "No constructor with " + paramCount + " parameter(s) found on " + dtoClass.getName()
                + ". Available constructors: " + Arrays.toString(dtoClass.getConstructors()));
    }

    private static <R> R mapRow(ResultSet rs, Constructor<R> ctor) throws SQLException {
        int count = ctor.getParameterCount();
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[count];
        for (int i = 0; i < count; i++) {
            args[i] = rs.getObject("c" + i, paramTypes[i]);
        }
        try {
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to instantiate DTO " + ctor.getDeclaringClass().getName(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private static <T, R> SelectSpec<T, R> mergeSort(SelectSpec<T, R> spec, JPageable<T> pageable) {
        JSort<T> pageableSort = pageable.getSort();
        if (!pageableSort.isEmpty()) {
            return new SelectSpec<>(
                    spec.getEntityClass(),
                    spec.getAlias(),
                    spec.getSelectedExpressions(),
                    spec.getWhere(),
                    spec.getJoins(),
                    pageableSort,
                    spec.getDtoClass(),
                    spec.getGroupByExpressions(),
                    spec.getHaving());
        }
        return spec;
    }
}
