package io.github.jsbxyyx.jdbcdsl.bean;

import java.lang.invoke.MethodHandle;

/**
 * PropertyAccessor implementation using MethodHandle for efficient property access.
 *
 * <p>MethodHandle provides better performance than reflection (Method.invoke)
 * while maintaining flexibility for dynamic property access.
 *
 * <p>This implementation uses {@code invoke()} rather than {@code invokeExact()}
 * to allow automatic type conversions and boxing/unboxing.
 */
public final class MethodHandleAccessor implements PropertyAccessor {

    private final String name;
    private final Class<?> type;
    private final MethodHandle getter;
    private final MethodHandle setter;

    /**
     * Creates a new MethodHandleAccessor.
     *
     * @param name the property name
     * @param type the property type
     * @param getter the getter MethodHandle (may be null if write-only)
     * @param setter the setter MethodHandle (may be null if read-only)
     */
    public MethodHandleAccessor(String name, Class<?> type, MethodHandle getter, MethodHandle setter) {
        this.name = name;
        this.type = type;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public Object get(Object target) {
        if (getter == null) {
            throw new UnsupportedOperationException("Property '" + name + "' is write-only");
        }
        try {
            return getter.invoke(target);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get property '" + name + "' from " + target.getClass(), e);
        }
    }

    @Override
    public void set(Object target, Object value) {
        write(target, value);
    }

    @Override
    public void write(Object target, Object value) {
        if (setter == null) {
            throw new UnsupportedOperationException("Property '" + name + "' is read-only");
        }
        try {
            setter.invoke(target, value);
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to write property '" + name + "' on " + target.getClass() + " with value: " + value, e);
        }
    }
}
