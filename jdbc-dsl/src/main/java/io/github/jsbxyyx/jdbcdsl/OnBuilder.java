package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for JOIN ON conditions.
 * Supports equality comparisons between properties of the root entity and the joined entity.
 */
public final class OnBuilder {

    private final List<PredicateNode> conditions = new ArrayList<>();

    /**
     * Adds an equality ON condition between a property of entity {@code A} and entity {@code B}.
     *
     * @param leftProp   method reference to the left-hand property
     * @param leftAlias  table alias for the left-hand property
     * @param rightProp  method reference to the right-hand property
     * @param rightAlias table alias for the right-hand property
     */
    public <A, B, V> OnBuilder eq(SFunction<A, V> leftProp, String leftAlias,
                                  SFunction<B, V> rightProp, String rightAlias) {
        PropertyRef leftRef = PropertyRefResolver.resolve(leftProp);
        PropertyRef rightRef = PropertyRefResolver.resolve(rightProp);
        conditions.add(new OnEqPredicate(leftRef, leftAlias, rightRef, rightAlias));
        return this;
    }

    List<PredicateNode> getConditions() {
        return conditions;
    }

    /**
     * A special predicate node representing {@code alias1.col = alias2.col}.
     */
    public static final class OnEqPredicate implements PredicateNode {
        private final PropertyRef leftRef;
        private final String leftAlias;
        private final PropertyRef rightRef;
        private final String rightAlias;

        OnEqPredicate(PropertyRef leftRef, String leftAlias,
                      PropertyRef rightRef, String rightAlias) {
            this.leftRef = leftRef;
            this.leftAlias = leftAlias;
            this.rightRef = rightRef;
            this.rightAlias = rightAlias;
        }

        public PropertyRef getLeftRef() { return leftRef; }
        public String getLeftAlias() { return leftAlias; }
        public PropertyRef getRightRef() { return rightRef; }
        public String getRightAlias() { return rightAlias; }
    }
}
