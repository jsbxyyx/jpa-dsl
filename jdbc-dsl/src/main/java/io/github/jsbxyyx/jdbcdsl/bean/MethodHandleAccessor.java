package io.github.jsbxyyx.jdbcdsl.bean;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;

/**
 * PropertyAccessor implementation using MethodHandle for efficient property access.
 *
 * <p>MethodHandle provides better performance than reflection (Method.invoke)
 * while maintaining flexibility for dynamic property access.
 *
 * <p>Handles are adapted once to a stable {@code (Object)Object} getter and
 * {@code (Object,Object)void} setter signature, allowing exact invocation in the hot path.
 */
public final class MethodHandleAccessor implements PropertyAccessor {

    private final String name;
    private final Class<?> type;
    private final MethodHandle getter;
    private final MethodHandle setter;
    private final BiConsumer<Object, Object> setterInvoker;

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
        this.getter = getter == null ? null : getter.asType(MethodType.methodType(Object.class, Object.class));
        this.setter =
                setter == null ? null : setter.asType(MethodType.methodType(void.class, Object.class, Object.class));
        this.setterInvoker = setter == null ? null : createSetterInvoker(setter);
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
            return (Object) getter.invokeExact(target);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get property '" + name + "' from " + target.getClass(), e);
        }
    }

    boolean usesLambdaSetter() {
        return setterInvoker != null;
    }

    BiConsumer<Object, Object> setterInvoker() {
        return setterInvoker;
    }

    MethodHandle setterHandle() {
        return setter;
    }

    @Override
    public void set(Object target, Object value) {
        write(target, value);
    }

    private static BiConsumer<Object, Object> createSetterInvoker(MethodHandle setter) {
        try {
            CallSite callSite = LambdaMetafactory.metafactory(
                    MethodHandles.lookup(),
                    "accept",
                    MethodType.methodType(BiConsumer.class),
                    MethodType.methodType(void.class, Object.class, Object.class),
                    setter,
                    setter.type());
            return (BiConsumer<Object, Object>) callSite.getTarget().invokeExact();
        } catch (Throwable e) {
            if (e instanceof Error error) {
                throw error;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return null;
        }
    }

    @Override
    public void write(Object target, Object value) {
        if (setter == null) {
            throw new UnsupportedOperationException("Property '" + name + "' is read-only");
        }
        if (setterInvoker != null) {
            setterInvoker.accept(target, value);
            return;
        }
        try {
            setter.invokeExact(target, value);
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to write property '" + name + "' on " + target.getClass() + " with value: " + value, e);
        }
    }
}
