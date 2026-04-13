package io.github.jsbxyyx.jdbcdsl;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reads {@link jakarta.persistence} annotations ({@code @Table}/{@code @Column}/{@code @Id})
 * from an entity class to produce an {@link EntityMeta}.
 *
 * <p>Strategy (field annotations take precedence over getter annotations):
 * <ol>
 *   <li>Scan all declared fields (including superclass fields) for {@code @Column} and {@code @Id}.</li>
 *   <li>For fields without {@code @Column}, fall back to scanning getter methods.</li>
 *   <li>Column name defaults to the property name when {@code @Column(name)} is blank.</li>
 * </ol>
 *
 * <p>Results are cached per class.
 */
public final class EntityMetaReader {

    private static final ConcurrentMap<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    private EntityMetaReader() {
    }

    /**
     * Returns (and caches) the {@link EntityMeta} for the given entity class.
     *
     * @param entityClass the JPA entity class
     * @return metadata extracted from JPA annotations
     */
    public static EntityMeta read(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, EntityMetaReader::doRead);
    }

    private static EntityMeta doRead(Class<?> entityClass) {
        // --- Table name ---
        String tableName;
        Table tableAnn = entityClass.getAnnotation(Table.class);
        if (tableAnn != null && !tableAnn.name().isBlank()) {
            tableName = tableAnn.name();
        } else {
            tableName = entityClass.getSimpleName();
        }

        Map<String, String> propertyToColumn = new LinkedHashMap<>();
        String idPropertyName = null;
        String idColumnName = null;
        boolean idGeneratedByIdentity = false;

        // --- Scan fields (walking up the class hierarchy) ---
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String propName = field.getName();
                if (propertyToColumn.containsKey(propName)) {
                    continue; // subclass field shadows superclass field
                }

                boolean isId = field.isAnnotationPresent(Id.class);
                Column colAnn = field.getAnnotation(Column.class);

                if (colAnn != null || isId) {
                    String colName = resolveColumnName(colAnn, propName);
                    propertyToColumn.put(propName, colName);
                    if (isId) {
                        idPropertyName = propName;
                        idColumnName = colName;
                        GeneratedValue gv = field.getAnnotation(GeneratedValue.class);
                        idGeneratedByIdentity = gv != null && gv.strategy() == GenerationType.IDENTITY;
                    }
                }
            }
            current = current.getSuperclass();
        }

        // --- Scan getter methods for properties not yet registered via field annotations ---
        for (Method method : entityClass.getMethods()) {
            String propName = getterToPropertyName(method.getName());
            if (propName == null) {
                continue;
            }
            if (propertyToColumn.containsKey(propName)) {
                continue; // already registered via field
            }

            boolean isId = method.isAnnotationPresent(Id.class);
            Column colAnn = method.getAnnotation(Column.class);

            if (colAnn != null || isId) {
                String colName = resolveColumnName(colAnn, propName);
                propertyToColumn.put(propName, colName);
                if (isId && idPropertyName == null) {
                    idPropertyName = propName;
                    idColumnName = colName;
                    GeneratedValue gv = method.getAnnotation(GeneratedValue.class);
                    idGeneratedByIdentity = gv != null && gv.strategy() == GenerationType.IDENTITY;
                }
            }
        }

        // Register all remaining fields (without @Column) so that every property has a mapping.
        // Default column name = property name.
        current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String propName = field.getName();
                if (!propertyToColumn.containsKey(propName)) {
                    propertyToColumn.put(propName, propName);
                }
            }
            current = current.getSuperclass();
        }

        return new EntityMeta(tableName, propertyToColumn, idPropertyName, idColumnName, idGeneratedByIdentity);
    }

    private static String resolveColumnName(Column colAnn, String defaultName) {
        if (colAnn != null && !colAnn.name().isBlank()) {
            return colAnn.name();
        }
        return defaultName;
    }

    private static String getterToPropertyName(String methodName) {
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
        return null;
    }
}
