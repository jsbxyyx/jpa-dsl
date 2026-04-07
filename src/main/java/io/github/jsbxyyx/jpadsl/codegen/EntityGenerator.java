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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that reads database table metadata via JDBC and generates
 * JPA {@code @Entity} Java source files.
 *
 * <p>Usage (Builder API):
 * <pre>{@code
 * EntityGenerator.builder()
 *     .dataSource(dataSource)
 *     .entityPackage("com.example.entity")
 *     .trimPrefix("t_", "sys_")
 *     .generate();
 * }</pre>
 *
 * <p>Legacy static API:
 * <pre>{@code
 * EntityGenerator.generate(dataSource, "com.example.entity", "src/main/java");
 * }</pre>
 */
public final class EntityGenerator {

    private static final Logger LOG = Logger.getLogger(EntityGenerator.class.getName());

    private EntityGenerator() {
    }

    // -------------------------------------------------------------------------
    // Builder API
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link Builder} for fluent configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link EntityGenerator}.
     */
    public static final class Builder {
        private DataSource dataSource;
        private String entityPackage;
        private String outputDir = "src/main/java";
        private boolean useLombok = true;
        private String[] prefixes = new String[0];
        private String repositoryPackage = null;
        private boolean repositoryOverride = false;

        private Builder() {
        }

        /** Sets the JDBC data source (required). */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /** Sets the Java package for generated entity classes (required). */
        public Builder entityPackage(String entityPackage) {
            this.entityPackage = entityPackage;
            return this;
        }

        /** Sets the root source directory. Defaults to {@code "src/main/java"}. */
        public Builder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /**
         * Controls Lombok annotation generation. Defaults to {@code true}.
         * <ul>
         *   <li>{@code true}: adds {@code @Data} and {@code @Accessors(chain = true)};
         *       no getter/setter methods are generated.</li>
         *   <li>{@code false}: generates standard getter methods and chain setter
         *       methods (setters return {@code this}).</li>
         * </ul>
         */
        public Builder useLombok(boolean useLombok) {
            this.useLombok = useLombok;
            return this;
        }

        /**
         * Specifies one or more table-name prefixes to strip before converting
         * the table name to a class name (case-insensitive match).
         * Example: {@code trimPrefix("t_", "sys_")} turns {@code t_user} into {@code User}.
         */
        public Builder trimPrefix(String... prefixes) {
            this.prefixes = prefixes;
            return this;
        }

        /**
         * Sets the Java package for generated Spring Data JPA repository interfaces.
         * When {@code null} or empty (the default), repository generation is skipped.
         */
        public Builder repositoryPackage(String repositoryPackage) {
            this.repositoryPackage = repositoryPackage;
            return this;
        }

        /**
         * Controls whether existing repository files are overwritten.
         * Defaults to {@code false}: if a repository file already exists it is left unchanged.
         * When {@code true} the file is always (re-)written.
         */
        public Builder repositoryOverride(boolean repositoryOverride) {
            this.repositoryOverride = repositoryOverride;
            return this;
        }

        /** Generates entity files for <em>all</em> tables in the database. */
        public void generate() {
            doGenerate((String[]) null);
        }

        /**
         * Generates entity files only for the specified tables.
         *
         * @param tableNames the tables to generate
         */
        public void generate(String... tableNames) {
            doGenerate(tableNames);
        }

        private void doGenerate(String[] tableNames) {
            if (dataSource == null) {
                throw new IllegalStateException("dataSource is required");
            }
            if (entityPackage == null || entityPackage.isEmpty()) {
                throw new IllegalStateException("entityPackage is required");
            }
            EntityGenerator.doGenerate(dataSource, entityPackage, outputDir, useLombok, prefixes, tableNames,
                    repositoryPackage, repositoryOverride);
        }
    }

    // -------------------------------------------------------------------------
    // Backward-compatible static methods
    // -------------------------------------------------------------------------

    /**
     * Generates JPA entity source files for <em>all</em> tables in the database.
     *
     * @param dataSource    the JDBC data source
     * @param entityPackage the Java package name for the generated entities (e.g. {@code "com.example.entity"})
     * @param outputDir     the root source directory (e.g. {@code "src/main/java"}); package sub-directories are
     *                      created automatically
     */
    public static void generate(DataSource dataSource, String entityPackage, String outputDir) {
        builder().dataSource(dataSource).entityPackage(entityPackage).outputDir(outputDir).generate();
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
        builder().dataSource(dataSource).entityPackage(entityPackage).outputDir(outputDir).generate(tableNames);
    }

    // -------------------------------------------------------------------------
    // Core implementation
    // -------------------------------------------------------------------------

    private static void doGenerate(DataSource dataSource, String entityPackage, String outputDir,
                                   boolean useLombok, String[] prefixes, String[] tableNames,
                                   String repositoryPackage, boolean repositoryOverride) {
        LOG.log(Level.INFO, "EntityGenerator starting: entityPackage={0}, outputDir={1}, useLombok={2}, prefixes={3}, repositoryPackage={4}",
                new Object[]{entityPackage, outputDir, useLombok, Arrays.toString(prefixes), repositoryPackage});
        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = resolveTables(meta, catalog, schema, tableNames);
            LOG.log(Level.INFO, "Resolved {0} table(s): {1}", new Object[]{tables.size(), tables});
            int processed = 0;
            for (String table : tables) {
                LOG.log(Level.INFO, "Processing table: {0}", table);
                List<ColumnInfo> columns = readColumns(meta, catalog, schema, table);
                Set<String> primaryKeys = readPrimaryKeys(meta, catalog, schema, table);
                LOG.log(Level.FINE, "Table {0}: {1} column(s), {2} primary key(s): {3}",
                        new Object[]{table, columns.size(), primaryKeys.size(), primaryKeys});
                try {
                    writeEntity(entityPackage, outputDir, table, columns, primaryKeys, useLombok, prefixes);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to write entity for table {0}: {1}",
                            new Object[]{table, e.getMessage()});
                    throw e;
                }
                if (repositoryPackage != null && !repositoryPackage.isEmpty()) {
                    try {
                        writeRepository(entityPackage, repositoryPackage, outputDir, table, columns,
                                primaryKeys, prefixes, repositoryOverride);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Failed to write repository for table {0}: {1}",
                                new Object[]{table, e.getMessage()});
                        throw e;
                    }
                }
                processed++;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.INFO, "EntityGenerator completed: {0} table(s) processed in {1} ms",
                    new Object[]{processed, elapsed});
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
        String comment = rs.getString("REMARKS");
        return new ColumnInfo(colName, typeName, nullable, autoIncrement, ordinalPosition, comment);
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
                                    List<ColumnInfo> columns, Set<String> primaryKeys,
                                    boolean useLombok, String[] prefixes) throws IOException {
        // Normalize names to lowercase so generated annotations are DB-case-agnostic.
        String lowerTable = tableName.toLowerCase();
        // Apply prefix trimming before converting to class name.
        String trimmedName = applyTrimPrefix(tableName, prefixes);
        String className = toPascalCase(trimmedName);

        // Primary-key lookup is case-insensitive (H2 returns uppercase names).
        Set<String> pkLower = new LinkedHashSet<>();
        for (String pk : primaryKeys) {
            pkLower.add(pk.toLowerCase());
        }

        // Collect imports
        Set<String> imports = new LinkedHashSet<>();
        if (useLombok) {
            imports.add("lombok.Data");
            imports.add("lombok.experimental.Accessors");
        }
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
            w.println(" */");
            if (useLombok) {
                w.println("@Data");
                w.println("@Accessors(chain = true)");
            }
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

                if (col.comment != null && !col.comment.isEmpty()) {
                    w.println("    /**");
                    w.println("     * " + col.comment.replace("*/", "* /"));
                    w.println("     */");
                }
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

            if (!useLombok) {
                // Generate standard getter and chain setter methods.
                for (ColumnInfo col : columns) {
                    String javaType = javaTypes.get(col);
                    String simpleType = simpleTypeName(javaType);
                    String lowerCol = col.name.toLowerCase();
                    String fieldName = toCamelCase(lowerCol);
                    String pascalField = toPascalCase(lowerCol);

                    // Getter
                    String getterPrefix = "Boolean".equals(simpleType) || "boolean".equals(simpleType) ? "is" : "get";
                    w.println("    public " + simpleType + " " + getterPrefix + pascalField + "() {");
                    w.println("        return this." + fieldName + ";");
                    w.println("    }");
                    w.println();

                    // Chain setter (returns this)
                    w.println("    public " + className + " set" + pascalField
                            + "(" + simpleType + " " + fieldName + ") {");
                    w.println("        this." + fieldName + " = " + fieldName + ";");
                    w.println("        return this;");
                    w.println("    }");
                    w.println();
                }
            }

            w.println("}");
        }
        LOG.log(Level.INFO, "Entity file written: {0}", file.getAbsolutePath());
    }

    private static void writeRepository(String entityPackage, String repositoryPackage, String outputDir,
                                        String tableName, List<ColumnInfo> columns, Set<String> primaryKeys,
                                        String[] prefixes, boolean override) throws IOException {
        String trimmedName = applyTrimPrefix(tableName, prefixes);
        String entityClassName = toPascalCase(trimmedName);
        String repositoryClassName = entityClassName + "Repository";

        // Build case-insensitive PK set
        Set<String> pkLower = new LinkedHashSet<>();
        for (String pk : primaryKeys) {
            pkLower.add(pk.toLowerCase());
        }
        String pkJavaType = resolvePkJavaType(columns, pkLower);

        File dir = new File(outputDir, repositoryPackage.replace('.', File.separatorChar));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + dir);
        }
        File file = new File(dir, repositoryClassName + ".java");
        if (file.exists() && !override) {
            LOG.log(Level.INFO, "Repository file skipped (already exists): {0}", file.getAbsolutePath());
            return;
        }

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.println("package " + repositoryPackage + ";");
            w.println();
            w.println("import " + entityPackage + "." + entityClassName + ";");
            w.println("import io.github.jsbxyyx.jpadsl.JpaDeleteExecutor;");
            w.println("import io.github.jsbxyyx.jpadsl.JpaUpdateExecutor;");
            w.println("import org.springframework.data.jpa.repository.JpaRepository;");
            w.println("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;");
            w.println("import org.springframework.stereotype.Repository;");
            w.println();
            w.println("@Repository");
            w.println("public interface " + repositoryClassName + " extends JpaRepository<"
                    + entityClassName + ", " + pkJavaType + ">, JpaSpecificationExecutor<"
                    + entityClassName + ">, JpaUpdateExecutor<" + entityClassName + ">,"
                    + " JpaDeleteExecutor<" + entityClassName + "> {");
            w.println("}");
        }
        LOG.log(Level.INFO, "Repository file written: {0}", file.getAbsolutePath());
    }

    /**
     * Resolves the simple Java type name of the primary key for use in repository generation.
     *
     * <p>When there is exactly one primary key column and its column info is found in {@code columns},
     * the corresponding Java type is returned (e.g. {@code "Long"}, {@code "Integer"}, {@code "String"}).
     * In all other cases — composite primary keys, missing PK column info, or tables without a PK —
     * {@code "Long"} is returned as a safe default.
     *
     * @param columns  the full list of column metadata for the table
     * @param pkLower  the set of primary key column names in lower-case
     * @return the simple Java type name for the repository ID parameter
     */
    private static String resolvePkJavaType(List<ColumnInfo> columns, Set<String> pkLower) {
        if (pkLower.size() == 1) {
            String pkColName = pkLower.iterator().next();
            for (ColumnInfo col : columns) {
                if (col.name.toLowerCase().equals(pkColName)) {
                    return simpleTypeName(toJavaType(col.typeName));
                }
            }
        }
        return "Long";
    }

    /**
     * Strips the first matching prefix from {@code tableName} (case-insensitive).
     * Returns the original name unchanged if no prefix matches.
     */
    private static String applyTrimPrefix(String tableName, String[] prefixes) {
        if (prefixes == null || prefixes.length == 0) {
            return tableName;
        }
        String lowerName = tableName.toLowerCase();
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isEmpty()
                    && lowerName.startsWith(prefix.toLowerCase())) {
                return tableName.substring(prefix.length());
            }
        }
        return tableName;
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
        final String comment;

        ColumnInfo(String name, String typeName, boolean nullable, boolean autoIncrement, int ordinalPosition,
                   String comment) {
            this.name = name;
            this.typeName = typeName;
            this.nullable = nullable;
            this.autoIncrement = autoIncrement;
            this.ordinalPosition = ordinalPosition;
            this.comment = comment;
        }
    }
}
