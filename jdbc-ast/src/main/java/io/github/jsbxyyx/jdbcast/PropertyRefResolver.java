package io.github.jsbxyyx.jdbcast;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves an {@link SFunction} method reference to a {@link PropertyRef}.
 * Results are cached per lambda class to avoid repeated reflection overhead.
 */
public final class PropertyRefResolver {

    private static final ConcurrentMap<Class<?>, PropertyRef> CACHE = new ConcurrentHashMap<>();

    private PropertyRefResolver() {
    }

    public static <T, R> PropertyRef resolve(SFunction<T, R> fn) {
        return CACHE.computeIfAbsent(fn.getClass(), k -> doResolve(fn));
    }

    private static <T, R> PropertyRef doResolve(SFunction<T, R> fn) {
        SerializedLambda sl = getSerializedLambda(fn);
        String implMethodName = sl.getImplMethodName();
        if (implMethodName.startsWith("lambda$")) {
            throw new IllegalArgumentException(
                    "SFunction must be a method reference (e.g. User::getName), not a lambda body. "
                    + "Got: " + implMethodName);
        }
        String propertyName = methodNameToPropertyName(implMethodName);
        String implClass = sl.getImplClass().replace('/', '.');
        Class<?> ownerClass;
        try {
            ownerClass = Class.forName(implClass, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            try {
                ownerClass = Class.forName(implClass);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Cannot load class: " + implClass, ex);
            }
        }
        return new PropertyRef(ownerClass, propertyName);
    }

    private static SerializedLambda getSerializedLambda(Serializable fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            return (SerializedLambda) writeReplace.invoke(fn);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot serialize lambda — use a direct method reference", e);
        }
    }

    private static String methodNameToPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            char first = methodName.charAt(3);
            if (Character.isUpperCase(first)) {
                return Character.toLowerCase(first) + methodName.substring(4);
            }
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            char first = methodName.charAt(2);
            if (Character.isUpperCase(first)) {
                return Character.toLowerCase(first) + methodName.substring(3);
            }
        }
        return methodName;
    }
}
