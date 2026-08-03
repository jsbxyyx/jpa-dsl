package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BeanMappingMeta implementation for standard JavaBeans.
 *
 * <p>This implementation supports:
 * <ul>
 *   <li>JavaBean getter/setter methods</li>
 *   <li>Direct field access (fallback)</li>
 *   <li>Case-insensitive property name matching</li>
 * </ul>
 *
 * <p>Properties are accessed via MethodHandle for optimal performance.
 */
public final class JavaBeanMeta implements BeanMappingMeta {

    private final Class<?> type;
    private final ObjectFactory<?> objectFactory;
    private final Map<String, PropertyAccessor> properties;
    private final Map<String, PropertyAccessor> lowerCaseProperties;
    private final ConversionService conversionService;

    /**
     * Creates a new JavaBeanMeta.
     *
     * @param type the bean class type
     * @param objectFactory factory for creating new instances
     * @param properties map of property name to accessor
     */
    public JavaBeanMeta(Class<?> type, ObjectFactory<?> objectFactory, Map<String, PropertyAccessor> properties) {
        this.type = type;
        this.objectFactory = objectFactory;
        this.properties = Map.copyOf(properties);
        this.conversionService = DefaultConversionService.getSharedInstance();

        // Build lowercase map for case-insensitive lookup
        Map<String, PropertyAccessor> lowerMap = new HashMap<>(properties.size());
        for (Map.Entry<String, PropertyAccessor> entry : properties.entrySet()) {
            lowerMap.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        this.lowerCaseProperties = Map.copyOf(lowerMap);
    }

    @Override
    public Object newInstance() {
        try {
            return objectFactory.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + type, e);
        }
    }

    @Override
    public Object getProperty(Object target, String propertyName) {
        PropertyAccessor accessor = getAccessor(propertyName);
        if (accessor == null) {
            throw new RuntimeException("Property '" + propertyName + "' not found on type " + type);
        }
        return accessor.get(target);
    }

    @Override
    public void setProperty(Object target, String propertyName, Object value) {
        PropertyAccessor accessor = getAccessor(propertyName);
        if (accessor == null) {
            // Silently ignore unknown properties (consistent with BeanUtils behavior)
            return;
        }

        // Convert value to target type if needed
        Object convertedValue = convertValue(value, accessor.getType());
        accessor.set(target, convertedValue);
    }

    /**
     * Converts a value to the target type using Spring's ConversionService.
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType == null) {
            return value;
        }
        if (value == null) {
            return null; // null is valid for reference types
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (conversionService.canConvert(value.getClass(), targetType)) {
            return conversionService.convert(value, targetType);
        }
        return value;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return getAccessor(propertyName) != null;
    }

    /**
     * Gets the accessor for a property name (case-insensitive).
     */
    private PropertyAccessor getAccessor(String propertyName) {
        // Try exact match first
        PropertyAccessor accessor = properties.get(propertyName);
        if (accessor != null) {
            return accessor;
        }
        // Fall back to case-insensitive match
        return lowerCaseProperties.get(propertyName.toLowerCase(Locale.ROOT));
    }
}
