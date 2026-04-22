package io.github.jsbxyyx.jdbcast.condition;

import java.util.List;

/** A conjunction of two or more conditions: {@code (c1 AND c2 AND ...)}. */
public record AndCondition(List<Condition> children) implements Condition {

    public AndCondition {
        if (children.size() < 2) throw new IllegalArgumentException("AND requires at least 2 children");
        children = List.copyOf(children);
    }
}
