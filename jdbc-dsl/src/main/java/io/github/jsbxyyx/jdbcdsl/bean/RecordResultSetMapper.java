package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.util.ClassUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Maps result columns to the canonical constructor of a record.
 */
final class RecordResultSetMapper implements ResultSetMapper {

    private final RecordMappingMeta meta;
    private final ComponentMapping[] mappings;
    private final int columnCount;

    private record ComponentMapping(
            int columnIndex, String name, Class<?> type, int jdbcType, ValueConverter converter) {}

    RecordResultSetMapper(ResultSet rs, RecordMappingMeta meta, ConverterRegistry registry) throws SQLException {
        this.meta = meta;
        String[] names = meta.getParameterNames();
        Class<?>[] types = meta.getParameterTypes();
        mappings = new ComponentMapping[names.length];
        ResultSetMetaData metadata = rs.getMetaData();
        columnCount = metadata.getColumnCount();

        for (int column = 1; column <= columnCount; column++) {
            String label = metadata.getColumnLabel(column);
            if (label == null || label.isBlank()) {
                label = metadata.getColumnName(column);
            }
            if (label == null || label.isBlank()) {
                continue;
            }
            Integer index = meta.getPropertyIndex(label);
            if (index == null) {
                continue;
            }
            if (mappings[index] != null) {
                throw new IllegalStateException("Multiple columns match record component " + names[index] + " of "
                        + meta.getType().getName());
            }
            int jdbcType = metadata.getColumnType(column);
            mappings[index] = new ComponentMapping(
                    column, names[index], types[index], jdbcType, registry.getConverter(jdbcType, types[index]));
        }
        for (int i = 0; i < names.length; i++) {
            if (mappings[i] == null) {
                throw new IllegalStateException("Missing column for record component " + names[i] + " of "
                        + meta.getType().getName());
            }
        }
    }

    @Override
    public Object mapRow(ResultSet rs) throws SQLException {
        Object[] args = new Object[mappings.length];
        for (int i = 0; i < mappings.length; i++) {
            ComponentMapping mapping = mappings[i];
            Object value = ResultSetHandler.readValue(rs, mapping.columnIndex(), mapping.type(), mapping.jdbcType());
            if (value == null && mapping.type().isPrimitive()) {
                throw new IllegalStateException("SQL NULL for primitive record component " + mapping.name()
                        + " at column " + mapping.columnIndex() + " of "
                        + meta.getType().getName());
            }
            try {
                args[i] = mapping.converter().convert(value);
                if (!ClassUtils.isAssignableValue(mapping.type(), args[i])) {
                    throw new IllegalArgumentException("Converted value is incompatible with "
                            + mapping.type().getName());
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Failed to map ResultSet column " + mapping.columnIndex()
                                + " to record component " + mapping.name() + " of "
                                + meta.getType().getName()
                                + ", value type="
                                + (value == null ? "null" : value.getClass().getName()),
                        e);
            }
        }
        return meta.newInstance(args);
    }

    @Override
    public Class<?> getTargetType() {
        return meta.getType();
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }
}
