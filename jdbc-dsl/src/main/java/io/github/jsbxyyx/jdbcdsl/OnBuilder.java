package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.predicate.PredicateNode;
import io.github.jsbxyyx.jdbcdsl.predicate.RawPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /**
     * Adds a raw SQL ON condition (escape hatch for complex expressions).
     *
     * <p>Useful when joining subqueries whose projected aliases don't map to entity
     * property names, or for multi-column / non-equality join conditions.
     *
     * <p>Example:
     * <pre>{@code
     * .leftJoinSubquery(subSpec, TOrder.class, "o",
     *     on -> on.raw("o.user_id = t.id AND o.status = 'ACTIVE'"))
     * }</pre>
     *
     * <p><strong>Warning:</strong> never embed user input directly in {@code sql}.
     */
    public OnBuilder raw(String sql) {
        conditions.add(new RawPredicate(sql, Map.of()));
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
