package io.github.jsbxyyx.jdbcast.clause;

/** The type of SQL JOIN. */
public enum JoinType {
    INNER("INNER"),
    LEFT("LEFT"),
    RIGHT("RIGHT"),
    FULL("FULL OUTER"),
    CROSS("CROSS");

    private final String keyword;

    JoinType(String keyword) { this.keyword = keyword; }

    public String keyword() { return keyword; }
}
