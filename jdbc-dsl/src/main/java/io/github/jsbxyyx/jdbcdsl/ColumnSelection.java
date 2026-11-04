package io.github.jsbxyyx.jdbcdsl;

record ColumnSelection(String columnName, String propertyName) {
    String resolve(EntityMeta meta) {
        if (propertyName == null) {
            return columnName;
        }
        String resolved = meta.getColumnName(propertyName);
        return resolved == null ? propertyName : resolved;
    }
}
