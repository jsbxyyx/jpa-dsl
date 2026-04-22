package io.github.jsbxyyx.jdbcast;

/**
 * Locking mode for SELECT … FOR UPDATE / FOR SHARE statements.
 */
public enum LockMode {
    /** {@code FOR UPDATE} */
    UPDATE,
    /** {@code FOR UPDATE NOWAIT} */
    UPDATE_NOWAIT,
    /** {@code FOR UPDATE SKIP LOCKED} */
    UPDATE_SKIP_LOCKED,
    /** {@code FOR SHARE} */
    SHARE
}
