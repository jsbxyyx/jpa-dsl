package io.github.jsbxyyx.jdbcast;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A serializable function used as a method-reference handle for type-safe column references.
 * Always use method references (e.g., {@code User::getName}) — lambdas will fail at runtime.
 *
 * @param <T> entity type
 * @param <R> property type
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
