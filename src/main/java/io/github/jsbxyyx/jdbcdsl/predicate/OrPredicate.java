package io.github.jsbxyyx.jdbcdsl.predicate;

import java.util.List;

/**
 * A disjunction (OR) of child predicates.
 */
public final class OrPredicate implements PredicateNode {

    private final List<PredicateNode> children;

    public OrPredicate(List<PredicateNode> children) {
        this.children = List.copyOf(children);
    }

    public List<PredicateNode> getChildren() {
        return children;
    }
}
