package io.github.jsbxyyx.jdbcdsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for {@link InsertSpec} (JDBC INSERT statements).
 *
 * <p>Usage (type-safe method references):
 * <pre>{@code
 * InsertSpec<TUser> spec = InsertBuilder.into(TUser.class)
 *     .columns(TUser::getUsername, TUser::getEmail, TUser::getStatus)
 *     .build();
 * executor.save(spec, entity);
 * }</pre>
 *
 * <p>Usage (raw column names):
 * <pre>{@code
 * InsertSpec<TUser> spec = InsertBuilder.into(TUser.class)
 *     .columns("username", "email", "status")
 *     .build();
 * executor.save(spec, entity);
 * }</pre>
 *
 * @param <T> the root entity type
 */
public final class InsertBuilder<T> {

    private final Class<T> entityClass;
    private final List<String> columnNames = new ArrayList<>();

    private InsertBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /** Starts building an INSERT for the given entity class. */
    public static <T> InsertBuilder<T> into(Class<T> entityClass) {
        return new InsertBuilder<>(entityClass);
    }

    /**
     * Adds columns to insert using type-safe method references.
     * The property names are resolved from the lambda and mapped to database column names
     * via {@link EntityMetaReader}.
     *
     * <p>Example: {@code .columns(TUser::getUsername, TUser::getEmail)}
     *
     * @param props getter method references that identify the columns to insert
     */
    @SafeVarargs
    public final InsertBuilder<T> columns(SFunction<T, ?>... props) {
        EntityMeta meta = EntityMetaReader.read(entityClass);
        for (SFunction<T, ?> prop : props) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            String colName = meta.getColumnName(ref.propertyName());
            columnNames.add(colName != null ? colName : ref.propertyName());
        }
        return this;
    }

    /**
     * Adds column names to insert. Can be called multiple times to accumulate columns.
     * If no columns are added, all entity columns are used (minus IDENTITY-generated pk).
     *
     * @param columns the column names (from {@code @Column(name=...)})
     */
    public InsertBuilder<T> columns(String... columns) {
        columnNames.addAll(Arrays.asList(columns));
        return this;
    }

    /** Builds the {@link InsertSpec}. */
    public InsertSpec<T> build() {
        return InsertSpec.of(entityClass, columnNames);
    }
}
