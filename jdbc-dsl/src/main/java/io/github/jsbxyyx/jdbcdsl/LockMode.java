package io.github.jsbxyyx.jdbcdsl;

/**
 * Row-level locking mode appended at the end of a SELECT statement.
 *
 * <p>Not all databases support every mode. Common compatibility matrix:
 * <ul>
 *   <li>{@link #UPDATE} — supported by MySQL, PostgreSQL and Oracle</li>
 *   <li>{@link #UPDATE_NOWAIT} — supported by PostgreSQL, Oracle; MySQL 8+</li>
 *   <li>{@link #UPDATE_SKIP_LOCKED} — supported by PostgreSQL, Oracle; MySQL 8+</li>
 *   <li>{@link #SHARE} — supported by PostgreSQL and MySQL 8 ({@code FOR SHARE})</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
 *     .select(TUser::getId, TUser::getUsername)
 *     .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
 *     .forUpdate(LockMode.UPDATE_SKIP_LOCKED)
 *     .mapTo(UserDto.class);
 * }</pre>
 */
public enum LockMode {

    /** {@code FOR UPDATE} — acquires an exclusive row lock. */
    UPDATE(" FOR UPDATE"),

    /** {@code FOR UPDATE NOWAIT} — acquires an exclusive row lock; fails immediately if locked. */
    UPDATE_NOWAIT(" FOR UPDATE NOWAIT"),

    /** {@code FOR UPDATE SKIP LOCKED} — acquires an exclusive lock; skips already-locked rows. */
    UPDATE_SKIP_LOCKED(" FOR UPDATE SKIP LOCKED"),

    /** {@code FOR SHARE} — acquires a shared row lock (PostgreSQL and MySQL 8). */
    SHARE(" FOR SHARE");

    private final String sql;

    LockMode(String sql) {
        this.sql = sql;
    }

    /** Returns the SQL suffix used by dialects with a trailing lock clause. */
    public String sql() {
        return sql;
    }
}
