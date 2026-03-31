package io.github.jsbxyyx.jpadsl.codegen;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class that reads database table metadata via JDBC and generates
 * JPA {@code @Entity} Java source files.
 *
 * <p>Usage:
 * <pre>{@code
 * EntityGenerator.generate(dataSource, "com.example.entity", "src/main/java");
 * }</pre>
 *
 * <p>You may add Lombok {@code @Data} annotation to the generated classes to
 * automatically generate getters/setters.</p>
 */
public final class EntityGenerator {

    private EntityGenerator() {
    }

    /**
     * Generates JPA entity source files for <em>all</em> tables in the database.
     *
     * @param dataSource    the JDBC data source
     * @param entityPackage the Java package name for the generated entities (e.g. {@code "com.example.entity"})
     * @param outputDir     the root source directory (e.g. {@code "src/main/java"}); package sub-directories are
     *                      created automatically
     */
    public static void generate(DataSource dataSource, String entityPackage, String outputDir) {
        generate(dataSource, entityPackage, outputDir, (String[]) null);
    }

    /**
     * Generates JPA entity source files for the specified tables.
     *
     * @param dataSource    the JDBC data source
     * @param entityPackage the Java package name for the generated entities
     * @param outputDir     the root source directory
     * @param tableNames    the tables to generate; when {@code null} or empty all tables are generated
     */
    public static void generate(DataSource dataSource, String entityPackage, String outputDir,
                                String... tableNames) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = resolveTables(meta, catalog, schema, tableNames);
            for (String table : tables) {
                List<ColumnInfo> columns = readColumns(meta, catalog, schema, table);
                Set<String> primaryKeys = readPrimaryKeys(meta, catalog, schema, table);
                writeEntity(entityPackage, outputDir, table, columns, primaryKeys);
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("EntityGenerator failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata helpers
    // -------------------------------------------------------------------------

    private static List<String> resolveTables(DatabaseMetaData meta, String catalog, String schema,
                                               String[] requested) throws SQLException {
        List<String> result = new ArrayList<>();
        if (requested != null && requested.length > 0) {
            result.addAll(Arrays.asList(requested));
            return result;
        }
        // Use null for catalog/schema to search across all schemas portably.
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                result.add(rs.getString("TABLE_NAME"));
            }
        }
        return result;
    }

