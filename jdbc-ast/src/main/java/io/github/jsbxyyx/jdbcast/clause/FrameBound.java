package io.github.jsbxyyx.jdbcast.clause;

/** A single boundary of a window frame (ROWS/RANGE/GROUPS BETWEEN start AND end). */
public final class FrameBound {

    public enum Kind {
        UNBOUNDED_PRECEDING, PRECEDING, CURRENT_ROW, FOLLOWING, UNBOUNDED_FOLLOWING
    }

    private final Kind kind;
    private final long offset;

    private FrameBound(Kind kind, long offset) {
        this.kind = kind;
        this.offset = offset;
    }

    public static FrameBound unboundedPreceding() { return new FrameBound(Kind.UNBOUNDED_PRECEDING, 0); }
    public static FrameBound preceding(long n)    { return new FrameBound(Kind.PRECEDING, n); }
    public static FrameBound currentRow()         { return new FrameBound(Kind.CURRENT_ROW, 0); }
    public static FrameBound following(long n)    { return new FrameBound(Kind.FOLLOWING, n); }
    public static FrameBound unboundedFollowing() { return new FrameBound(Kind.UNBOUNDED_FOLLOWING, 0); }

    public Kind getKind()   { return kind; }
    public long getOffset() { return offset; }

    public String toSql() {
        return switch (kind) {
            case UNBOUNDED_PRECEDING  -> "UNBOUNDED PRECEDING";
            case PRECEDING            -> offset + " PRECEDING";
            case CURRENT_ROW          -> "CURRENT ROW";
            case FOLLOWING            -> offset + " FOLLOWING";
            case UNBOUNDED_FOLLOWING  -> "UNBOUNDED FOLLOWING";
        };
    }
}
