package io.github.jsbxyyx.jdbcdsl;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A serializable function interface that enables lambda serialization for method-reference resolution.
 * <p>
 * Always use method references (e.g., {@code User::getName}) — lambdas will fail at runtime.
 *
 * @param <T> the input type (entity class)
 * @param <R> the return type (property type)
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
