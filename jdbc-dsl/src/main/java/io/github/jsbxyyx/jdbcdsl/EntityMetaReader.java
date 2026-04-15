package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li>When no explicit name is provided by an annotation, the active {@link NamingStrategy}
 *       (from {@link JdbcDslConfig#getNamingStrategy()}) is applied to derive the column/table name.</li>
 * </ol>
 *
 * <p>Results are cached per class. The cache is automatically cleared when the naming strategy
 * is changed via {@link JdbcDslConfig#setNamingStrategy(NamingStrategy)}.
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

    /**
     * Clears the metadata cache. Called automatically when the naming strategy changes;
     * can also be used in tests to reset state between test cases.
     */
    static void clearCache() {
        CACHE.clear();
    }

    private static EntityMeta doRead(Class<?> entityClass) {
        NamingStrategy naming = JdbcDslConfig.getNamingStrategy();

        // --- Table name ---
        String tableName;
        Table tableAnn = entityClass.getAnnotation(Table.class);
        if (tableAnn != null && !tableAnn.name().isBlank()) {
            tableName = tableAnn.name();
        } else {
            tableName = naming.classToTable(entityClass.getSimpleName());
        }

        Map<String, String> propertyToColumn = new LinkedHashMap<>();
        String idPropertyName = null;
        String idColumnName = null;
        boolean idGeneratedByIdentity = false;

        // Logical-delete metadata
        String logicalDeletePropertyName = null;
        String logicalDeleteColumnName = null;
        String logicalDeletedValue = null;
        String logicalDeleteNormalValue = null;

        // Auto-fill metadata
        List<String> createdDateProps = new ArrayList<>();
        List<String> lastModifiedDateProps = new ArrayList<>();

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
                    String colName = resolveColumnName(colAnn, propName, naming);
                    propertyToColumn.put(propName, colName);
                    if (isId) {
                        idPropertyName = propName;
                        idColumnName = colName;
                        GeneratedValue gv = field.getAnnotation(GeneratedValue.class);
                        idGeneratedByIdentity = gv != null && gv.strategy() == GenerationType.IDENTITY;
                    }
                }

                // @LogicalDelete
                LogicalDelete ld = field.getAnnotation(LogicalDelete.class);
                if (ld != null && logicalDeletePropertyName == null) {
                    String ldColName = resolveColumnName(colAnn, propName, naming);
                    logicalDeletePropertyName = propName;
                    logicalDeleteColumnName = ldColName;
                    logicalDeletedValue = ld.deletedValue();
                    logicalDeleteNormalValue = ld.normalValue();
                }

                // @CreatedDate (Spring Data)
                if (field.isAnnotationPresent(CreatedDate.class)) {
                    createdDateProps.add(propName);
                }

                // @LastModifiedDate (Spring Data)
                if (field.isAnnotationPresent(LastModifiedDate.class)) {
                    lastModifiedDateProps.add(propName);
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
                String colName = resolveColumnName(colAnn, propName, naming);
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
        // Apply naming strategy to derive the default column name.
        current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String propName = field.getName();
                if (!propertyToColumn.containsKey(propName)) {
                    propertyToColumn.put(propName, naming.propertyToColumn(propName));
                }
            }
            current = current.getSuperclass();
        }

        return new EntityMeta(tableName, propertyToColumn, idPropertyName, idColumnName,
                idGeneratedByIdentity,
                logicalDeletePropertyName, logicalDeleteColumnName,
                logicalDeletedValue, logicalDeleteNormalValue,
                createdDateProps, lastModifiedDateProps);
    }

    private static String resolveColumnName(Column colAnn, String propName, NamingStrategy naming) {
        if (colAnn != null && !colAnn.name().isBlank()) {
            return colAnn.name(); // explicit annotation always wins
        }
        return naming.propertyToColumn(propName);
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
