package io.github.jsbxyyx.jdbcdsl.bean;

import java.beans.Introspector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating BeanMappingMeta instances.
 *
 * <p>This factory analyzes a class and creates optimized metadata for property access:
 * <ul>
 *   <li>Scans for JavaBean getter/setter methods</li>
 *   <li>Falls back to direct field access if no setter exists</li>
 *   <li>Uses MethodHandle for efficient property access</li>
 *   <li>Caches object creation via ObjectFactory</li>
 * </ul>
 */
public final class BeanMappingMetaFactory {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Creates BeanMappingMeta for the given type.
     *
     * @param type the class to analyze
     * @return the bean mapping metadata
     * @throws RuntimeException if meta creation fails
     */
    public BeanMappingMeta create(Class<?> type) {
        if (type.isRecord()) {
            throw new UnsupportedOperationException("Record types are not yet supported for SELECT queries. "
                    + "Records require a different construction strategy using canonical constructors.");
        }

        ObjectFactory<?> factory = createObjectFactory(type);
        Map<String, PropertyAccessor> properties = scanProperties(type);

        return new JavaBeanMeta(type, factory, properties);
    }

    /**
     * Creates an ObjectFactory for the given type.
     */
    private ObjectFactory<?> createObjectFactory(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            MethodHandle constructorHandle = LOOKUP.unreflectConstructor(constructor);

            return () -> {
                try {
                    return constructorHandle.invoke();
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to create instance of " + type, e);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("No accessible no-arg constructor found for " + type, e);
        }
    }

    /**
     * Scans the class for properties and creates PropertyAccessor instances.
     */
    private Map<String, PropertyAccessor> scanProperties(Class<?> type) {
        Map<String, PropertyAccessor> properties = new HashMap<>();

        // Scan for getter/setter methods
        Map<String, Method> getters = new HashMap<>();
        Map<String, Method> setters = new HashMap<>();

        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            String methodName = method.getName();
            String propertyName = null;

            // Check for getter
            if (methodName.startsWith("get") && methodName.length() > 3 && method.getParameterCount() == 0) {
                propertyName = decapitalize(methodName.substring(3));
                getters.put(propertyName, method);
            } else if (methodName.startsWith("is")
                    && methodName.length() > 2
                    && method.getParameterCount() == 0
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                propertyName = decapitalize(methodName.substring(2));
                getters.put(propertyName, method);
            }

            // Check for setter
            if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                propertyName = decapitalize(methodName.substring(3));
                setters.put(propertyName, method);
            }
        }

        // Create MethodHandleAccessor for properties with getter/setter
        for (String propertyName : setters.keySet()) {
            Method getter = getters.get(propertyName);
            Method setter = setters.get(propertyName);

            try {
                MethodHandle getterHandle = null;
                if (getter != null) {
                    getter.setAccessible(true);
                    getterHandle = LOOKUP.unreflect(getter).asType(MethodType.methodType(Object.class, Object.class));
                }

                setter.setAccessible(true);
                MethodHandle setterHandle =
                        LOOKUP.unreflect(setter).asType(MethodType.methodType(void.class, Object.class, Object.class));

                Class<?> propertyType = setter.getParameterTypes()[0];

                properties.put(
                        propertyName, new MethodHandleAccessor(propertyName, propertyType, getterHandle, setterHandle));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle for property: " + propertyName, e);
            }
        }

        // Fallback: scan fields for properties without setters
        scanFieldsForFallback(type, properties);

        return properties;
    }

    /**
     * Scans fields and creates FieldAccessor for properties without setters.
     */
    private void scanFieldsForFallback(Class<?> type, Map<String, PropertyAccessor> properties) {
        Class<?> currentClass = type;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                String fieldName = field.getName();
                if (!properties.containsKey(fieldName)) {
                    properties.put(fieldName, new FieldAccessor(field));
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    /**
     * Decapitalizes a string (first character to lowercase).
     */
    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Use Introspector.decapitalize for proper JavaBean naming rules
        return Introspector.decapitalize(name);
    }
}
