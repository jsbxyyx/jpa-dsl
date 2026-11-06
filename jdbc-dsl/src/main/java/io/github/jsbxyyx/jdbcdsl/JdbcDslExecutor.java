package io.github.jsbxyyx.jdbcdsl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.BeanMappingMetaFactory;
import io.github.jsbxyyx.jdbcdsl.bean.ConverterRegistry;
import io.github.jsbxyyx.jdbcdsl.bean.DefaultConverterRegistry;
import io.github.jsbxyyx.jdbcdsl.bean.JavaBeanMeta;
import io.github.jsbxyyx.jdbcdsl.bean.MapperCache;
import io.github.jsbxyyx.jdbcdsl.bean.MappingMode;
import io.github.jsbxyyx.jdbcdsl.bean.PropertyAccessor;
import io.github.jsbxyyx.jdbcdsl.bean.RecordMappingMeta;
import io.github.jsbxyyx.jdbcdsl.bean.ResultSetMapper;
import io.github.jsbxyyx.jdbcdsl.bean.ScalarRowMapper;
import io.github.jsbxyyx.jdbcdsl.bean.UnknownColumnBehavior;
import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.DialectDetector;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executes jdbc-dsl select queries using {@link NamedParameterJdbcTemplate}.
 *
 * <p>Result mapping uses a JavaBean setter strategy: each column label from
 * {@link ResultSetMetaData#getColumnLabel(int)} is matched <em>case-insensitively</em> to a bean
 * property setter. If no setter is found for a column, the value is injected directly into the
 * field with the same name (case-insensitive). This supports entities where the primary key field
 * has no setter (e.g. JPA {@code @Id}).
 *
 * <p>JavaBean DTOs and entities must provide an accessible no-arg constructor.
 * Record projections are constructed using their canonical constructor.
 *
 * <p>The default constructor detects the {@link Dialect} from the DataSource.
 * Pass an explicit dialect (e.g., {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect}) as needed.
 */
public final class JdbcDslExecutor {

    // Avoid access-plan setup overhead for small batches.
    private static final int MIN_BATCH_ROWS_FOR_ACCESS_PLAN = 16;

    private final NamedParameterJdbcTemplate jdbc;
    private final Dialect dialect;
    private final JdbcDslCacheManager cacheManager;
    private final BeanMappingMetaFactory beanMappingMetaFactory;
    private final MapperCache mapperCache;
    private final ConverterRegistry converterRegistry;
    private final MappingMode mappingMode;
    private final BooleanSupplier allowNullToPrimitiveDefault;
    private final BooleanSupplier logicalDeleteAutoFilter;
    private final BooleanSupplier allowEmptyWhere;
    private final Function<Class<?>, EntityMeta> metadata;
    private TimeProvider timeProvider = LocalDateTime::now;

    /**
     * Creates a {@link JdbcDslExecutor} that auto-detects the {@link Dialect} from the
     * DataSource associated with {@code jdbc}.
     *
     * <p>The detection probes {@link java.sql.DatabaseMetaData#getDatabaseProductName()} and
     * selects the matching dialect. If detection fails, construction fails rather than using
     * an incompatible fallback; pass an explicit dialect when detection is unavailable.
     *
     * @param jdbc the template whose DataSource is used for dialect detection
     * @throws IllegalStateException if the dialect cannot be detected
     */
    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc) {
        this(
                jdbc,
                DialectDetector.detect(jdbc.getJdbcTemplate().getDataSource()),
                new JdbcDslCacheManager(),
                new BeanMappingMetaFactory());
    }

    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc, Dialect dialect) {
        this(jdbc, dialect, new JdbcDslCacheManager(), new BeanMappingMetaFactory());
    }

    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc, Dialect dialect, JdbcDslCacheManager cacheManager) {
        this(jdbc, dialect, cacheManager, new BeanMappingMetaFactory());
    }

    public JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory) {
        this(
                jdbc,
                dialect,
                cacheManager,
                beanMappingMetaFactory,
                new DefaultConverterRegistry(DefaultConversionService.getSharedInstance()),
                UnknownColumnBehavior.NONE);
    }

    /**
     * Creates an executor with a custom conversion registry and unknown-column policy.
     * Mapper caches are isolated by executor, even when the cache manager is shared.
     */
    public JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns) {
        this(
                jdbc,
                dialect,
                cacheManager,
                beanMappingMetaFactory,
                converterRegistry,
                unknownColumns,
                MappingMode.STRICT);
    }

    public JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode) {
        this(
                jdbc,
                dialect,
                cacheManager,
                beanMappingMetaFactory,
                converterRegistry,
                unknownColumns,
                mappingMode,
                JdbcDslConfig::isAllowNullToPrimitiveDefault,
                JdbcDslConfig::isLogicalDeleteAutoFilter,
                JdbcDslConfig::isAllowEmptyWhere,
                EntityMetaReader::read);
    }

    /**
     * Creates an executor with fixed NULL and logical-delete policies independent of legacy static configuration.
     */
    public JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode,
            boolean allowNullToPrimitiveDefault,
            boolean logicalDeleteAutoFilter) {
        this(
                jdbc,
                dialect,
                cacheManager,
                beanMappingMetaFactory,
                converterRegistry,
                unknownColumns,
                mappingMode,
                () -> allowNullToPrimitiveDefault,
                () -> logicalDeleteAutoFilter,
                JdbcDslConfig::isAllowEmptyWhere,
                EntityMetaReader::read);
    }

    /**
     * Creates an executor with fixed naming, empty-WHERE, NULL and logical-delete policies.
     * Type-safe column references are resolved with this executor's naming strategy.
     */
    public JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode,
            boolean allowNullToPrimitiveDefault,
            boolean logicalDeleteAutoFilter,
            boolean allowEmptyWhere,
            NamingStrategy namingStrategy) {
        this(
                jdbc,
                dialect,
                cacheManager,
                beanMappingMetaFactory,
                converterRegistry,
                unknownColumns,
                mappingMode,
                () -> allowNullToPrimitiveDefault,
                () -> logicalDeleteAutoFilter,
                () -> allowEmptyWhere,
                fixedMetadata(namingStrategy));
    }

    private static Function<Class<?>, EntityMeta> fixedMetadata(NamingStrategy namingStrategy) {
        java.util.Objects.requireNonNull(namingStrategy, "namingStrategy");
        Cache<Class<?>, EntityMeta> cache = Caffeine.newBuilder()
                .maximumSize(JdbcDslCacheManager.DEFAULT_BEAN_MAPPING_CACHE_MAX_SIZE)
                .build();
        return type -> cache.get(type, key -> EntityMetaReader.read(key, namingStrategy));
    }

    private JdbcDslExecutor(
            NamedParameterJdbcTemplate jdbc,
            Dialect dialect,
            JdbcDslCacheManager cacheManager,
            BeanMappingMetaFactory beanMappingMetaFactory,
            ConverterRegistry converterRegistry,
            UnknownColumnBehavior unknownColumns,
            MappingMode mappingMode,
            BooleanSupplier allowNullToPrimitiveDefault,
            BooleanSupplier logicalDeleteAutoFilter,
            BooleanSupplier allowEmptyWhere,
            Function<Class<?>, EntityMeta> metadata) {
        this.jdbc = jdbc;
        this.dialect = dialect;
        this.cacheManager = cacheManager;
        this.beanMappingMetaFactory = beanMappingMetaFactory;
        this.converterRegistry = java.util.Objects.requireNonNull(converterRegistry, "converterRegistry");
        this.mappingMode = java.util.Objects.requireNonNull(mappingMode, "mappingMode");
        this.allowNullToPrimitiveDefault = allowNullToPrimitiveDefault;
        this.logicalDeleteAutoFilter = logicalDeleteAutoFilter;
        this.allowEmptyWhere = allowEmptyWhere;
        this.metadata = metadata;
        this.mapperCache = new MapperCache(
                cacheManager, converterRegistry, unknownColumns, mappingMode, allowNullToPrimitiveDefault);
    }

    /** Creates an update builder using this executor's empty-WHERE policy. */
    public <T> UpdateBuilder<T> updateBuilder(Class<T> entityClass) {
        return UpdateBuilder.from(entityClass, allowEmptyWhere);
    }

    /** Creates a delete builder using this executor's empty-WHERE policy. */
    public <T> DeleteBuilder<T> deleteBuilder(Class<T> entityClass) {
        return DeleteBuilder.from(entityClass, allowEmptyWhere);
    }

    private void requireWhere(PredicateNode where) {
        if (where == null && !allowEmptyWhere.getAsBoolean()) {
            throw new IllegalStateException("UPDATE or DELETE without WHERE is disabled for this executor");
        }
    }

    /**
     * Replaces the time provider used for auto-filling
     * {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} fields.
     *
     * <p>The default provider returns {@link LocalDateTime#now()}.
     * Inject a fixed-time provider in tests for deterministic assertions:
     * <pre>{@code
     * executor.setTimeProvider(() -> LocalDateTime.of(2024, 1, 1, 0, 0, 0));
     * }</pre>
     *
     * @param timeProvider the new time provider (must not be {@code null})
     */
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider == null) {
            throw new IllegalArgumentException("timeProvider must not be null");
        }
        this.timeProvider = timeProvider;
    }

    // ------------------------------------------------------------------ //
    //  Raw SQL escape hatch
    // ------------------------------------------------------------------ //

    /**
     * Executes an arbitrary SQL query and maps each row to {@code resultClass} via the same
     * setter-injection strategy used by {@link #select(SelectSpec)}.
     *
     * <p>This is the escape hatch for queries that cannot be expressed with the typed DSL
     * (e.g. cross-database-specific functions, recursive CTEs, UNION queries).
     *
     * <p><strong>Warning:</strong> never pass user-controlled data directly in {@code sql}.
     * Bind user input as named parameters in {@code params}.
     *
     * @param sql         parameterized SQL string with {@code :name} placeholders
     * @param params      map of named parameter values (may be empty, not null)
     * @param resultClass JavaBean class to map each row into
     */
    public <R> List<R> query(String sql, Map<String, Object> params, Class<R> resultClass) {
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        return jdbc.query(sql, params, mapper);
    }

    /** Executes raw SQL with an explicitly supplied row mapping. */
    public <R> List<R> queryMapped(String sql, Map<String, Object> params, RowMapper<R> mapper) {
        return jdbc.query(sql, params, mapper);
    }

    /**
     * Maps and consumes each row without collecting the entire result in a list.
     * The callback runs synchronously while the JDBC resources are open.
     *
     * <p>Driver buffering is independent of this method. Configure fetch size and any
     * driver-specific cursor or transaction settings on the underlying JdbcTemplate/DataSource.
     * Do not pass user-controlled SQL directly; bind values through {@code params}.
     *
     * @param sql parameterized SQL
     * @param params named parameters
     * @param resultClass scalar, bean, or record result type
     * @param consumer callback invoked once per row, including null scalar values
     * @param <R> result type
     */
    public <R> void queryEach(
            String sql, Map<String, Object> params, Class<R> resultClass, Consumer<? super R> consumer) {
        queryEachMapped(sql, params, buildBeanRowMapper(resultClass), consumer);
    }

    /**
     * Consumes each raw SQL result row using an explicitly supplied mapper.
     * Mapping and callback exceptions stop iteration and propagate to the caller.
     */
    public <R> void queryEachMapped(
            String sql, Map<String, Object> params, RowMapper<R> mapper, Consumer<? super R> consumer) {
        java.util.Objects.requireNonNull(mapper, "mapper");
        java.util.Objects.requireNonNull(consumer, "consumer");
        jdbc.query(sql, params, (ResultSetExtractor<Void>) rs -> {
            int rowNum = 0;
            while (rs.next()) {
                consumer.accept(mapper.mapRow(rs, rowNum++));
            }
            return null;
        });
    }

    /**
     * Executes an arbitrary SQL query and returns the first row mapped to {@code resultClass},
     * or {@code null} if no rows match.
     */
    public <R> R queryOne(String sql, Map<String, Object> params, Class<R> resultClass) {
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        return jdbc.query(sql, params, (ResultSetExtractor<R>) rs -> {
            if (!rs.next()) {
                return null;
            }
            return mapper.mapRow(rs, 0);
        });
    }

    /**
     * Executes an arbitrary SQL DML statement (INSERT, UPDATE, or DELETE) and returns the
     * number of affected rows.
     *
     * <p><strong>Warning:</strong> never pass user-controlled data directly in {@code sql}.
     */
    public int update(String sql, Map<String, Object> params) {
        return jdbc.update(sql, params);
    }

    // ------------------------------------------------------------------ //
    //  Type-safe DSL queries
    // ------------------------------------------------------------------ //

    /**
     * Executes a SELECT and maps results to {@code R} via setter injection.
     */
    public <T, R> List<R> select(SelectSpec<T, R> spec) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /** Executes a DSL query with an explicitly supplied row mapping. */
    public <T, R> List<R> selectMapped(SelectSpec<T, ?> spec, RowMapper<R> mapper) {
        SelectSpec<T, ?> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Maps and consumes a DSL query one row at a time without building a result list.
     * The same logical-delete filtering and mapping rules as {@link #select(SelectSpec)}
     * apply. Driver buffering and fetch-size configuration are described by
     * {@link #queryEach(String, Map, Class, Consumer)}.
     */
    public <T, R> void selectEach(SelectSpec<T, R> spec, Consumer<? super R> consumer) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        queryEachMapped(
                rendered.getSql(), rendered.getParams(), buildBeanRowMapper(effectiveSpec.getDtoClass()), consumer);
    }

    /**
     * Consumes a paged or sorted-unpaged DSL query without executing a COUNT query.
     * Pagination and sort follow {@link #select(SelectSpec, JPageable)}.
     */
    public <T, R> void selectEach(SelectSpec<T, R> spec, JPageable<T> pageable, Consumer<? super R> consumer) {
        selectEachMapped(spec, pageable, buildBeanRowMapper(spec.getDtoClass()), consumer);
    }

    /**
     * Consumes a DSL query one row at a time using an explicitly supplied mapper.
     */
    public <T, R> void selectEachMapped(SelectSpec<T, ?> spec, RowMapper<R> mapper, Consumer<? super R> consumer) {
        SelectSpec<T, ?> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        queryEachMapped(rendered.getSql(), rendered.getParams(), mapper, consumer);
    }

    /** Consumes a paged or sorted-unpaged DSL query using an explicitly supplied mapper. */
    public <T, R> void selectEachMapped(
            SelectSpec<T, ?> spec, JPageable<T> pageable, RowMapper<R> mapper, Consumer<? super R> consumer) {
        SelectSpec<T, ?> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> params = new LinkedHashMap<>(rendered.getParams());
        String sql = rendered.getSql();
        if (pageable.isPaged()) {
            sql = dialect.applyPagination(sql, pageable.offset(), pageable.getSize(), params);
        }
        queryEachMapped(sql, params, mapper, consumer);
    }

    /**
     * Finds an entity by its mapped primary key, respecting logical-delete filters.
     *
     * @param entityClass entity class
     * @param id primary key value
     * @return matching entity, or {@code null} when no row matches
     * @throws IllegalStateException if the entity class has no {@code @Id} field
     */
    public <T> T selectById(Class<T> entityClass, Object id) {
        EntityMeta meta = metadata.apply(entityClass);
        String idPropName = meta.getIdPropertyName();
        if (idPropName == null) {
            throw new IllegalStateException("No @Id field found on " + entityClass.getName());
        }
        PropertyRef idRef = new PropertyRef(entityClass, idPropName);
        LeafPredicate where = LeafPredicate.of(idRef, "t", LeafPredicate.Op.EQ, id);
        SelectSpec<T, T> spec = new SelectSpec<>(
                entityClass,
                "t",
                false,
                List.of(),
                where,
                List.of(),
                JSort.unsorted(),
                entityClass,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
        List<T> results = select(spec);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes a COUNT query for the given spec.
     *
     * <p>For queries with JOINs and no GROUP BY, the count includes every row produced by the
     * JOIN, matching SQL row semantics; a root entity matching multiple joined rows contributes
     * multiple rows. For queries with GROUP BY, the count is the number of groups after HAVING
     * is applied.
     */
    public <T, R> long count(SelectSpec<T, R> spec) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql countSql = SqlRenderer.renderCount(effectiveSpec, metadata);
        Long total = jdbc.queryForObject(countSql.getSql(), countSql.getParams(), Long.class);
        return total != null ? total : 0L;
    }

    /**
     * Executes a paginated SELECT (applying sort, offset, and limit from {@code pageable}) and
     * returns the matching rows as a list. No COUNT query is executed.
     *
     * <p>The pagination SQL is generated by the configured {@link Dialect}, ensuring
     * multi-database compatibility.
     *
     * <p>Mapping strategy is identical to {@link #select(SelectSpec)}.
     */
    public <T, R> List<R> select(SelectSpec<T, R> spec, JPageable<T> pageable) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> queryParams = new LinkedHashMap<>(rendered.getParams());
        String querySql = rendered.getSql();
        if (pageable.isPaged()) {
            querySql = dialect.applyPagination(querySql, pageable.offset(), pageable.getSize(), queryParams);
        }
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        return jdbc.query(querySql, queryParams, mapper);
    }

    /**
     * Executes a SELECT with {@code LIMIT 1} and returns the first matching result, or
     * {@code null} if no rows match.
     *
     * <p>The LIMIT clause is applied via the configured {@link Dialect} (same logic as
     * pagination), so the generated SQL is compatible across databases.
     *
     * <p>Mapping strategy is identical to {@link #select(SelectSpec)}.
     */
    public <T, R> R findOne(SelectSpec<T, R> spec) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> limitedParams = new LinkedHashMap<>(rendered.getParams());
        String limitedSql = dialect.applyPagination(rendered.getSql(), 0, 1, limitedParams);
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        List<R> results = jdbc.query(limitedSql, limitedParams, mapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes a SELECT with {@code LIMIT 1} (using the sort from {@code pageable}) and returns
     * the first matching result, or {@code null} if no rows match. No COUNT query is executed.
     *
     * <p>Only the sort from {@code pageable} is applied; offset and size from pageable are ignored
     * (the query always fetches at most one row starting from offset 0).
     *
     * <p>The pagination SQL is generated by the configured {@link Dialect}, ensuring
     * multi-database compatibility.
     *
     * <p>Mapping strategy is identical to {@link #select(SelectSpec)}.
     */
    public <T, R> R findOne(SelectSpec<T, R> spec, JPageable<T> pageable) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> limitedParams = new LinkedHashMap<>(rendered.getParams());
        String limitedSql = dialect.applyPagination(rendered.getSql(), 0, 1, limitedParams);
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        List<R> results = jdbc.query(limitedSql, limitedParams, mapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes a {@link UnionSpec} ({@code UNION} / {@code UNION ALL}) and returns all rows
     * mapped to the common result type {@code R}.
     *
     * <p>Example:
     * <pre>{@code
     * UnionSpec<NameDto> union = UnionSpec
     *     .of(SelectBuilder.from(User.class).select(User::getName).mapTo(NameDto.class))
     *     .unionAll(SelectBuilder.from(Admin.class).select(Admin::getName).mapTo(NameDto.class))
     *     .build();
     *
     * List<NameDto> results = executor.union(union);
     * }</pre>
     */
    public <R> List<R> union(UnionSpec<R> spec) {
        RenderedSql rendered = SqlRenderer.renderUnion(applyLogicalDeleteFilter(spec), metadata);
        RowMapper<R> mapper = buildBeanRowMapper(spec.getDtoClass());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a paginated {@link UnionSpec} and returns a page of results.
     */
    public <R> List<R> union(UnionSpec<R> spec, long offset, int size) {
        RenderedSql rendered = SqlRenderer.renderUnion(applyLogicalDeleteFilter(spec), metadata);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(rendered.getParams());
        String paginatedSql = dialect.applyPagination(rendered.getSql(), offset, size, paginatedParams);
        RowMapper<R> mapper = buildBeanRowMapper(spec.getDtoClass());
        return jdbc.query(paginatedSql, paginatedParams, mapper);
    }

    /**
     * Executes a paginated SELECT and returns a Spring {@link Page}.
     *
     * <p>The total count follows {@link #count(SelectSpec)} semantics: JOIN queries without
     * GROUP BY count every matching joined row, while grouped queries count the resulting groups.
     */
    public <T, R> Page<R> selectPage(SelectSpec<T, R> spec, JPageable<T> pageable) {
        if (pageable.isUnpaged()) {
            throw new IllegalArgumentException("page() requires a paged JPageable");
        }
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));

        RenderedSql countSql = SqlRenderer.renderCount(effectiveSpec, metadata);
        Long total = jdbc.queryForObject(countSql.getSql(), countSql.getParams(), Long.class);
        long totalCount = total != null ? total : 0L;

        RenderedSql selectSql = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(selectSql.getParams());
        String paginatedSql =
                dialect.applyPagination(selectSql.getSql(), pageable.offset(), pageable.getSize(), paginatedParams);

        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        List<R> content = jdbc.query(paginatedSql, paginatedParams, mapper);

        return new PageImpl<>(content, pageable.toSpringPageable(), totalCount);
    }

    /**
     * Executes a paginated SELECT without a COUNT query. One extra row is fetched
     * to determine whether another slice exists; no total count is available.
     * Ordering and logical-delete filtering match {@link #select(SelectSpec, JPageable)}.
     *
     * @throws IllegalArgumentException if the pageable is unpaged or its size cannot be incremented
     */
    public <T, R> Slice<R> selectSlice(SelectSpec<T, R> spec, JPageable<T> pageable) {
        if (pageable.isUnpaged()) {
            throw new IllegalArgumentException("slice() requires a paged JPageable");
        }
        int size = pageable.getSize();
        if (size == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("slice() requires a page size smaller than Integer.MAX_VALUE");
        }
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec, metadata);
        Map<String, Object> params = new LinkedHashMap<>(rendered.getParams());
        String sql = dialect.applyPagination(rendered.getSql(), pageable.offset(), size + 1, params);
        List<R> rows = jdbc.query(sql, params, buildBeanRowMapper(effectiveSpec.getDtoClass()));
        boolean hasNext = rows.size() > size;
        List<R> content = hasNext ? new ArrayList<>(rows.subList(0, size)) : rows;
        return new SliceImpl<>(content, pageable.toSpringPageable(), hasNext);
    }

    /**
     * Executes an UPDATE described by the given {@link UpdateSpec}.
     *
     * <p>If the entity has a field annotated with
     * {@link org.springframework.data.annotation.LastModifiedDate} that is <em>not</em> already
     * present in {@code spec}'s assignments, it is automatically added with the current timestamp
     * from the configured {@link TimeProvider}.
     *
     * @param spec the update specification (SET assignments and optional WHERE conditions)
     * @return the number of rows affected
     */
    public <T> int executeUpdate(UpdateSpec<T> spec) {
        requireWhere(spec.getWhere());
        UpdateSpec<T> effectiveSpec = injectLastModifiedDate(spec);
        RenderedSql rendered = SqlRenderer.renderUpdate(effectiveSpec, metadata);
        return jdbc.update(rendered.getSql(), rendered.getParams());
    }

    /**
     * Executes a DELETE described by the given {@link DeleteSpec}.
     *
     * @param spec the delete specification (optional WHERE conditions)
     * @return the number of rows affected
     */
    public <T> int executeDelete(DeleteSpec<T> spec) {
        requireWhere(spec.getWhere());
        RenderedSql rendered = SqlRenderer.renderDelete(spec, metadata);
        return jdbc.update(rendered.getSql(), rendered.getParams());
    }

    /**
     * Executes a batch of UPDATE statements described by the given {@link UpdateSpec} list.
     *
     * <p>Consecutive specs that produce the same SQL are executed together in a single
     * {@link NamedParameterJdbcTemplate#batchUpdate} call. Groups execute in input order.
     *
     * <p>{@link org.springframework.data.annotation.LastModifiedDate} auto-fill is applied to
     * each spec before rendering, exactly as in {@link #executeUpdate(UpdateSpec)}.
     *
     * <p>If {@code specs} is empty an empty {@code int[]} is returned immediately.
     *
     * @param specs the list of update specifications to execute
     * @param <T>   entity type
     * @return concatenated array of per-row affected counts in encounter order
     */
    public <T> int[] executeBatchUpdate(List<UpdateSpec<T>> specs) {
        if (specs == null || specs.isEmpty()) {
            return new int[0];
        }
        List<BatchGroup> groups = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            UpdateSpec<T> spec = specs.get(i);
            requireWhere(spec.getWhere());
            UpdateSpec<T> effectiveSpec = injectLastModifiedDate(spec);
            RenderedSql rendered = SqlRenderer.renderUpdate(effectiveSpec, metadata);
            addBatchItem(groups, rendered, i);
        }
        return executeBatchGroups(groups, specs.size());
    }

    /**
     * Executes a batch of DELETE statements described by the given {@link DeleteSpec} list.
     *
     * <p>Like {@link #executeBatchUpdate}, only consecutive specs producing the same SQL
     * are batched together, preserving input order.
     *
     * <p>If {@code specs} is empty an empty {@code int[]} is returned immediately.
     *
     * @param specs the list of delete specifications to execute
     * @param <T>   entity type
     * @return concatenated array of per-row affected counts in encounter order
     */
    public <T> int[] executeBatchDelete(List<DeleteSpec<T>> specs) {
        if (specs == null || specs.isEmpty()) {
            return new int[0];
        }
        List<BatchGroup> groups = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            DeleteSpec<T> spec = specs.get(i);
            requireWhere(spec.getWhere());
            RenderedSql rendered = SqlRenderer.renderDelete(spec, metadata);
            addBatchItem(groups, rendered, i);
        }
        return executeBatchGroups(groups, specs.size());
    }

    /**
     * 执行逻辑删除（软删除）：将实体中 {@link io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete}
     * 标注字段的值更新为 {@code deletedValue}，而不是真正删除行。
     *
     * <p>实体类必须恰好有一个字段标注了 {@code @LogicalDelete}，否则抛出
     * {@link IllegalArgumentException}。
     *
     * @param spec 指定了 WHERE 条件的删除规格（WHERE 条件被复用为 UPDATE 的 WHERE 条件）
     * @param <T>  实体类型
     * @return 受影响的行数
     * @throws IllegalArgumentException 当实体类没有 {@code @LogicalDelete} 字段时
     */
    public <T> int executeLogicalDelete(DeleteSpec<T> spec) {
        requireWhere(spec.getWhere());
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        String ldPropName = meta.getLogicalDeletePropertyName();
        if (ldPropName == null) {
            throw new IllegalArgumentException(
                    "No @LogicalDelete field found on " + spec.getEntityClass().getName());
        }
        // Build UPDATE ... SET deleted_col = deletedValue WHERE ...
        List<Map.Entry<String, Object>> assignments = new ArrayList<>();
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(
                ldPropName,
                convertLogicalDeleteString(meta.getLogicalDeletedValue(), spec.getEntityClass(), ldPropName)));
        UpdateSpec<T> updateSpec = new UpdateSpec<>(spec.getEntityClass(), assignments, spec.getWhere());
        RenderedSql rendered = SqlRenderer.renderUpdate(updateSpec, metadata);
        return jdbc.update(rendered.getSql(), rendered.getParams());
    }

    // ------------------------------------------------------------------ //
    //  INSERT
    // ------------------------------------------------------------------ //

    /**
     * Executes an UPSERT (INSERT … ON CONFLICT / MERGE) for the given entity.
     *
     * <p>The SQL syntax is determined by the configured {@link Dialect}:
     * <ul>
     *   <li>MySQL: {@code INSERT … ON DUPLICATE KEY UPDATE}</li>
     *   <li>PostgreSQL / H2: {@code INSERT … ON CONFLICT … DO UPDATE SET}</li>
     *   <li>Oracle / Oracle 11g: {@code MERGE INTO … USING … FROM DUAL …}</li>
     *   <li>SQL Server: {@code MERGE INTO … USING (SELECT …) AS s(…) … ;}</li>
     *   <li>Default (Sql2008): throws {@link UnsupportedOperationException}</li>
     * </ul>
     * MySQL {@link UpsertBuilder#doNothing()} is unsupported because {@code INSERT IGNORE}
     * can suppress errors other than duplicate keys.
     *
     * <p>Fields annotated with {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} are injected with the
     * current timestamp before rendering (same as {@link #save(Object)}).
     * Because the database decides at runtime whether to INSERT or UPDATE, the
     * {@code @CreatedDate} column is only actually written when the row does not yet exist —
     * it is excluded from the UPDATE clause by default when {@link UpsertBuilder#doUpdateAll()}
     * or an explicit {@link UpsertBuilder#doUpdate(SFunction[])} that omits it is used.
     *
     * <p><b>Note on primary keys:</b> identity-generated primary keys (annotated with
     * {@code @GeneratedValue(strategy = IDENTITY)}) are excluded from INSERT and UPDATE by
     * default. When the conflict key is the identity PK itself, its entity value is included in
     * the insert source for conflict detection, but it is never included in the UPDATE SET.
     *
     * @param spec   the upsert specification (conflict columns, optional update-column subset)
     * @param entity the entity whose field values are used for both INSERT and conditional UPDATE
     * @throws UnsupportedOperationException if the configured dialect does not support UPSERT
     */
    public <T> void upsert(UpsertSpec<T> spec, T entity) {
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        spec = spec.resolve(meta);
        injectInsertTimestamps(entity, meta);
        // Include an identity PK only when it is needed as the conflict key. The dialect
        // resolver still excludes it from the UPDATE SET clause.
        boolean identityPkIsConflictColumn =
                meta.isIdGeneratedByIdentity() && spec.getConflictColumns().contains(meta.getIdColumnName());
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, !identityPkIsConflictColumn);
        RenderedSql rendered = SqlRenderer.renderUpsert(spec, meta, colValues, dialect);
        jdbc.update(rendered.getSql(), rendered.getParams());
    }

    /**
     * Inserts {@code entity} using all its columns (excluding IDENTITY-generated primary keys).
     *
     * <p>If the entity's primary key is annotated with
     * {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}, the generated key is read
     * back and set on the entity.
     *
     * <p>Fields annotated with {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} are automatically set to the
     * current timestamp from the configured {@link TimeProvider}.
     *
     * @param entity the entity to insert
     */
    public <T> void save(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMeta meta = metadata.apply(entityClass);
        injectInsertTimestamps(entity, meta);
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, true);
        doInsert(InsertSpec.of(entityClass), meta, colValues, entity);
    }

    /**
     * Inserts {@code entity} using the columns specified by {@code spec}.
     *
     * <p>If the spec has no explicit column names, all entity columns are inserted (excluding
     * IDENTITY-generated primary keys). If the spec specifies column names, exactly those
     * columns are used.
     *
     * <p>When the entity's primary key is {@code IDENTITY}-generated and the spec does not
     * restrict columns, the generated key is read back and set on the entity.
     *
     * <p>Fields annotated with {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} are automatically set to the
     * current timestamp from the configured {@link TimeProvider}.
     *
     * @param spec   the insert specification (entity class and optional column list)
     * @param entity the entity to insert
     */
    public <T> void save(InsertSpec<T> spec, T entity) {
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        injectInsertTimestamps(entity, meta);
        boolean excludeIdentityPk = spec.getColumnNames().isEmpty();
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, excludeIdentityPk);
        doInsert(spec, meta, colValues, entity);
    }

    /**
     * Inserts only the non-{@code null} columns of {@code entity} (excluding IDENTITY-generated
     * primary keys regardless of their value).
     *
     * <p>Fields annotated with {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} are automatically set to the
     * current timestamp from the configured {@link TimeProvider} before null-filtering.
     *
     * @param entity the entity to insert
     */
    public <T> void saveNonNull(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMeta meta = metadata.apply(entityClass);
        injectInsertTimestamps(entity, meta);
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, true);
        // Remove null-valued columns
        colValues.entrySet().removeIf(e -> e.getValue() == null);
        InsertSpec<T> spec = InsertSpec.of(entityClass, new ArrayList<>(colValues.keySet()));
        doInsert(spec, meta, colValues, entity);
    }

    /**
     * 批量插入 {@code spec.getRows()} 中的所有实体，使用单条 SQL + 多参数数组的方式
     * 调用 {@link NamedParameterJdbcTemplate#batchUpdate}。
     *
     * <p>若 {@code rows} 为空，立即返回空数组 {@code int[0]}。
     *
     * <p>字段自动填充：与单条 {@link #save(Object)} 相同，每行在插入前注入
     * {@link org.springframework.data.annotation.CreatedDate} /
     * {@link org.springframework.data.annotation.LastModifiedDate} 字段。
     *
     * @param spec 批量插入规格（实体类、行列表和可选的显式列名）
     * @param <T>  实体类型
     * @return 每行受影响的行数数组
     */
    public <T> int[] executeBatchInsert(BatchInsertSpec<T> spec) {
        return executeBatchInsertInternal(spec, 0);
    }

    /**
     * Inserts rows in consecutive JDBC batches of at most {@code batchSize} rows.
     *
     * <p>Counts are returned in input order. The specification already holds all input rows;
     * chunking bounds the temporary parameter arrays, not the input or result arrays.
     * If a later batch fails, earlier batches may already have executed. Use an external
     * transaction when the entire operation must be atomic.
     *
     * @param spec batch insert specification
     * @param batchSize maximum number of rows per JDBC batch; must be positive
     * @param <T> entity type
     * @return driver-reported update counts in input order
     */
    public <T> int[] executeBatchInsert(BatchInsertSpec<T> spec, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInsertInternal(spec, batchSize);
    }

    private <T> int[] executeBatchInsertInternal(BatchInsertSpec<T> spec, int batchSize) {
        List<T> rows = spec.getRows();
        if (rows.isEmpty()) {
            return new int[0];
        }
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        boolean excludeIdentityPk = spec.getColumnNames().isEmpty();

        // Auto-fill timestamps for each row
        for (T row : rows) {
            injectInsertTimestamps(row, meta);
        }

        // Build column values for the first row to determine the column set and SQL
        LinkedHashMap<String, Object> firstColValues = buildColumnValues(rows.get(0), meta, excludeIdentityPk);
        InsertSpec<T> insertSpec = spec.insertSpec(meta);
        RenderedSql rendered = SqlRenderer.renderInsert(insertSpec, meta, firstColValues);
        BatchInsertParameters parameters = rows.size() >= MIN_BATCH_ROWS_FOR_ACCESS_PLAN
                ? new BatchInsertParameters(meta, excludeIdentityPk)
                : null;
        if (batchSize == 0 || batchSize >= rows.size()) {
            return jdbc.batchUpdate(
                    rendered.getSql(),
                    buildBatchInsertParams(rows, 0, rows.size(), meta, excludeIdentityPk, parameters));
        }
        int[] counts = new int[rows.size()];
        for (int start = 0; start < rows.size(); ) {
            int end = start + Math.min(batchSize, rows.size() - start);
            int[] chunk = jdbc.batchUpdate(
                    rendered.getSql(), buildBatchInsertParams(rows, start, end, meta, excludeIdentityPk, parameters));
            if (chunk.length != end - start) {
                throw new IllegalStateException(
                        "Batch insert returned " + chunk.length + " counts for " + (end - start) + " rows");
            }
            System.arraycopy(chunk, 0, counts, start, chunk.length);
            start = end;
        }
        return counts;
    }

    private <T> MapSqlParameterSource[] buildBatchInsertParams(
            List<T> rows, int start, int end, EntityMeta meta, boolean skipIdentityPk, BatchInsertParameters plan) {
        if (plan != null) {
            return plan.build(rows, start, end);
        }
        MapSqlParameterSource[] params = new MapSqlParameterSource[end - start];
        for (int i = start; i < end; i++) {
            T row = rows.get(i);
            Class<?> rowClass = row.getClass();
            BeanMappingMeta beanMeta = cacheManager.getBeanMappingCache().get(rowClass, beanMappingMetaFactory::create);
            MapSqlParameterSource values = new MapSqlParameterSource();
            for (Map.Entry<String, String> entry : meta.getPropertyToColumn().entrySet()) {
                String property = entry.getKey();
                if (skipIdentityPk && meta.isIdGeneratedByIdentity() && property.equals(meta.getIdPropertyName())) {
                    continue;
                }
                values.addValue(entry.getValue(), beanMeta.getProperty(row, property));
            }
            params[i - start] = values;
        }
        return params;
    }

    private final class BatchInsertParameters {
        private final EntityMeta meta;
        private final boolean skipIdentityPk;
        private final Map<Class<?>, BatchAccessPlan> plansByType = new HashMap<>();

        private BatchInsertParameters(EntityMeta meta, boolean skipIdentityPk) {
            this.meta = meta;
            this.skipIdentityPk = skipIdentityPk;
        }

        private BatchProperty[] properties(BeanMappingMeta beanMeta) {
            List<BatchProperty> properties = new ArrayList<>();
            // Custom metadata may implement getProperty differently from its accessors.
            boolean builtInMeta = beanMeta instanceof JavaBeanMeta || beanMeta instanceof RecordMappingMeta;
            for (Map.Entry<String, String> entry : meta.getPropertyToColumn().entrySet()) {
                String name = entry.getKey();
                if (!skipIdentityPk || !meta.isIdGeneratedByIdentity() || !name.equals(meta.getIdPropertyName())) {
                    properties.add(new BatchProperty(
                            entry.getValue(), name, builtInMeta ? beanMeta.getPropertyAccessor(name) : null));
                }
            }
            return properties.toArray(BatchProperty[]::new);
        }

        private <T> MapSqlParameterSource[] build(List<T> rows, int start, int end) {
            MapSqlParameterSource[] params = new MapSqlParameterSource[end - start];
            for (int i = start; i < end; i++) {
                T row = rows.get(i);
                Class<?> rowClass = row.getClass();
                BeanMappingMeta beanMeta =
                        cacheManager.getBeanMappingCache().get(rowClass, beanMappingMetaFactory::create);
                BatchAccessPlan plan = plansByType.get(rowClass);
                if (plan == null || plan.meta() != beanMeta) {
                    plan = new BatchAccessPlan(beanMeta, properties(beanMeta));
                    plansByType.put(rowClass, plan);
                }
                MapSqlParameterSource values = new MapSqlParameterSource();
                for (BatchProperty property : plan.properties()) {
                    Object value = property.accessor() == null
                            ? beanMeta.getProperty(row, property.name())
                            : property.accessor().get(row);
                    values.addValue(property.column(), value);
                }
                params[i - start] = values;
            }
            return params;
        }
    }

    private record BatchAccessPlan(BeanMappingMeta meta, BatchProperty[] properties) {}

    private record BatchProperty(String column, String name, PropertyAccessor accessor) {}

    private <T> void doInsert(InsertSpec<T> spec, EntityMeta meta, LinkedHashMap<String, Object> colValues, T entity) {
        RenderedSql rendered = SqlRenderer.renderInsert(spec, meta, colValues);
        if (meta.isIdGeneratedByIdentity()) {
            MapSqlParameterSource paramSource = new MapSqlParameterSource(rendered.getParams());
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(rendered.getSql(), paramSource, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null && meta.getIdPropertyName() != null) {
                @SuppressWarnings("unchecked")
                Class<T> entityClass = (Class<T>) entity.getClass();
                BeanMappingMeta beanMeta = cacheManager
                        .getBeanMappingCache()
                        .get(entityClass, cls -> beanMappingMetaFactory.create(entityClass));
                PropertyAccessor idAccessor = beanMeta.getPropertyAccessor(meta.getIdPropertyName());
                Class<?> idType = idAccessor == null ? null : idAccessor.getType();
                Object convertedKey = idType == null
                        ? key
                        : converterRegistry.getConverter(key.getClass(), idType).convert(key);
                beanMeta.setProperty(entity, meta.getIdPropertyName(), convertedKey);
            }
        } else {
            jdbc.update(rendered.getSql(), rendered.getParams());
        }
    }

    // ------------------------------------------------------------------ //
    //  RETURNING: INSERT / UPDATE / DELETE returning rows
    // ------------------------------------------------------------------ //

    /**
     * Executes an INSERT and returns the inserted row's values for the specified columns.
     *
     * <p>Uses a {@code RETURNING col1, col2, …} clause (PostgreSQL / H2).
     * MySQL does not support this; use {@link #save(Object)} and read the generated key instead.
     *
     * <p>Fields annotated with {@link org.springframework.data.annotation.CreatedDate} and
     * {@link org.springframework.data.annotation.LastModifiedDate} are auto-filled before INSERT.
     *
     * @param entity         the entity to insert
     * @param returningProps property references whose DB column values should be returned
     * @param resultClass    JavaBean class to map each returned row into
     * @return the list of returned rows (typically one row for a single-row INSERT)
     */
    @SafeVarargs
    public final <T, R> List<R> saveReturning(T entity, Class<R> resultClass, SFunction<T, ?>... returningProps) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMeta meta = metadata.apply(entityClass);
        injectInsertTimestamps(entity, meta);
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, true);
        List<String> returningCols = resolveColumnNames(entityClass, meta, returningProps);
        RenderedSql rendered =
                SqlRenderer.renderInsertReturning(InsertSpec.of(entityClass), meta, colValues, returningCols);
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes an UPDATE described by the given {@link UpdateSpec} and returns the modified
     * rows' values for the specified columns.
     *
     * <p>Uses a {@code RETURNING col1, col2, …} clause (PostgreSQL / H2).
     * MySQL does not support RETURNING; use {@link #executeUpdate(UpdateSpec)} instead.
     *
     * <p>{@link org.springframework.data.annotation.LastModifiedDate} auto-fill is applied
     * exactly as in {@link #executeUpdate(UpdateSpec)}.
     *
     * @param spec           the update specification
     * @param returningProps property references whose DB column values should be returned
     * @param resultClass    JavaBean class to map each returned row into
     * @return the list of returned rows (one per affected row)
     */
    @SafeVarargs
    public final <T, R> List<R> executeUpdateReturning(
            UpdateSpec<T> spec, Class<R> resultClass, SFunction<T, ?>... returningProps) {
        requireWhere(spec.getWhere());
        UpdateSpec<T> effectiveSpec = injectLastModifiedDate(spec);
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        List<String> returningCols = resolveColumnNames(spec.getEntityClass(), meta, returningProps);
        RenderedSql rendered = SqlRenderer.renderUpdateReturning(effectiveSpec, returningCols, metadata);
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a DELETE described by the given {@link DeleteSpec} and returns the deleted
     * rows' values for the specified columns.
     *
     * <p>Uses a {@code RETURNING col1, col2, …} clause (PostgreSQL / H2).
     * MySQL does not support RETURNING; use {@link #executeDelete(DeleteSpec)} instead.
     *
     * @param spec           the delete specification
     * @param returningProps property references whose DB column values should be returned
     * @param resultClass    JavaBean class to map each returned row into
     * @return the list of returned rows (one per deleted row)
     */
    @SafeVarargs
    public final <T, R> List<R> executeDeleteReturning(
            DeleteSpec<T> spec, Class<R> resultClass, SFunction<T, ?>... returningProps) {
        requireWhere(spec.getWhere());
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        List<String> returningCols = resolveColumnNames(spec.getEntityClass(), meta, returningProps);
        RenderedSql rendered = SqlRenderer.renderDeleteReturning(spec, returningCols, metadata);
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Resolves an array of property method references to their DB column names, using entity
     * metadata for the mapping.
     */
    @SafeVarargs
    private static <T> List<String> resolveColumnNames(
            Class<T> entityClass, EntityMeta meta, SFunction<T, ?>... props) {
        List<String> cols = new ArrayList<>();
        for (SFunction<T, ?> prop : props) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            String colName = meta.getColumnName(ref.propertyName());
            cols.add(colName != null ? colName : ref.propertyName());
        }
        return cols;
    }

    /**
     * Builds an ordered map of {@code columnName → value} for the given entity by reading
     * property values via BeanMappingMeta.
     *
     * @param entity           the entity instance
     * @param meta             entity metadata
     * @param skipIdentityPk   when {@code true}, the IDENTITY-generated primary key column is excluded
     */
    private <T> LinkedHashMap<String, Object> buildColumnValues(T entity, EntityMeta meta, boolean skipIdentityPk) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        BeanMappingMeta beanMeta =
                cacheManager.getBeanMappingCache().get(entityClass, cls -> beanMappingMetaFactory.create(entityClass));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : meta.getPropertyToColumn().entrySet()) {
            String propName = entry.getKey();
            String colName = entry.getValue();
            if (skipIdentityPk && meta.isIdGeneratedByIdentity() && propName.equals(meta.getIdPropertyName())) {
                continue;
            }
            result.put(colName, beanMeta.getProperty(entity, propName));
        }
        return result;
    }

    // ------------------------------------------------------------------ //
    //  UPDATE BY ID / DELETE BY ID
    // ------------------------------------------------------------------ //

    /**
     * Updates all non-primary-key columns of {@code entity} using a WHERE clause on the
     * primary key.
     *
     * @param entity the entity to update (must have a non-null primary key)
     * @return the number of rows affected
     * @throws IllegalStateException if the entity class has no {@code @Id} field
     */
    public <T> int updateById(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMeta meta = metadata.apply(entityClass);
        BeanMappingMeta beanMeta =
                cacheManager.getBeanMappingCache().get(entityClass, cls -> beanMappingMetaFactory.create(entityClass));

        String idPropName = meta.getIdPropertyName();
        if (idPropName == null) {
            throw new IllegalStateException("No @Id field found on " + entityClass.getName());
        }

        // Build assignments: all non-PK columns
        List<Map.Entry<String, Object>> assignments = new ArrayList<>();
        for (String propName : meta.getPropertyToColumn().keySet()) {
            if (!propName.equals(idPropName)) {
                assignments.add(
                        new AbstractMap.SimpleImmutableEntry<>(propName, beanMeta.getProperty(entity, propName)));
            }
        }

        if (assignments.isEmpty()) {
            return 0;
        }

        // WHERE clause: id = entity.id
        Object idValue = beanMeta.getProperty(entity, idPropName);
        PropertyRef idRef = new PropertyRef(entityClass, idPropName);
        LeafPredicate where = LeafPredicate.of(idRef, "t", LeafPredicate.Op.EQ, idValue);

        UpdateSpec<T> spec = new UpdateSpec<>(entityClass, assignments, where);
        return executeUpdate(spec);
    }

    /**
     * Deletes the row identified by {@code id} from the table mapped to {@code entityClass}.
     *
     * @param entityClass the entity class whose table is targeted
     * @param id          the primary key value
     * @return the number of rows affected
     * @throws IllegalStateException if the entity class has no {@code @Id} field
     */
    public <T> int deleteById(Class<T> entityClass, Object id) {
        EntityMeta meta = metadata.apply(entityClass);

        String idPropName = meta.getIdPropertyName();
        if (idPropName == null) {
            throw new IllegalStateException("No @Id field found on " + entityClass.getName());
        }

        PropertyRef idRef = new PropertyRef(entityClass, idPropName);
        LeafPredicate where = LeafPredicate.of(idRef, "t", LeafPredicate.Op.EQ, id);
        DeleteSpec<T> spec = new DeleteSpec<>(entityClass, where);
        return executeDelete(spec);
    }

    // ------------------------------------------------------------------ //
    //  RowMapper: JavaBean setter mapping with field fallback
    // ------------------------------------------------------------------ //

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
    private static boolean isScalarType(Class<?> cls) {
        return cls.isPrimitive()
                || Number.class.isAssignableFrom(cls)
                || CharSequence.class.isAssignableFrom(cls)
                || Boolean.class == cls
                || Character.class == cls
                || java.util.Date.class.isAssignableFrom(cls)
                || java.time.temporal.Temporal.class.isAssignableFrom(cls);
    }

    private <R> RowMapper<R> buildBeanRowMapper(Class<R> beanClass) {
        if (isScalarType(beanClass)) {
            return new ScalarRowMapper<>(beanClass, converterRegistry, mappingMode, allowNullToPrimitiveDefault);
        }
        return createBeanRowMapper(beanClass);
    }

    private <R> RowMapper<R> createBeanRowMapper(Class<R> beanClass) {
        BeanMappingMeta meta =
                cacheManager.getBeanMappingCache().get(beanClass, cls -> beanMappingMetaFactory.create(beanClass));

        return new RowMapper<>() {
            private ResultSetMapper mapper;

            @Override
            public R mapRow(ResultSet rs, int rowNum) throws SQLException {
                if (mapper == null) {
                    mapper = mapperCache.getMapper(rs, meta);
                }
                @SuppressWarnings("unchecked")
                R instance = (R) mapper.mapRow(rs);
                return instance;
            }
        };
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
                    spec.isDistinct(),
                    spec.getSelectedExpressions(),
                    spec.getWhere(),
                    spec.getJoins(),
                    pageableSort,
                    spec.getDtoClass(),
                    spec.getGroupByExpressions(),
                    spec.getHaving(),
                    spec.getCteDefs(),
                    spec.getTableNameOverride(),
                    spec.getSubqueryFrom(),
                    spec.getLockMode());
        }
        return spec;
    }

    // ------------------------------------------------------------------ //
    //  Logical-delete auto-filter helper
    // ------------------------------------------------------------------ //

    /**
     * If logical-delete filtering is enabled and the root entity
     * has a {@code @LogicalDelete} field, returns a new {@link SelectSpec} with an additional
     * {@code AND alias.deleted_col = normalValue} predicate. Otherwise returns {@code spec} as-is.
     */
    private <T, R> SelectSpec<T, R> applyLogicalDeleteFilter(SelectSpec<T, R> spec) {
        if (!logicalDeleteAutoFilter.getAsBoolean()) {
            return spec;
        }
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        String ldPropName = meta.getLogicalDeletePropertyName();
        if (ldPropName == null) {
            return spec;
        }
        // Build a predicate: alias.deleted_col = normalValue
        Object normalVal =
                convertLogicalDeleteString(meta.getLogicalDeleteNormalValue(), spec.getEntityClass(), ldPropName);
        PropertyRef propRef = new PropertyRef(spec.getEntityClass(), ldPropName);
        LeafPredicate ldPredicate = LeafPredicate.of(propRef, spec.getAlias(), LeafPredicate.Op.EQ, normalVal);

        PredicateNode combinedWhere;
        if (spec.getWhere() == null) {
            combinedWhere = ldPredicate;
        } else {
            combinedWhere = new AndPredicate(List.of(spec.getWhere(), ldPredicate));
        }
        return new SelectSpec<>(
                spec.getEntityClass(),
                spec.getAlias(),
                spec.isDistinct(),
                spec.getSelectedExpressions(),
                combinedWhere,
                spec.getJoins(),
                spec.getSort(),
                spec.getDtoClass(),
                spec.getGroupByExpressions(),
                spec.getHaving(),
                spec.getCteDefs(),
                spec.getTableNameOverride(),
                spec.getSubqueryFrom(),
                spec.getLockMode());
    }

    /**
     * Applies the same root-entity logical-delete filter used by SELECT to every UNION branch.
     */
    private <R> UnionSpec<R> applyLogicalDeleteFilter(UnionSpec<R> spec) {
        if (!logicalDeleteAutoFilter.getAsBoolean()) {
            return spec;
        }
        List<UnionSpec.Branch<R>> branches = spec.getBranches();
        UnionSpec.Builder<R> builder =
                UnionSpec.of(applyLogicalDeleteFilter(branches.get(0).spec()));
        for (int i = 1; i < branches.size(); i++) {
            UnionSpec.Branch<R> branch = branches.get(i);
            SelectSpec<?, R> filtered = applyLogicalDeleteFilter(branch.spec());
            switch (branch.unionType()) {
                case UNION -> builder.union(filtered);
                case UNION_ALL -> builder.unionAll(filtered);
                case INTERSECT -> builder.intersect(filtered);
                case INTERSECT_ALL -> builder.intersectAll(filtered);
                case EXCEPT -> builder.except(filtered);
                case EXCEPT_ALL -> builder.exceptAll(filtered);
            }
        }
        return builder.orderBy(spec.getSort()).build();
    }

    private void addBatchItem(List<BatchGroup> groups, RenderedSql rendered, int index) {
        BatchGroup group = groups.isEmpty() ? null : groups.get(groups.size() - 1);
        if (group == null || !group.sql().equals(rendered.getSql())) {
            group = new BatchGroup(rendered.getSql(), new ArrayList<>());
            groups.add(group);
        }
        group.items().add(new BatchItem(index, rendered.getParams()));
    }

    private record BatchGroup(String sql, List<BatchItem> items) {}

    private int[] executeBatchGroups(List<BatchGroup> groups, int resultSize) {
        int[] results = new int[resultSize];
        for (BatchGroup group : groups) {
            List<BatchItem> items = group.items();
            MapSqlParameterSource[] batchParams = items.stream()
                    .map(item -> new MapSqlParameterSource(item.params()))
                    .toArray(MapSqlParameterSource[]::new);
            int[] batchResults = jdbc.batchUpdate(group.sql(), batchParams);
            if (batchResults.length != items.size()) {
                throw new IllegalStateException(
                        "Batch update returned " + batchResults.length + " counts for " + items.size() + " statements");
            }
            for (int i = 0; i < items.size(); i++) {
                results[items.get(i).index()] = batchResults[i];
            }
        }
        return results;
    }

    // ------------------------------------------------------------------ //
    //  Auto-fill helpers (@CreatedDate / @LastModifiedDate)
    // ------------------------------------------------------------------ //

    /**
     * Injects the current timestamp into all {@code @CreatedDate} and {@code @LastModifiedDate}
     * fields of {@code entity} before an INSERT.
     */
    private <T> void injectInsertTimestamps(T entity, EntityMeta meta) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        BeanMappingMeta beanMeta =
                cacheManager.getBeanMappingCache().get(entityClass, cls -> beanMappingMetaFactory.create(entityClass));

        LocalDateTime now = timeProvider.now();
        for (String propName : meta.getCreatedDatePropertyNames()) {
            beanMeta.setProperty(entity, propName, now);
        }
        for (String propName : meta.getLastModifiedDatePropertyNames()) {
            beanMeta.setProperty(entity, propName, now);
        }
    }

    /**
     * If {@code spec}'s entity has {@code @LastModifiedDate} fields that are not already in the
     * spec's assignments, adds them with the current timestamp and returns a new
     * {@link UpdateSpec}. Otherwise returns {@code spec} unchanged.
     */
    private <T> UpdateSpec<T> injectLastModifiedDate(UpdateSpec<T> spec) {
        EntityMeta meta = metadata.apply(spec.getEntityClass());
        List<String> lmdProps = meta.getLastModifiedDatePropertyNames();
        if (lmdProps.isEmpty()) {
            return spec;
        }
        Set<String> assignedProps =
                spec.getAssignments().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        List<Map.Entry<String, Object>> newAssignments = new ArrayList<>(spec.getAssignments());
        LocalDateTime now = timeProvider.now();
        for (String propName : lmdProps) {
            if (!assignedProps.contains(propName)) {
                newAssignments.add(new AbstractMap.SimpleImmutableEntry<>(propName, now));
            }
        }
        if (newAssignments.size() == spec.getAssignments().size()) {
            return spec; // nothing added
        }
        return new UpdateSpec<>(spec.getEntityClass(), newAssignments, spec.getWhere());
    }

    // ------------------------------------------------------------------ //
    //  Logical-delete value conversion helper
    // ------------------------------------------------------------------ //

    /**
     * Converts a logical-delete String value (e.g. {@code "1"}) to match the Java type of the
     * annotated field. Uses {@link DefaultConversionService} for the conversion.
     *
     * @return the converted value, or {@code null} when {@code stringValue} is {@code null}
     */
    private static Object convertLogicalDeleteString(String stringValue, Class<?> entityClass, String propName) {
        if (stringValue == null) {
            return null;
        }
        // Resolve the field's type
        Class<?> cls = entityClass;
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(propName);
                Class<?> fieldType = f.getType();
                if (fieldType == String.class) {
                    return stringValue;
                }
                ConversionService cs = DefaultConversionService.getSharedInstance();
                if (cs.canConvert(String.class, fieldType)) {
                    return cs.convert(stringValue, fieldType);
                }
                return stringValue;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return stringValue;
    }

    private record BatchItem(int index, Map<String, Object> params) {}
}
