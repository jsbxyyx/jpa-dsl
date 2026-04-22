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
 * A {@link MetaResolver} that reads Jakarta Persistence {@code @Table} and {@code @Column}
 * annotations to resolve entity table names and column names.
 *
 * <p>Fallback rules:
 * <ul>
 *   <li>Table name → entity simple class name when {@code @Table} is absent or has no {@code name}.</li>
 *   <li>Column name → Java property name when {@code @Column} is absent or has no {@code name}.</li>
 * </ul>
 *
 * <p>Results are cached to avoid repeated reflection. Use the shared {@link #INSTANCE}.
 *
 * <pre>{@code
 * AnsiSqlRenderer renderer = new AnsiSqlRenderer(JpaMetaResolver.INSTANCE);
 * RenderedSql result = renderer.render(stmt);
 * }</pre>
 */
public final class JpaMetaResolver implements MetaResolver {

    /** Shared singleton. */
    public static final JpaMetaResolver INSTANCE = new JpaMetaResolver();

    private final ConcurrentMap<Class<?>, String> tableCache  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String>   columnCache = new ConcurrentHashMap<>();

    private JpaMetaResolver() {}

    // ------------------------------------------------------------------ //
    //  MetaResolver implementation
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
    //  Resolution helpers
    // ------------------------------------------------------------------ //

    private String resolveTableName(Class<?> cls) {
        for (String annotationName : new String[]{
                "jakarta.persistence.Table",
                "javax.persistence.Table"}) {
            try {
                @SuppressWarnings("unchecked")
                Class<Annotation> annClass = (Class<Annotation>) Class.forName(annotationName);
                Annotation ann = cls.getAnnotation(annClass);
                if (ann != null) {
                    Method nameMethod = annClass.getMethod("name");
                    String name = (String) nameMethod.invoke(ann);
                    if (name != null && !name.isEmpty()) return name;
                }
            } catch (Exception ignored) {
                // annotation not on classpath or not present — try next
            }
        }
        return cls.getSimpleName();
    }

    private String resolveColumnName(PropertyRef ref) {
        Field field = findField(ref.ownerClass(), ref.propertyName());
        if (field != null) {
            for (String annotationName : new String[]{
                    "jakarta.persistence.Column",
                    "javax.persistence.Column"}) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<Annotation> annClass = (Class<Annotation>) Class.forName(annotationName);
                    Annotation ann = field.getAnnotation(annClass);
                    if (ann != null) {
                        Method nameMethod = annClass.getMethod("name");
                        String name = (String) nameMethod.invoke(ann);
                        if (name != null && !name.isEmpty()) return name;
                    }
                } catch (Exception ignored) {}
            }
        }
        return ref.propertyName();
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
}
