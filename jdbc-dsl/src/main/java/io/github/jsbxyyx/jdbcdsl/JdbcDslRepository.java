package io.github.jsbxyyx.jdbcdsl;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/**
 * Generic base repository for a single jdbc-dsl entity type.
 *
 * <p>The repository binds an entity class to {@link JdbcDslExecutor}, centralising the
 * common CRUD and query operations that would otherwise be repeated in every generated
 * repository. Subclasses only need to provide the concrete entity and identifier types:
 *
 * <pre>{@code
 * @Repository
 * public class UserRepository extends JdbcDslRepository<User, Long> {
 *     public UserRepository(JdbcDslExecutor executor) {
 *         super(User.class, executor);
 *     }
 * }
 * }</pre>
 *
 * <p>The builder factory methods preserve the concrete entity type {@code T}, so method
 * references such as {@code User::getStatus} remain compile-time type safe.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public abstract class JdbcDslRepository<T, ID> {

    private final Class<T> entityClass;
    private final JdbcDslExecutor jdbcDslExecutor;

    protected JdbcDslRepository(Class<T> entityClass, JdbcDslExecutor jdbcDslExecutor) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.jdbcDslExecutor = Objects.requireNonNull(jdbcDslExecutor, "jdbcDslExecutor");
    }

    /** Returns the entity class bound to this repository. */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /** Returns the underlying executor for advanced operations not exposed by this base class. */
    protected JdbcDslExecutor getJdbcDslExecutor() {
        return jdbcDslExecutor;
    }

    /** Creates a type-safe SELECT builder for the bound entity. */
    public SelectBuilder<T> selectBuilder() {
        return SelectBuilder.from(entityClass);
    }

    /** Creates a type-safe INSERT builder for the bound entity. */
    public InsertBuilder<T> insertBuilder() {
        return InsertBuilder.into(entityClass);
    }

    /** Creates a type-safe UPDATE builder for the bound entity. */
    public UpdateBuilder<T> updateBuilder() {
        return UpdateBuilder.from(entityClass);
    }

    /** Creates a type-safe DELETE builder for the bound entity. */
    public DeleteBuilder<T> deleteBuilder() {
        return DeleteBuilder.from(entityClass);
    }

    /** Creates a type-safe UPSERT builder for the bound entity. */
    public UpsertBuilder<T> upsertBuilder() {
        return UpsertBuilder.into(entityClass);
    }

    /** Inserts an entity using all mapped columns except an IDENTITY primary key. */
    public void save(T entity) {
        jdbcDslExecutor.save(entity);
    }

    /** Inserts an entity using the columns selected by the supplied specification. */
    public void save(InsertSpec<T> spec, T entity) {
        jdbcDslExecutor.save(spec, entity);
    }

    /** Inserts only the non-null properties of an entity. */
    public void saveNonNull(T entity) {
        jdbcDslExecutor.saveNonNull(entity);
    }

    /** Executes an UPSERT specification for an entity. */
    public void upsert(UpsertSpec<T> spec, T entity) {
        jdbcDslExecutor.upsert(spec, entity);
    }

    /** Updates an entity by its mapped primary key. */
    public int updateById(T entity) {
        return jdbcDslExecutor.updateById(entity);
    }

    /** Executes an UPDATE specification. */
    public int update(UpdateSpec<T> spec) {
        return jdbcDslExecutor.executeUpdate(spec);
    }

    /** Deletes an entity by its primary key. */
    public int deleteById(ID id) {
        return jdbcDslExecutor.deleteById(entityClass, id);
    }

    /** Executes a DELETE specification. */
    public int delete(DeleteSpec<T> spec) {
        return jdbcDslExecutor.executeDelete(spec);
    }

    /** Executes a SELECT specification and returns all matching rows. */
    public <R> List<R> list(SelectSpec<T, R> spec) {
        return jdbcDslExecutor.select(spec);
    }

    /** Executes a paginated SELECT without a count query. */
    public <R> List<R> list(SelectSpec<T, R> spec, JPageable<T> pageable) {
        return jdbcDslExecutor.select(spec, pageable);
    }

    /** Returns the first matching row, or {@code null} when no row matches. */
    public <R> R findOne(SelectSpec<T, R> spec) {
        return jdbcDslExecutor.findOne(spec);
    }

    /** Returns the first matching row using the pageable sort, or {@code null}. */
    public <R> R findOne(SelectSpec<T, R> spec, JPageable<T> pageable) {
        return jdbcDslExecutor.findOne(spec, pageable);
    }

    /** Executes a paginated SELECT including a count query. */
    public <R> Page<R> page(SelectSpec<T, R> spec, JPageable<T> pageable) {
        return jdbcDslExecutor.selectPage(spec, pageable);
    }
}
