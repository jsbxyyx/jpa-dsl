package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.DialectDetector;
import io.github.jsbxyyx.jdbcdsl.predicate.AndPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.LeafPredicate;
import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.JdbcUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>DTOs and entities must provide a public (or package-private) no-arg constructor.
 *
 * <p>The default {@link Dialect} is {@link Sql2008Dialect}.
 * Pass a custom dialect (e.g., {@link io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect}) as needed.
 */
public final class JdbcDslExecutor {

    private final NamedParameterJdbcTemplate jdbc;
    private final Dialect dialect;
    private TimeProvider timeProvider = LocalDateTime::now;

    /**
     * Creates a {@link JdbcDslExecutor} that auto-detects the {@link Dialect} from the
     * DataSource associated with {@code jdbc}.
     *
     * <p>The detection probes {@link java.sql.DatabaseMetaData#getDatabaseProductName()} and
     * selects the matching dialect (MySQL/MariaDB, PostgreSQL, SQL Server, H2). Falls back to
     * {@link io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect} when the DataSource is
     * unavailable or the product name is not recognised.
     *
     * @param jdbc the template whose DataSource is used for dialect detection
     */
    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, DialectDetector.detect(jdbc.getJdbcTemplate().getDataSource()));
    }

    public JdbcDslExecutor(NamedParameterJdbcTemplate jdbc, Dialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
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
        if (timeProvider == null) throw new IllegalArgumentException("timeProvider must not be null");
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

    /**
     * Executes an arbitrary SQL query and returns the first row mapped to {@code resultClass},
     * or {@code null} if no rows match.
     */
    public <R> R queryOne(String sql, Map<String, Object> params, Class<R> resultClass) {
        RowMapper<R> mapper = buildBeanRowMapper(resultClass);
        List<R> results = jdbc.query(sql, params, mapper);
        return results.isEmpty() ? null : results.get(0);
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
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec);
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a COUNT query for the given spec and returns the total number of matching rows.
     *
     * <p>When the spec contains JOINs (no GROUP BY), uses {@code COUNT(DISTINCT pk)} to avoid
     * counting duplicate rows produced by one-to-many joins.
     * When the spec contains GROUP BY, wraps the query as a derived table and counts groups.
     */
    public <T, R> long count(SelectSpec<T, R> spec) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(spec);
        RenderedSql countSql = SqlRenderer.renderCount(effectiveSpec);
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
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(rendered.getParams());
        String paginatedSql = dialect.applyPagination(
                rendered.getSql(), pageable.offset(), pageable.getSize(), paginatedParams);
        RowMapper<R> mapper = buildBeanRowMapper(effectiveSpec.getDtoClass());
        return jdbc.query(paginatedSql, paginatedParams, mapper);
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
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec);
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
        RenderedSql rendered = SqlRenderer.renderSelect(effectiveSpec);
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
        RenderedSql rendered = SqlRenderer.renderUnion(spec);
        RowMapper<R> mapper = buildBeanRowMapper(spec.getDtoClass());
        return jdbc.query(rendered.getSql(), rendered.getParams(), mapper);
    }

    /**
     * Executes a paginated {@link UnionSpec} and returns a page of results.
     */
    public <R> List<R> union(UnionSpec<R> spec, long offset, int size) {
        RenderedSql rendered = SqlRenderer.renderUnion(spec);
        Map<String, Object> paginatedParams = new LinkedHashMap<>(rendered.getParams());
        String paginatedSql = dialect.applyPagination(rendered.getSql(), offset, size, paginatedParams);
        RowMapper<R> mapper = buildBeanRowMapper(spec.getDtoClass());
        return jdbc.query(paginatedSql, paginatedParams, mapper);
    }

    /**
     * Executes a paginated SELECT and returns a Spring {@link Page}.
     *
     * <p>Note: when the spec contains JOINs, the COUNT query uses {@code COUNT(*)} which may
     * count duplicate rows. See the README for details.
     */
    public <T, R> Page<R> selectPage(SelectSpec<T, R> spec, JPageable<T> pageable) {
        SelectSpec<T, R> effectiveSpec = applyLogicalDeleteFilter(mergeSort(spec, pageable));

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
        UpdateSpec<T> effectiveSpec = injectLastModifiedDate(spec);
        RenderedSql rendered = SqlRenderer.renderUpdate(effectiveSpec);
        return jdbc.update(rendered.getSql(), rendered.getParams());
    }

    /**
     * Executes a DELETE described by the given {@link DeleteSpec}.
     *
     * @param spec the delete specification (optional WHERE conditions)
     * @return the number of rows affected
     */
    public <T> int executeDelete(DeleteSpec<T> spec) {
        RenderedSql rendered = SqlRenderer.renderDelete(spec);
        return jdbc.update(rendered.getSql(), rendered.getParams());
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
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        String ldPropName = meta.getLogicalDeletePropertyName();
        if (ldPropName == null) {
            throw new IllegalArgumentException(
                    "No @LogicalDelete field found on " + spec.getEntityClass().getName());
        }
        // Build UPDATE ... SET deleted_col = deletedValue WHERE ...
        List<Map.Entry<String, Object>> assignments = new ArrayList<>();
        assignments.add(new AbstractMap.SimpleImmutableEntry<>(ldPropName,
                convertLogicalDeleteString(meta.getLogicalDeletedValue(), spec.getEntityClass(), ldPropName)));
        UpdateSpec<T> updateSpec = new UpdateSpec<>(spec.getEntityClass(), assignments, spec.getWhere());
        RenderedSql rendered = SqlRenderer.renderUpdate(updateSpec);
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
     * {@code @GeneratedValue(strategy = IDENTITY)}) are excluded from the INSERT and UPDATE
     * clauses. The database assigns the PK on INSERT; the PK is never included in the UPDATE SET
     * (updating a primary key is almost always wrong). When the conflict key is the PK itself,
     * set the id field on the entity — the conflict is detected via the unique index; the value
     * is still supplied through the conflict column's parameter.
     *
     * @param spec   the upsert specification (conflict columns, optional update-column subset)
     * @param entity the entity whose field values are used for both INSERT and conditional UPDATE
     * @throws UnsupportedOperationException if the configured dialect does not support UPSERT
     */
    public <T> void upsert(UpsertSpec<T> spec, T entity) {
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        injectInsertTimestamps(entity, meta);
        // Skip the identity PK — the database assigns it on INSERT, and PKs must not appear
        // in the UPDATE SET clause (databases forbid updating a primary key via ON CONFLICT).
        LinkedHashMap<String, Object> colValues = buildColumnValues(entity, meta, true);
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
        EntityMeta meta = EntityMetaReader.read(entityClass);
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
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
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
        EntityMeta meta = EntityMetaReader.read(entityClass);
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
        List<T> rows = spec.getRows();
        if (rows.isEmpty()) {
            return new int[0];
        }
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        boolean excludeIdentityPk = spec.getColumnNames().isEmpty();

        // Auto-fill timestamps for each row
        for (T row : rows) {
            injectInsertTimestamps(row, meta);
        }

        // Build column values for the first row to determine the column set and SQL
        LinkedHashMap<String, Object> firstColValues = buildColumnValues(rows.get(0), meta, excludeIdentityPk);
        InsertSpec<T> insertSpec = spec.getColumnNames().isEmpty()
                ? InsertSpec.of(spec.getEntityClass())
                : InsertSpec.of(spec.getEntityClass(), spec.getColumnNames());
        RenderedSql rendered = SqlRenderer.renderInsert(insertSpec, meta, firstColValues);

        // Build SqlParameterSource array (one entry per row)
        List<String> cols = new ArrayList<>(firstColValues.keySet());
        MapSqlParameterSource[] batchParams = rows.stream()
                .map(row -> {
                    LinkedHashMap<String, Object> cv = buildColumnValues(row, meta, excludeIdentityPk);
                    Map<String, Object> p = new LinkedHashMap<>();
                    for (String col : cols) {
                        p.put(col, cv.get(col));
                    }
                    return new MapSqlParameterSource(p);
                })
                .toArray(MapSqlParameterSource[]::new);

        return jdbc.batchUpdate(rendered.getSql(), batchParams);
    }

    private <T> void doInsert(InsertSpec<T> spec, EntityMeta meta,
                               LinkedHashMap<String, Object> colValues, T entity) {
        RenderedSql rendered = SqlRenderer.renderInsert(spec, meta, colValues);
        if (meta.isIdGeneratedByIdentity()) {
            MapSqlParameterSource paramSource = new MapSqlParameterSource(rendered.getParams());
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(rendered.getSql(), paramSource, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null && meta.getIdPropertyName() != null) {
                setPropertyValue(entity, meta.getIdPropertyName(), key);
            }
        } else {
            jdbc.update(rendered.getSql(), rendered.getParams());
        }
    }

    /**
     * Builds an ordered map of {@code columnName → value} for the given entity by reading
     * property values via getter methods (falling back to field access).
     *
     * @param entity           the entity instance
     * @param meta             entity metadata
     * @param skipIdentityPk   when {@code true}, the IDENTITY-generated primary key column is excluded
     */
    private static <T> LinkedHashMap<String, Object> buildColumnValues(T entity, EntityMeta meta,
                                                                        boolean skipIdentityPk) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : meta.getPropertyToColumn().entrySet()) {
            String propName = entry.getKey();
            String colName = entry.getValue();
            if (skipIdentityPk && meta.isIdGeneratedByIdentity()
                    && propName.equals(meta.getIdPropertyName())) {
                continue;
            }
            result.put(colName, getPropertyValue(entity, propName));
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
        EntityMeta meta = EntityMetaReader.read(entityClass);

        String idPropName = meta.getIdPropertyName();
        if (idPropName == null) {
            throw new IllegalStateException("No @Id field found on " + entityClass.getName());
        }

        // Build assignments: all non-PK columns
        List<Map.Entry<String, Object>> assignments = new ArrayList<>();
        for (String propName : meta.getPropertyToColumn().keySet()) {
            if (!propName.equals(idPropName)) {
                assignments.add(new AbstractMap.SimpleImmutableEntry<>(
                        propName, getPropertyValue(entity, propName)));
            }
        }

        if (assignments.isEmpty()) {
            return 0;
        }

        // WHERE clause: id = entity.id
        Object idValue = getPropertyValue(entity, idPropName);
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
        EntityMeta meta = EntityMetaReader.read(entityClass);

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
    private static boolean isScalarType(Class<?> cls) {
        return cls.isPrimitive()
                || Number.class.isAssignableFrom(cls)
                || CharSequence.class.isAssignableFrom(cls)
                || Boolean.class == cls
                || Character.class == cls
                || java.util.Date.class.isAssignableFrom(cls)
                || java.time.temporal.Temporal.class.isAssignableFrom(cls);
    }

    private static <R> RowMapper<R> buildBeanRowMapper(Class<R> beanClass) {
        if (isScalarType(beanClass)) {
            return new SingleColumnRowMapper<>(beanClass);
        }
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
                    if (f != null) {
                        Object converted = convertValue(value, f.getType(), conversionService);
                        // Skip null injection into primitive fields to avoid NullPointerException
                        if (converted == null && f.getType().isPrimitive()) continue;
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
        if (targetType == null) return value;
        if (value == null) return null; // null is valid for reference types; callers guard primitives
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

    /**
     * Reads a property value from {@code entity} using a getter method (or direct field access as
     * fallback).
     */
    private static Object getPropertyValue(Object entity, String propName) {
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(entity.getClass())) {
            if (pd.getName().equals(propName)) {
                Method readMethod = pd.getReadMethod();
                if (readMethod != null) {
                    try {
                        return readMethod.invoke(entity);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(
                                "Cannot read property '" + propName + "' on "
                                + entity.getClass().getName(), e);
                    }
                }
            }
        }
        // Fallback: walk the class hierarchy looking for a field with that name
        Class<?> cls = entity.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(propName);
                f.setAccessible(true);
                return f.get(entity);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Cannot access field '" + propName + "' on "
                        + entity.getClass().getName(), e);
            }
        }
        return null;
    }

    /**
     * Sets a property value on {@code entity} using a setter method (or direct field access as
     * fallback).
     */
    private static void setPropertyValue(Object entity, String propName, Object value) {
        ConversionService cs = DefaultConversionService.getSharedInstance();
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(entity.getClass())) {
            if (pd.getName().equals(propName)) {
                Method writeMethod = pd.getWriteMethod();
                if (writeMethod != null) {
                    Class<?> paramType = writeMethod.getParameterTypes()[0];
                    Object converted = convertValue(value, paramType, cs);
                    try {
                        writeMethod.invoke(entity, converted);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(
                                "Cannot set property '" + propName + "' on "
                                + entity.getClass().getName(), e);
                    }
                    return;
                }
            }
        }
        // Fallback: walk the class hierarchy
        Class<?> cls = entity.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(propName);
                f.setAccessible(true);
                Object converted = convertValue(value, f.getType(), cs);
                f.set(entity, converted);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Cannot set field '" + propName + "' on "
                        + entity.getClass().getName(), e);
            }
        }
    }

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
                    spec.getTableNameOverride());
        }
        return spec;
    }

    // ------------------------------------------------------------------ //
    //  Logical-delete auto-filter helper
    // ------------------------------------------------------------------ //

    /**
     * If {@link JdbcDslConfig#isLogicalDeleteAutoFilter()} is {@code true} and the root entity
     * has a {@code @LogicalDelete} field, returns a new {@link SelectSpec} with an additional
     * {@code AND alias.deleted_col = normalValue} predicate. Otherwise returns {@code spec} as-is.
     */
    private static <T, R> SelectSpec<T, R> applyLogicalDeleteFilter(SelectSpec<T, R> spec) {
        if (!JdbcDslConfig.isLogicalDeleteAutoFilter()) {
            return spec;
        }
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        String ldPropName = meta.getLogicalDeletePropertyName();
        if (ldPropName == null) {
            return spec;
        }
        // Build a predicate: alias.deleted_col = normalValue
        Object normalVal = convertLogicalDeleteString(meta.getLogicalDeleteNormalValue(),
                spec.getEntityClass(), ldPropName);
        PropertyRef propRef = new PropertyRef(spec.getEntityClass(), ldPropName);
        LeafPredicate ldPredicate = LeafPredicate.of(propRef, spec.getAlias(),
                LeafPredicate.Op.EQ, normalVal);

        PredicateNode combinedWhere;
        if (spec.getWhere() == null) {
            combinedWhere = ldPredicate;
        } else {
            combinedWhere = new AndPredicate(List.of(spec.getWhere(), ldPredicate));
        }
        return new SelectSpec<>(spec.getEntityClass(), spec.getAlias(),
                spec.isDistinct(), spec.getSelectedExpressions(), combinedWhere,
                spec.getJoins(), spec.getSort(), spec.getDtoClass(),
                spec.getGroupByExpressions(), spec.getHaving(),
                spec.getCteDefs(), spec.getTableNameOverride());
    }

    // ------------------------------------------------------------------ //
    //  Auto-fill helpers (@CreatedDate / @LastModifiedDate)
    // ------------------------------------------------------------------ //

    /**
     * Injects the current timestamp into all {@code @CreatedDate} and {@code @LastModifiedDate}
     * fields of {@code entity} before an INSERT.
     */
    private <T> void injectInsertTimestamps(T entity, EntityMeta meta) {
        LocalDateTime now = timeProvider.now();
        for (String propName : meta.getCreatedDatePropertyNames()) {
            setPropertyValue(entity, propName, now);
        }
        for (String propName : meta.getLastModifiedDatePropertyNames()) {
            setPropertyValue(entity, propName, now);
        }
    }

    /**
     * If {@code spec}'s entity has {@code @LastModifiedDate} fields that are not already in the
     * spec's assignments, adds them with the current timestamp and returns a new
     * {@link UpdateSpec}. Otherwise returns {@code spec} unchanged.
     */
    private <T> UpdateSpec<T> injectLastModifiedDate(UpdateSpec<T> spec) {
        EntityMeta meta = EntityMetaReader.read(spec.getEntityClass());
        List<String> lmdProps = meta.getLastModifiedDatePropertyNames();
        if (lmdProps.isEmpty()) {
            return spec;
        }
        Set<String> assignedProps = spec.getAssignments().stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
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
    private static Object convertLogicalDeleteString(String stringValue, Class<?> entityClass,
                                                      String propName) {
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
}
