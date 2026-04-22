package io.github.jsbxyyx.jdbcast.condition;

import java.util.List;

/** A disjunction of two or more conditions: {@code (c1 OR c2 OR ...)}. */
public record OrCondition(List<Condition> children) implements Condition {

    public OrCondition {
        if (children.size() < 2) throw new IllegalArgumentException("OR requires at least 2 children");
        children = List.copyOf(children);
    }
}
