package io.github.jsbxyyx.jdbcast.expr;

import java.util.Objects;

/** A binary arithmetic expression such as {@code (t.count + :p1)}. */
public record ArithmeticExpr<V>(String operator, Expr<?> left, Expr<?> right) implements Expr<V> {
    public ArithmeticExpr {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (!operator.equals("+") && !operator.equals("-") && !operator.equals("*") && !operator.equals("/")) {
            throw new IllegalArgumentException("Unsupported arithmetic operator: " + operator);
        }
    }
}
