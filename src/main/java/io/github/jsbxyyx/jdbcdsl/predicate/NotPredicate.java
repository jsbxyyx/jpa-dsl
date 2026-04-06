package io.github.jsbxyyx.jdbcdsl.predicate;

/**
 * A negation (NOT) of a child predicate.
 */
public final class NotPredicate implements PredicateNode {

    private final PredicateNode child;

    public NotPredicate(PredicateNode child) {
        this.child = child;
    }

    public PredicateNode getChild() {
        return child;
    }
}
