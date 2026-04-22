package io.github.jsbxyyx.jdbcast.clause;

/** The type of set operation combining two SELECT statements. */
public enum SetOpType {
    UNION("UNION"),
    UNION_ALL("UNION ALL"),
    INTERSECT("INTERSECT"),
    INTERSECT_ALL("INTERSECT ALL"),
    EXCEPT("EXCEPT"),
    EXCEPT_ALL("EXCEPT ALL");

    private final String keyword;

    SetOpType(String keyword) { this.keyword = keyword; }

    public String keyword() { return keyword; }
}
