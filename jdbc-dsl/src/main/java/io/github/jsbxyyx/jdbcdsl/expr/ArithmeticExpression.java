package io.github.jsbxyyx.jdbcdsl.expr;

import java.util.Objects;

/**
 * A binary arithmetic SQL expression used by UPDATE assignments and other expression contexts.
 */
public final class ArithmeticExpression implements SqlExpression<Number> {

    private final Operator operator;
    private final SqlExpression<?> left;
    private final SqlExpression<?> right;

    public ArithmeticExpression(Operator operator, SqlExpression<?> left, SqlExpression<?> right) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    public Operator getOperator() {
        return operator;
    }

    public SqlExpression<?> getLeft() {
        return left;
    }

    public SqlExpression<?> getRight() {
        return right;
    }

    public enum Operator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/");

        private final String sql;

        Operator(String sql) {
            this.sql = sql;
        }

        public String getSql() {
            return sql;
        }
    }
}
