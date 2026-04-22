package io.github.jsbxyyx.jdbcdsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link UpsertSpec}.
 *
 * <p>Usage example — insert-or-update by unique username:
 * <pre>{@code
 * UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
 *     .onConflict(TUser::getUsername)  // the column(s) that trigger a conflict
 *     .doUpdateAll()                    // update all non-conflict columns on match
 *     .build();
 *
 * executor.upsert(spec, entity);
 * }</pre>
 *
 * <p>To update only specific columns on conflict:
 * <pre>{@code
 * UpsertSpec<TUser> spec = UpsertBuilder.into(TUser.class)
 *     .onConflict(TUser::getUsername)
 *     .doUpdate(TUser::getEmail, TUser::getStatus)
 *     .build();
 * }</pre>
 *
 * <p><b>Note on MySQL:</b> MySQL infers the conflict from the table's unique indexes; calling
 * {@link #onConflict} is optional for MySQL but required for PostgreSQL / H2 / Oracle / SQL Server.
 *
 * @param <T> the entity type
 */
public final class UpsertBuilder<T> {

    private final Class<T> entityClass;
    private final List<String> conflictColumns = new ArrayList<>();
    private final List<String> updateColumns = new ArrayList<>();
    private boolean doNothing = false;

    private UpsertBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public static <T> UpsertBuilder<T> into(Class<T> entityClass) {
        return new UpsertBuilder<>(entityClass);
    }

    /**
     * Specifies the conflict-target columns (the unique key that detects a duplicate row).
     *
     * <p>These become the {@code ON CONFLICT (col1, col2)} clause for PostgreSQL/H2 and
     * the {@code ON (t.col1 = s.col1 AND …)} clause for Oracle/SQL Server MERGE.
     * For MySQL they are stored but not emitted in SQL (MySQL infers from unique indexes).
     *
     * @param props method references identifying the conflict-target properties
     */
    @SafeVarargs
    public final UpsertBuilder<T> onConflict(SFunction<T, ?>... props) {
        EntityMeta meta = EntityMetaReader.read(entityClass);
        for (SFunction<T, ?> prop : props) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            String col = meta.getColumnName(ref.propertyName());
            conflictColumns.add(col != null ? col : ref.propertyName());
        }
        return this;
    }

    /**
     * Specifies exactly which columns to include in the UPDATE part when a conflict occurs.
     * Overrides the default "all non-conflict columns" behaviour.
     *
     * @param props method references for properties to update on conflict
     */
    @SafeVarargs
    public final UpsertBuilder<T> doUpdate(SFunction<T, ?>... props) {
        EntityMeta meta = EntityMetaReader.read(entityClass);
        for (SFunction<T, ?> prop : props) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            String col = meta.getColumnName(ref.propertyName());
            updateColumns.add(col != null ? col : ref.propertyName());
        }
        return this;
    }

    /**
     * Marks the intent to update all non-conflict columns when a conflict occurs.
     *
     * <p>This is the default behaviour when {@link #doUpdate} is never called.
     * Calling this method is a no-op that improves readability.
     */
    public UpsertBuilder<T> doUpdateAll() {
        // updateColumns stays empty — resolved as "all non-conflict columns" at render time.
        return this;
    }

    /**
     * Marks the conflict action as "do nothing" — duplicate rows are silently skipped
     * without updating the existing row.
     *
     * <p>SQL syntax per dialect:
     * <ul>
     *   <li>PostgreSQL: {@code INSERT … ON CONFLICT [(target)] DO NOTHING}</li>
     *   <li>MySQL: {@code INSERT IGNORE INTO …}</li>
     *   <li>H2 / Oracle / SQL Server: MERGE with only a WHEN NOT MATCHED INSERT branch</li>
     * </ul>
     *
     * <p>When this is set, any columns added via {@link #doUpdate} are ignored at render time.
     */
    public UpsertBuilder<T> doNothing() {
        this.doNothing = true;
        return this;
    }

    public UpsertSpec<T> build() {
        return new UpsertSpec<>(entityClass, conflictColumns, updateColumns, doNothing);
    }
}
