package io.github.jsbxyyx.jdbcast.meta;

import io.github.jsbxyyx.jdbcast.PropertyRef;
import io.github.jsbxyyx.jdbcast.PropertyRefResolver;
import io.github.jsbxyyx.jdbcast.SFunction;
import io.github.jsbxyyx.jdbcast.renderer.MetaResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link MetaResolver} for Spring Data JDBC / Spring Data Relational entities.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Table name: {@code @org.springframework.data.relational.core.mapping.Table(value)} →
 *       {@code @javax/jakarta.persistence.Table(name)} → snake_case of simple class name</li>
 *   <li>Column name: {@code @org.springframework.data.relational.core.mapping.Column(value)} →
 *       {@code @javax/jakarta.persistence.Column(name)} → snake_case of property name</li>
 * </ol>
 *
 * <p>The snake_case conversion matches Spring Data JDBC's {@code DefaultNamingStrategy}:
 * {@code "userId"} → {@code "user_id"}, {@code "orderNo"} → {@code "order_no"}.
 *
 * <pre>{@code
 * // Spring Boot auto-configuration example:
 * @Bean
 * public AnsiSqlRenderer sqlRenderer() {
 *     return new AnsiSqlRenderer(SpringDataRelationalMetaResolver.INSTANCE);
 * }
 * }</pre>
 */
public final class SpringDataRelationalMetaResolver implements MetaResolver {

    /** Shared singleton. */
    public static final SpringDataRelationalMetaResolver INSTANCE =
            new SpringDataRelationalMetaResolver();

    private final ConcurrentMap<Class<?>, String> tableCache  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String>   columnCache = new ConcurrentHashMap<>();

    private SpringDataRelationalMetaResolver() {}

    // ------------------------------------------------------------------ //
    //  MetaResolver
    // ------------------------------------------------------------------ //

    @Override
    public String tableName(Class<?> entityClass) {
        return tableCache.computeIfAbsent(entityClass, this::resolveTableName);
    }

    @Override
    public String columnName(SFunction<?, ?> getter) {
        PropertyRef ref = PropertyRefResolver.resolve(getter);
        return columnName(ref.ownerClass(), ref.propertyName());
    }

    @Override
    public String columnName(Class<?> entityClass, String propertyName) {
        String key = entityClass.getName() + "#" + propertyName;
        return columnCache.computeIfAbsent(key,
                k -> resolveColumnName(new PropertyRef(entityClass, propertyName)));
    }

    // ------------------------------------------------------------------ //
    //  Table name resolution
    // ------------------------------------------------------------------ //

    private String resolveTableName(Class<?> cls) {
        // 1. Spring Data Relational @Table(value / schema)
        String sdName = readAnnotationStringAttr(cls,
                "org.springframework.data.relational.core.mapping.Table", "value");
        if (sdName != null && !sdName.isEmpty()) return sdName;

        // 2. Jakarta / javax @Table(name)
        String jpaName = readAnnotationStringAttr(cls, "jakarta.persistence.Table", "name");
        if (jpaName != null && !jpaName.isEmpty()) return jpaName;

        String jpaName2 = readAnnotationStringAttr(cls, "javax.persistence.Table", "name");
        if (jpaName2 != null && !jpaName2.isEmpty()) return jpaName2;

        // 3. Fallback: snake_case of simple class name
        return toSnakeCase(cls.getSimpleName());
    }

    // ------------------------------------------------------------------ //
    //  Column name resolution
    // ------------------------------------------------------------------ //

    private String resolveColumnName(PropertyRef ref) {
        Field field = findField(ref.ownerClass(), ref.propertyName());
        if (field != null) {
            // 1. Spring Data Relational @Column(value)
            String sdCol = readAnnotationStringAttr(field,
                    "org.springframework.data.relational.core.mapping.Column", "value");
            if (sdCol != null && !sdCol.isEmpty()) return sdCol;

            // 2. Jakarta / javax @Column(name)
            String jpaCol = readAnnotationStringAttr(field, "jakarta.persistence.Column", "name");
            if (jpaCol != null && !jpaCol.isEmpty()) return jpaCol;

            String jpaCol2 = readAnnotationStringAttr(field, "javax.persistence.Column", "name");
            if (jpaCol2 != null && !jpaCol2.isEmpty()) return jpaCol2;
        }
        // 3. Fallback: snake_case of property name (Spring Data JDBC DefaultNamingStrategy)
        return toSnakeCase(ref.propertyName());
    }

    // ------------------------------------------------------------------ //
    //  Reflection helpers
    // ------------------------------------------------------------------ //

    private static String readAnnotationStringAttr(Class<?> cls, String annotationFqn, String method) {
        try {
            @SuppressWarnings("unchecked")
            Class<Annotation> annClass = (Class<Annotation>) Class.forName(annotationFqn);
            Annotation ann = cls.getAnnotation(annClass);
            if (ann == null) return null;
            Method m = annClass.getMethod(method);
            return (String) m.invoke(ann);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readAnnotationStringAttr(Field field, String annotationFqn, String method) {
        try {
            @SuppressWarnings("unchecked")
            Class<Annotation> annClass = (Class<Annotation>) Class.forName(annotationFqn);
            Annotation ann = field.getAnnotation(annClass);
            if (ann == null) return null;
            Method m = annClass.getMethod(method);
            return (String) m.invoke(ann);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String propertyName) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(propertyName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  snake_case conversion
    // ------------------------------------------------------------------ //

    /**
     * Converts a camelCase / PascalCase identifier to snake_case.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "TUser"} → {@code "t_user"}</li>
     *   <li>{@code "userId"} → {@code "user_id"}</li>
     *   <li>{@code "orderNo"} → {@code "order_no"}</li>
     *   <li>{@code "XMLParser"} → {@code "x_m_l_parser"} (matches Spring's behaviour)</li>
     * </ul>
     */
    public static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
