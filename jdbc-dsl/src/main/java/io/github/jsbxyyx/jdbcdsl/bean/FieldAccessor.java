package io.github.jsbxyyx.jdbcdsl.bean;

import java.lang.reflect.Field;

/**
 * PropertyAccessor implementation using direct field access.
 *
 * <p>This is a fallback accessor used when no getter/setter methods are available.
 * It directly accesses the field using reflection.
 *
 * <p>Field access is slower than MethodHandle but provides compatibility with
 * fields that don't follow JavaBean conventions.
 */
public final class FieldAccessor implements PropertyAccessor {

    private final Field field;

    /**
     * Creates a new FieldAccessor.
     *
     * @param field the field to access (must be made accessible)
     */
    public FieldAccessor(Field field) {
        this.field = field;
        this.field.setAccessible(true);
    }

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }

    @Override
    public Object get(Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field '" + field.getName() + "' from " + target.getClass(), e);
        }
    }

    @Override
    public void set(Object target, Object value) {
        write(target, value);
    }

    @Override
    public void write(Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to write field '" + field.getName() + "' on " + target.getClass() + " with value: " + value,
                    e);
        }
    }
}
