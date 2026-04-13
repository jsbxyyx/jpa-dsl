package io.github.jsbxyyx.jdbcdsl.predicate;

import java.util.List;

/**
 * A conjunction (AND) of child predicates.
 */
public final class AndPredicate implements PredicateNode {

    private final List<PredicateNode> children;

    public AndPredicate(List<PredicateNode> children) {
        this.children = List.copyOf(children);
    }

    public List<PredicateNode> getChildren() {
        return children;
    }
}
