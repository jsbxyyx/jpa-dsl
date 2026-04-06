package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect;
import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes jdbc-dsl select queries using {@link NamedParameterJdbcTemplate}.
 *
 * <p>Result mapping uses a JavaBean setter strategy: each column label from
 * {@link ResultSetMetaData#getColumnLabel(int)} is matched <em>case-insensitively</em> to a bean
 * property setter. If no setter is found for a column, the value is injected directly into the
 * field with the same name (case-insensitive). This supports entities where the primary key field
 * has no setter (e.g. JPA {@code @Id}).
 *
 * <p>DTOs and entities must provide a public (or package-private) no-arg constructor.
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
     * Executes a SELECT and maps results to {@code R} via setter injection.
     */
    public <T, R> List<R> select(SelectSpec<T, R> spec) {
        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        RowMapper<R> mapper = buildBeanRowMapper(spec.getDtoClass());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a paginated SELECT and returns a Spring {@link Page}.
     *
     * <p>Note: when the spec contains JOINs, the COUNT query uses {@code COUNT(*)} which may
     * count duplicate rows. See the README for details.
     */
    public <T, R> Page<R> selectPage(SelectSpec<T, R> spec, JPageable<T> pageable) {
        SelectSpec<T, R> effectiveSpec = mergeSort(spec, pageable);

        RenderedSql countSql = SqlRenderer.renderCount(effectiveSpec);
        Long total = jdbc.queryForObject(countSql.getSql(), countSql.getParams(), Long.class);
        long totalCount = total != null ? total : 0L;

        RenderedSql selectSql = SqlRenderer.renderSelect(effectiveSpec);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(selectSql.getParams());
        String paginatedSql = dialect.applyPagination(
                selectSql.getSql(), pageable.offset(), pageable.getSize(), paginatedParams);

        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        List<R> content = jdbc.query(paginatedSql, paginatedParams, mapper);

        return new PageImpl<>(content, pageable.toSpringPageable(), totalCount);
    }

    // ------------------------------------------------------------------ //
    //  RowMapper: JavaBean setter mapping with field fallback
    // ------------------------------------------------------------------ //

    /** Per-class cache: lowercase-label → writable PropertyDescriptor. */
    private static final ConcurrentHashMap<Class<?>, Map<String, PropertyDescriptor>> PROP_CACHE =
            new ConcurrentHashMap<>();

    /** Per-class cache: lowercase-fieldName → accessible Field. */
    private static final ConcurrentHashMap<Class<?>, Map<String, Field>> FIELD_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Builds a {@link RowMapper} that maps each result-set row to an instance of {@code beanClass}.
     *
     * <p>Mapping strategy for each column label:
     * <ol>
     *   <li>Normalize the label to lowercase (handles JDBC drivers that upper-case aliases).</li>
     *   <li>Look up a writable property by the lowercase label and call its setter.</li>
     *   <li>If no setter matches, look up a field by the lowercase label and inject directly.</li>
     *   <li>Columns with no matching property or field are silently ignored.</li>
     * </ol>
     */
    private static <R> RowMapper<R> buildBeanRowMapper(Class<R> beanClass) {
        Map<String, PropertyDescriptor> propMap = buildPropertyMap(beanClass);
        Map<String, Field> fieldMap = buildFieldMap(beanClass);
        ConversionService conversionService = DefaultConversionService.getSharedInstance();

        return (rs, rowNum) -> {
            R instance = newInstance(beanClass);
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isBlank()) {
                    label = meta.getColumnName(i);
                }
                if (label == null || label.isBlank()) continue;

                String key = label.toLowerCase(Locale.ROOT);
                Object value = JdbcUtils.getResultSetValue(rs, i);

                PropertyDescriptor pd = propMap.get(key);
                if (pd != null) {
                    Method writeMethod = pd.getWriteMethod();
                    Class<?> propType = pd.getPropertyType();
                    Object converted = convertValue(value, propType, conversionService);
                    try {
                        writeMethod.invoke(instance, converted);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(
                                "Cannot invoke setter '" + writeMethod.getName() + "' on "
                                + beanClass.getName(), e);
                    }
                } else {
                    Field f = fieldMap.get(key);
                    if (f != null && value != null) {
                        Object converted = convertValue(value, f.getType(), conversionService);
                        try {
                            f.set(instance, converted);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(
                                    "Cannot set field '" + f.getName() + "' on "
                                    + beanClass.getName(), e);
                        }
                    }
                }
            }
            return instance;
        };
    }

    private static Object convertValue(Object value, Class<?> targetType,
                                       ConversionService cs) {
        if (value == null || targetType == null) return value;
        if (targetType.isInstance(value)) return value;
        if (cs.canConvert(value.getClass(), targetType)) {
            return cs.convert(value, targetType);
        }
        return value;
    }

    private static Map<String, PropertyDescriptor> buildPropertyMap(Class<?> beanClass) {
        return PROP_CACHE.computeIfAbsent(beanClass, cls -> {
            Map<String, PropertyDescriptor> map = new HashMap<>();
            for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(cls)) {
                if (pd.getWriteMethod() != null) {
                    map.put(pd.getName().toLowerCase(Locale.ROOT), pd);
                }
            }
            return map;
        });
    }

    private static Map<String, Field> buildFieldMap(Class<?> beanClass) {
        return FIELD_CACHE.computeIfAbsent(beanClass, cls -> {
            Map<String, Field> map = new HashMap<>();
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    String key = f.getName().toLowerCase(Locale.ROOT);
                    if (!map.containsKey(key)) {
                        f.setAccessible(true);
                        map.put(key, f);
                    }
                }
                c = c.getSuperclass();
            }
            return map;
        });
    }

    @SuppressWarnings("unchecked")
    private static <R> R newInstance(Class<R> beanClass) throws SQLException {
        try {
            java.lang.reflect.Constructor<R> ctor = beanClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new SQLException(
                    "No no-arg constructor found on " + beanClass.getName()
                    + ". Setter-based mapping requires a public (or accessible) no-arg constructor.",
                    e);
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
