package io.github.jsbxyyx.jdbcast.clause;

/** The frame unit keyword for a window frame clause. */
public enum FrameType {
    /** {@code ROWS BETWEEN ...} — physical row counting */
    ROWS,
    /** {@code RANGE BETWEEN ...} — logical peer-based boundaries */
    RANGE,
    /** {@code GROUPS BETWEEN ...} — peer-group counting (PostgreSQL 11+, H2 2.x, SQLite 3.28+) */
    GROUPS
}
