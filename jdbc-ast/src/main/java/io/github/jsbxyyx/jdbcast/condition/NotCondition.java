package io.github.jsbxyyx.jdbcast.condition;

/** Negation of a condition: {@code NOT (inner)}. */
public record NotCondition(Condition inner) implements Condition {
}
