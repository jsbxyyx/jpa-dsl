package io.github.jsbxyyx.jdbcast.condition;

/** Comparison operator for a binary predicate. */
public enum Op {
    EQ("="), NE("<>"), LT("<"), LTE("<="), GT(">"), GTE(">=");

    private final String symbol;

    Op(String symbol) { this.symbol = symbol; }

    public String symbol() { return symbol; }
}
