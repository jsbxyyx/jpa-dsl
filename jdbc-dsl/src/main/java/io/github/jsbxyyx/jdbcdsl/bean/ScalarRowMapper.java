package io.github.jsbxyyx.jdbcdsl.bean;

import io.github.jsbxyyx.jdbcdsl.JdbcDslConfig;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.ClassUtils;

import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

/**
 * Maps a single result column using the same conversion rules as bean properties.
 *
 * <p>Metadata and the converter are cached for the most recently used ResultSet.
 * The ResultSet is weakly referenced so a reusable mapper does not retain it.
 */
public final class ScalarRowMapper<T> implements RowMapper<T> {

    private final Class<T> targetType;
    private final ConverterRegistry registry;
    private final MappingMode mappingMode;
    private final BooleanSupplier allowNullToPrimitiveDefault;
    private volatile ColumnMapping columnMapping;

    public ScalarRowMapper(Class<T> targetType, ConverterRegistry registry) {
        this(targetType, registry, MappingMode.STRICT);
    }

    public ScalarRowMapper(Class<T> targetType, ConverterRegistry registry, MappingMode mappingMode) {
        this(targetType, registry, mappingMode, JdbcDslConfig::isAllowNullToPrimitiveDefault);
    }

    public ScalarRowMapper(
            Class<T> targetType,
            ConverterRegistry registry,
            MappingMode mappingMode,
            BooleanSupplier allowNullToPrimitiveDefault) {
        this.targetType = targetType;
        this.registry = registry;
        this.mappingMode = java.util.Objects.requireNonNull(mappingMode, "mappingMode");
        this.allowNullToPrimitiveDefault =
                java.util.Objects.requireNonNull(allowNullToPrimitiveDefault, "allowNullToPrimitiveDefault");
    }

    @Override
    @SuppressWarnings("unchecked")
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        ColumnMapping mapping = columnMapping(rs);
        Object value = ResultSetHandler.readValue(rs, 1, targetType, mapping.jdbcType, mappingMode);
        if (value == null && mappingMode == MappingMode.MYBATIS) {
            return null;
        }
        if (value == null && targetType.isPrimitive()) {
            if (!allowNullToPrimitiveDefault.getAsBoolean()) {
                throw new IllegalStateException(
                        "SQL NULL cannot be mapped to primitive scalar " + targetType.getTypeName());
            }
            return (T) PrimitiveDefaults.valueOf(targetType);
        }
        try {
            Object converted = mapping.converter().convert(value);
            if (!ClassUtils.isAssignableValue(targetType, converted)) {
                throw new IllegalArgumentException("Converted value is incompatible with " + targetType.getName());
            }
            return (T) converted;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to map ResultSet column 1 to scalar " + targetType.getName(), e);
        }
    }

    private ColumnMapping columnMapping(ResultSet rs) throws SQLException {
        ColumnMapping mapping = columnMapping;
        if (mapping == null || mapping.resultSet.get() != rs) {
            synchronized (this) {
                mapping = columnMapping;
                if (mapping == null || mapping.resultSet.get() != rs) {
                    ResultSetMetaData metadata = rs.getMetaData();
                    int count = metadata.getColumnCount();
                    if (count != 1) {
                        throw new IncorrectResultSetColumnCountException(1, count);
                    }
                    mapping = new ColumnMapping(rs, metadata.getColumnType(1));
                    columnMapping = mapping;
                }
            }
        }
        return mapping;
    }

    private final class ColumnMapping {
        private final WeakReference<ResultSet> resultSet;
        private final int jdbcType;
        private volatile ValueConverter converter;

        private ColumnMapping(ResultSet resultSet, int jdbcType) {
            this.resultSet = new WeakReference<>(resultSet);
            this.jdbcType = jdbcType;
        }

        private ValueConverter converter() {
            ValueConverter current = converter;
            if (current == null) {
                synchronized (this) {
                    current = converter;
                    if (current == null) {
                        current = registry.getConverter(jdbcType, targetType);
                        converter = current;
                    }
                }
            }
            return current;
        }
    }
}