    private static List<ColumnInfo> readColumns(DatabaseMetaData meta, String catalog, String schema,
                                                 String tableName) throws SQLException {
        List<ColumnInfo> cols = new ArrayList<>();
        // Pass null for catalog/schema to be portable across databases (e.g. H2, MySQL, PostgreSQL).
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            if (!rs.next()) {
                // Some databases (e.g. H2 in default mode) store names in uppercase; retry.
                try (ResultSet rsUpper = meta.getColumns(null, null, tableName.toUpperCase(), "%")) {
                    while (rsUpper.next()) {
                        cols.add(columnInfoFromResultSet(rsUpper));
                    }
                }
                cols.sort(Comparator.comparingInt(c -> c.ordinalPosition));
                return cols;
            }
            cols.add(columnInfoFromResultSet(rs));
            while (rs.next()) {
                cols.add(columnInfoFromResultSet(rs));
            }
        }
        cols.sort(Comparator.comparingInt(c -> c.ordinalPosition));
        return cols;
    }

    private static ColumnInfo columnInfoFromResultSet(ResultSet rs) throws SQLException {
        String colName = rs.getString("COLUMN_NAME");
        String typeName = rs.getString("TYPE_NAME");
        boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        String isAutoincrement = rs.getString("IS_AUTOINCREMENT");
        boolean autoIncrement = "YES".equalsIgnoreCase(isAutoincrement);
        int ordinalPosition = rs.getInt("ORDINAL_POSITION");
        return new ColumnInfo(colName, typeName, nullable, autoIncrement, ordinalPosition);
    }

    private static Set<String> readPrimaryKeys(DatabaseMetaData meta, String catalog, String schema,
                                                String tableName) throws SQLException {
        Set<String> pks = new LinkedHashSet<>();
        // Pass null for catalog/schema to be portable across databases.
        try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (pks.isEmpty()) {
            // Retry with uppercase table name for databases that store names in uppercase.
            try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName.toUpperCase())) {
                while (rs.next()) {
                    pks.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return pks;
    }

    // -------------------------------------------------------------------------
    // Code-generation
    // -------------------------------------------------------------------------

    private static void writeEntity(String pkg, String outputDir, String tableName,
                                    List<ColumnInfo> columns, Set<String> primaryKeys) throws IOException {
        // Normalize names to lowercase so generated annotations are DB-case-agnostic.
        String lowerTable = tableName.toLowerCase();
        String className = toPascalCase(tableName);

        // Primary-key lookup is case-insensitive (H2 returns uppercase names).
        Set<String> pkLower = new LinkedHashSet<>();
        for (String pk : primaryKeys) {
            pkLower.add(pk.toLowerCase());
        }

        // Collect imports
        Set<String> imports = new LinkedHashSet<>();
        imports.add("jakarta.persistence.Column");
        imports.add("jakarta.persistence.Entity");
        imports.add("jakarta.persistence.Table");

        boolean hasPk = !pkLower.isEmpty();
        boolean hasAutoIncrement = columns.stream()
                .anyMatch(c -> pkLower.contains(c.name.toLowerCase()) && c.autoIncrement);

        if (hasPk) {
            imports.add("jakarta.persistence.Id");
        }
        if (hasAutoIncrement) {
            imports.add("jakarta.persistence.GeneratedValue");
            imports.add("jakarta.persistence.GenerationType");
        }

        // Determine Java types and collect additional imports
        Map<ColumnInfo, String> javaTypes = new LinkedHashMap<>();
        for (ColumnInfo col : columns) {
            String javaType = toJavaType(col.typeName);
            javaTypes.put(col, javaType);
            switch (javaType) {
                case "java.math.BigDecimal":
                    imports.add("java.math.BigDecimal");
                    break;
                case "java.time.LocalDate":
                    imports.add("java.time.LocalDate");
                    break;
                case "java.time.LocalTime":
                    imports.add("java.time.LocalTime");
                    break;
                case "java.time.LocalDateTime":
                    imports.add("java.time.LocalDateTime");
                    break;
                case "java.time.OffsetDateTime":
                    imports.add("java.time.OffsetDateTime");
                    break;
                default:
                    break;
            }
        }

        // Resolve output path
        File dir = new File(outputDir, pkg.replace('.', File.separatorChar));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + dir);
        }
        File file = new File(dir, className + ".java");

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            w.println("package " + pkg + ";");
            w.println();

            for (String imp : imports) {
                w.println("import " + imp + ";");
            }
            w.println();

            w.println("/**");
            w.println(" * Auto-generated JPA entity for table: " + lowerTable);
            w.println(" * <p>You may add Lombok @Data annotation to generate getters/setters.</p>");
            w.println(" */");
            w.println("@Entity");
            w.println("@Table(name = \"" + lowerTable + "\")");
            w.println("public class " + className + " {");
            w.println();

            for (ColumnInfo col : columns) {
                String javaType = javaTypes.get(col);
                String simpleType = simpleTypeName(javaType);
                String lowerCol = col.name.toLowerCase();
                String fieldName = toCamelCase(lowerCol);
                boolean isPk = pkLower.contains(lowerCol);

                if (isPk) {
                    w.println("    @Id");
                    if (col.autoIncrement) {
                        w.println("    @GeneratedValue(strategy = GenerationType.IDENTITY)");
                    }
                }
                if (!col.nullable) {
                    w.println("    @Column(name = \"" + lowerCol + "\", nullable = false)");
                } else {
                    w.println("    @Column(name = \"" + lowerCol + "\")");
                }
                w.println("    private " + simpleType + " " + fieldName + ";");
                w.println();
            }

            w.println("}");
        }
    }

    // -------------------------------------------------------------------------
    // Type mappings
    // -------------------------------------------------------------------------

    static String toJavaType(String sqlType) {
        if (sqlType == null) {
            return "Object";
        }
        String upper = sqlType.toUpperCase();
        // Strip size qualifiers such as "VARCHAR(255)"
        int paren = upper.indexOf('(');
        if (paren >= 0) {
            upper = upper.substring(0, paren).trim();
        }
        switch (upper) {
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "CLOB":
            case "LONGVARCHAR":
            case "NVARCHAR":
            case "NCHAR":
            case "NCLOB":
            case "LONGNVARCHAR":
            case "CHARACTER VARYING":   // H2 / PostgreSQL
            case "CHARACTER":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
                return "String";
            case "INTEGER":
            case "INT":
            case "INT4":
                return "Integer";
            case "BIGINT":
            case "INT8":
                return "Long";
            case "SMALLINT":
            case "TINYINT":
            case "INT2":
                return "Short";
            case "FLOAT":
            case "FLOAT4":
            case "REAL":
                return "Float";
            case "DOUBLE":
            case "FLOAT8":
            case "DOUBLE PRECISION":
                return "Double";
            case "DECIMAL":
            case "NUMERIC":
                return "java.math.BigDecimal";
            case "BOOLEAN":
            case "BOOL":
            case "BIT":
                return "Boolean";
            case "DATE":
                return "java.time.LocalDate";
            case "TIME":
                return "java.time.LocalTime";
            case "TIMESTAMP":
            case "DATETIME":
            case "TIMESTAMP WITHOUT TIME ZONE":
                return "java.time.LocalDateTime";
            case "TIMESTAMP WITH TIME ZONE":
            case "TIMESTAMPTZ":
                return "java.time.OffsetDateTime";
            case "BLOB":
            case "BINARY":
            case "VARBINARY":
            case "LONGVARBINARY":
            case "BYTEA":
                return "byte[]";
            default:
                return "Object";
        }
    }

    private static String simpleTypeName(String javaType) {
        int dot = javaType.lastIndexOf('.');
        return dot >= 0 ? javaType.substring(dot + 1) : javaType;
    }

    // -------------------------------------------------------------------------
    // Naming helpers
    // -------------------------------------------------------------------------

    /**
     * Converts {@code snake_case} (or {@code SCREAMING_SNAKE_CASE}) to {@code PascalCase}.
     */
    static String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * Converts {@code snake_case} to {@code camelCase}.
     */
    static String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) {
            return pascal;
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    // -------------------------------------------------------------------------
    // Internal DTO
    // -------------------------------------------------------------------------

    private static final class ColumnInfo {
        final String name;
        final String typeName;
        final boolean nullable;
        final boolean autoIncrement;
        final int ordinalPosition;

        ColumnInfo(String name, String typeName, boolean nullable, boolean autoIncrement, int ordinalPosition) {
            this.name = name;
            this.typeName = typeName;
            this.nullable = nullable;
            this.autoIncrement = autoIncrement;
            this.ordinalPosition = ordinalPosition;
        }
    }
}
