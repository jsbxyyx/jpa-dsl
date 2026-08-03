package io.github.jsbxyyx.jdbcdsl.bean;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BeanMappingMeta implementation for Java Record types.
 *
 * <p>Records are immutable and must be constructed via their canonical constructor.
 * This implementation:
 * <ul>
 *   <li>Uses the canonical constructor to create instances</li>
 *   <li>Maps property names to constructor parameter positions</li>
 *   <li>Supports case-insensitive property name matching</li>
 *   <li>Throws exception on setProperty (records are immutable)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * record User(Long id, String name) {}
 *
 * RecordMappingMeta meta = new RecordMappingMeta(User.class);
 * // Properties must be set via constructor, not setProperty
 * }</pre>
 */
public final class RecordMappingMeta implements BeanMappingMeta {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Class<?> type;
    private final Constructor<?> canonicalConstructor;
    private final String[] parameterNames;
    private final Class<?>[] parameterTypes;
    private final Map<String, Integer> propertyIndexMap;
    private final Map<String, Integer> lowerCasePropertyIndexMap;
    private final Map<String, PropertyAccessor> propertyAccessors;

    /**
     * Creates a new RecordMappingMeta.
     *
     * @param type the record class type
     * @throws IllegalArgumentException if type is not a record
     */
    public RecordMappingMeta(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("Type must be a record: " + type);
        }

        this.type = type;

        // Get record components
        RecordComponent[] components = type.getRecordComponents();
        this.parameterNames = new String[components.length];
        this.parameterTypes = new Class<?>[components.length];

        Map<String, Integer> indexMap = new HashMap<>(components.length);
        Map<String, Integer> lowerIndexMap = new HashMap<>(components.length);
        Map<String, PropertyAccessor> accessorMap = new HashMap<>(components.length);

        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            Class<?> componentType = components[i].getType();

            parameterNames[i] = name;
            parameterTypes[i] = componentType;
            indexMap.put(name, i);
            lowerIndexMap.put(name.toLowerCase(Locale.ROOT), i);

            // Create accessor for getter method
            try {
                Method getterMethod = components[i].getAccessor();
                MethodHandle getterHandle = LOOKUP.unreflect(getterMethod);
                PropertyAccessor accessor = new MethodHandleAccessor(name, componentType, getterHandle, null);
                accessorMap.put(name, accessor);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create accessor for record component: " + name, e);
            }
        }

        this.propertyIndexMap = Map.copyOf(indexMap);
        this.lowerCasePropertyIndexMap = Map.copyOf(lowerIndexMap);
        this.propertyAccessors = Map.copyOf(accessorMap);

        // Get canonical constructor
        try {
            this.canonicalConstructor = type.getDeclaredConstructor(parameterTypes);
            this.canonicalConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find canonical constructor for record: " + type, e);
        }
    }

    @Override
    public Object newInstance() {
        // Records cannot be instantiated without parameters
        // This method creates an instance with null values
        Object[] args = new Object[parameterTypes.length];
        try {
            return canonicalConstructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of record " + type, e);
        }
    }

    /**
     * Creates a new record instance with the given property values.
     *
     * <p>This is the preferred way to create record instances, as it allows
     * setting all properties at construction time.
     *
     * @param propertyValues map of property name to value
     * @return a new record instance
     * @throws RuntimeException if instantiation fails
     */
    public Object newInstance(Map<String, Object> propertyValues) {
        Object[] args = new Object[parameterTypes.length];

        for (Map.Entry<String, Object> entry : propertyValues.entrySet()) {
            Integer index = getPropertyIndex(entry.getKey());
            if (index != null) {
                args[index] = entry.getValue();
            }
        }

        try {
            return canonicalConstructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create instance of record " + type + " with values: " + propertyValues, e);
        }
    }

    @Override
    public Object getProperty(Object target, String propertyName) {
        PropertyAccessor accessor = getPropertyAccessor(propertyName);
        if (accessor == null) {
            throw new RuntimeException("Property '" + propertyName + "' not found on record type " + type);
        }
        return accessor.get(target);
    }

    @Override
    public void setProperty(Object target, String propertyName, Object value) {
        throw new UnsupportedOperationException(
                "Cannot set property '" + propertyName + "' on record type " + type + ". Records are immutable.");
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return getPropertyIndex(propertyName) != null;
    }

    @Override
    public Class<?> getPropertyType(String propertyName) {
        Integer index = getPropertyIndex(propertyName);
        return index != null ? parameterTypes[index] : null;
    }

    /**
     * Gets the parameter index for a property name (case-insensitive).
     *
     * @param propertyName the property name
     * @return the parameter index, or null if not found
     */
    private Integer getPropertyIndex(String propertyName) {
        // Try exact match first
        Integer index = propertyIndexMap.get(propertyName);
        if (index != null) {
            return index;
        }
        // Fall back to case-insensitive match
        return lowerCasePropertyIndexMap.get(propertyName.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the parameter names of the canonical constructor.
     *
     * @return array of parameter names
     */
    public String[] getParameterNames() {
        return parameterNames.clone();
    }

    /**
     * Returns the parameter types of the canonical constructor.
     *
     * @return array of parameter types
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    @Override
    public PropertyAccessor getPropertyAccessor(String propertyName) {
        // Try exact match first
        PropertyAccessor accessor = propertyAccessors.get(propertyName);
        if (accessor != null) {
            return accessor;
        }
        // Fall back to case-insensitive match
        Integer index = lowerCasePropertyIndexMap.get(propertyName.toLowerCase(Locale.ROOT));
        if (index != null) {
            String exactName = parameterNames[index];
            return propertyAccessors.get(exactName);
        }
        return null;
    }
}
