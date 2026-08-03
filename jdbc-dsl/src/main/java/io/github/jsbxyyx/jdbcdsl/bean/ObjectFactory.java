package io.github.jsbxyyx.jdbcdsl.bean;

/**
 * Factory for creating new instances of a type.
 *
 * <p>This functional interface allows caching of object creation logic,
 * avoiding repeated reflection calls to constructors.
 *
 * <p>Typical usage with method references:
 * <pre>{@code
 * ObjectFactory<User> factory = User::new;
 * User user = factory.create();
 * }</pre>
 *
 * @param <T> the type of objects created by this factory
 */
@FunctionalInterface
public interface ObjectFactory<T> {

    /**
     * Creates a new instance of type T.
     *
     * @return a new instance
     * @throws RuntimeException if instantiation fails
     */
    T create();
}
