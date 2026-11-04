package io.github.jsbxyyx.jdbcdsl.bean;

import io.github.jsbxyyx.jdbcdsl.JdbcDslConfig;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.ClassUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

/**
 * Maps a single result column using the same conversion rules as bean properties.
 */
public final class ScalarRowMapper<T> implements RowMapper<T> {

    private final Class<T> targetType;
    private final ConverterRegistry registry;
    private final MappingMode mappingMode;
    private final BooleanSupplier allowNullToPrimitiveDefault;

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
        ResultSetMetaData metadata = rs.getMetaData();
        int count = metadata.getColumnCount();
        if (count != 1) {
            throw new IncorrectResultSetColumnCountException(1, count);
        }
        int jdbcType = metadata.getColumnType(1);
        Object value = ResultSetHandler.readValue(rs, 1, targetType, jdbcType, mappingMode);
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
            Object converted = registry.getConverter(jdbcType, targetType).convert(value);
            if (!ClassUtils.isAssignableValue(targetType, converted)) {
                throw new IllegalArgumentException("Converted value is incompatible with " + targetType.getName());
            }
            return (T) converted;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to map ResultSet column 1 to scalar " + targetType.getName(), e);
        }
    }
}
