package io.github.jsbxyyx.jdbcdsl;

/**
 * A single column order directive (property reference + direction).
 */
public final class JOrder {

    public enum Direction {
        ASC, DESC
    }

    private final PropertyRef propertyRef;
    private final Direction direction;

    JOrder(PropertyRef propertyRef, Direction direction) {
        this.propertyRef = propertyRef;
        this.direction = direction;
    }

    public PropertyRef getPropertyRef() {
        return propertyRef;
    }

    public Direction getDirection() {
        return direction;
    }
}
