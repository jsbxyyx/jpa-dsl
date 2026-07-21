package io.github.jsbxyyx.jdbcdsl.codegen;

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
import java.util.Collection;
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
 * JDBC entity and {@code @Repository} Java source files for use with {@code jdbc-dsl}.
 *
 * <p>Generated entities use {@code jakarta.persistence} annotations ({@code @Table},
 * {@code @Column}, {@code @Id}) so that {@link io.github.jsbxyyx.jdbcdsl.EntityMetaReader}
 * can resolve column mappings at runtime.
 *
 * <p>Generated repositories are thin concrete {@code @Repository} classes that bind an entity
 * and identifier type to {@link io.github.jsbxyyx.jdbcdsl.JdbcDslRepository}. Common CRUD,
 * query, and type-safe builder operations are inherited from the generic base repository.
 *
 * <p>Usage (Builder API):
 * <pre>{@code
 * JdbcEntityGenerator.builder()
 *     .dataSource(dataSource)
 *     .entityPackage("com.example.entity")
 *     .repositoryPackage("com.example.repository")
 *     .outputDir("src/main/java")
 *     .generate();
 * }</pre>
 */
public final class JdbcEntityGenerator {

    private static final Logger LOG = Logger.getLogger(JdbcEntityGenerator.class.getName());

    private JdbcEntityGenerator() {
    }

    // -------------------------------------------------------------------------
    // Builder API
    // -------------------------------------------------------------------------

    /** Returns a new {@link Builder} for fluent configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link JdbcEntityGenerator}.
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
         *   <li>{@code true}: adds {@code @Data}, {@code @NoArgsConstructor}, and
         *       {@code @Accessors(chain = true)}; no getter/setter methods are generated.</li>
         *   <li>{@code false}: generates standard getter methods and chain setter
         *       methods (setters return {@code this}) plus a no-arg constructor.</li>
         * </ul>
         */
        public Builder useLombok(boolean useLombok) {
            this.useLombok = useLombok;
            return this;
        }

        /**
         * Specifies one or more table-name prefixes to strip before converting
         * the table name to a class name (case-insensitive match).
         */
        public Builder trimPrefix(String... prefixes) {
            this.prefixes = prefixes;
            return this;
        }

        /**
         * Sets the Java package for generated {@code @Repository} classes.
         * When {@code null} or empty (the default), repository generation is skipped.
         */
        public Builder repositoryPackage(String repositoryPackage) {
            this.repositoryPackage = repositoryPackage;
            return this;
        }

        /**
         * Controls whether existing repository files are overwritten.
         * Defaults to {@code false}: if a repository file already exists it is left unchanged.
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
            JdbcEntityGenerator.doGenerate(dataSource, entityPackage, outputDir, useLombok,
                    prefixes, tableNames, repositoryPackage, repositoryOverride);
        }
    }

    // -------------------------------------------------------------------------
    // Core generation logic
    // -------------------------------------------------------------------------

    static void doGenerate(DataSource dataSource, String entityPackage, String outputDir,
                           boolean useLombok, String[] prefixes, String[] tableNames,
                           String repositoryPackage, boolean repositoryOverride) {
        LOG.log(Level.INFO,
                "JdbcEntityGenerator starting: entityPackage={0}, outputDir={1}, useLombok={2}, "
                        + "prefixes={3}, repositoryPackage={4}",
                new Object[]{entityPackage, outputDir, useLombok, Arrays.toString(prefixes), repositoryPackage});
        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            List<String> tables = resolveTables(meta, tableNames);
            LOG.log(Level.INFO, "Resolved {0} table(s): {1}", new Object[]{tables.size(), tables});
            int processed = 0;
            for (String table : tables) {
                LOG.log(Level.INFO, "Processing table: {0}", table);
                List<ColumnInfo> columns = readColumns(meta, table);
                Set<String> primaryKeys = readPrimaryKeys(meta, table);
                try {
                    writeEntity(entityPackage, outputDir, table, columns, primaryKeys, useLombok, prefixes);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to write JDBC entity for table {0}: {1}",
                            new Object[]{table, e.getMessage()});
                    throw e;
                }
                if (repositoryPackage != null && !repositoryPackage.isEmpty()) {
                    try {
                        writeRepository(entityPackage, repositoryPackage, outputDir, table, columns,
                                primaryKeys, prefixes, repositoryOverride);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Failed to write JDBC repository for table {0}: {1}",
                                new Object[]{table, e.getMessage()});
                        throw e;
                    }
                }
                processed++;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.INFO, "JdbcEntityGenerator completed: {0} table(s) processed in {1} ms",
                    new Object[]{processed, elapsed});
        } catch (SQLException | IOException e) {
            throw new RuntimeException("JdbcEntityGenerator failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata helpers
    // -------------------------------------------------------------------------

    private static List<String> resolveTables(DatabaseMetaData meta, String[] requested) throws SQLException {
        List<String> result = new ArrayList<>();
        if (requested != null && requested.length > 0) {
            result.addAll(Arrays.asList(requested));
            return result;
        }
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                result.add(rs.getString("TABLE_NAME"));
            }
        }
        return result;
    }

    private static List<ColumnInfo> readColumns(DatabaseMetaData meta, String tableName) throws SQLException {
        List<ColumnInfo> cols = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            if (!rs.next()) {
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

    private static Set<String> readPrimaryKeys(DatabaseMetaData meta, String tableName) throws SQLException {
        Set<String> pks = new LinkedHashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (pks.isEmpty()) {
            try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName.toUpperCase())) {
                while (rs.next()) {
                    pks.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return pks;
    }

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------

    private static void writeEntity(String pkg, String outputDir, String tableName,
                                    List<ColumnInfo> columns, Set<String> primaryKeys,
                                    boolean useLombok, String[] prefixes) throws IOException {
        String lowerTable = tableName.toLowerCase();
        String trimmedName = applyTrimPrefix(tableName, prefixes);
        String className = toPascalCase(trimmedName);

        Set<String> pkLower = new LinkedHashSet<>();
        for (String pk : primaryKeys) {
            pkLower.add(pk.toLowerCase());
        }

        // Collect imports
        Set<String> imports = new LinkedHashSet<>();
        if (useLombok) {
            imports.add("lombok.Data");
            imports.add("lombok.NoArgsConstructor");
            imports.add("lombok.experimental.Accessors");
        }
        imports.add("jakarta.persistence.Column");
        imports.add("jakarta.persistence.Table");

        boolean hasPk = !pkLower.isEmpty();
        if (hasPk) {
            imports.add("jakarta.persistence.Id");
        }

        // Check whether any PK column is auto-increment (IDENTITY strategy)
        boolean hasAutoIncrementPk = false;
        for (ColumnInfo col : columns) {
            if (pkLower.contains(col.name.toLowerCase()) && col.autoIncrement) {
                hasAutoIncrementPk = true;
                break;
            }
        }
        if (hasAutoIncrementPk) {
            imports.add("jakarta.persistence.GeneratedValue");
            imports.add("jakarta.persistence.GenerationType");
        }

        // Check whether any column needs special jdbc-dsl annotations
        boolean hasLogicalDelete = false;
        boolean hasCreatedDate = false;
        boolean hasLastModifiedDate = false;
        for (ColumnInfo col : columns) {
            String lc = col.name.toLowerCase();
            if (isLogicalDeleteColumn(lc)) hasLogicalDelete = true;
            if (isCreatedDateColumn(lc)) hasCreatedDate = true;
            if (isLastModifiedDateColumn(lc)) hasLastModifiedDate = true;
        }
        if (hasLogicalDelete) {
            imports.add("io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete");
        }
        if (hasCreatedDate) {
            imports.add("org.springframework.data.annotation.CreatedDate");
        }
        if (hasLastModifiedDate) {
            imports.add("org.springframework.data.annotation.LastModifiedDate");
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

        File dir = new File(outputDir, pkg.replace('.', File.separatorChar));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + dir);
        }
        File file = new File(dir, className + ".java");

        try (PrintWriter w = newLinePrinter(file)) {

            w.println("package " + pkg + ";");
            w.println();

            writeImports(w, imports);
            w.println();

            w.println("/**");
            w.println(" * Auto-generated JDBC entity for table: " + lowerTable);
            w.println(" */");
            if (useLombok) {
                w.println("@Data");
                w.println("@NoArgsConstructor");
                w.println("@Accessors(chain = true)");
            }
            w.println("@Table(name = \"" + lowerTable + "\")");
            w.println("public class " + className + " {");
            w.println();

            for (ColumnInfo col : columns) {
                String javaType = javaTypes.get(col);
                String simpleType = simpleTypeName(javaType);
                String lowerCol = col.name.toLowerCase();
                String fieldName = toCamelCase(lowerCol);
                boolean isPk = pkLower.contains(lowerCol);

                if (col.comment != null && !col.comment.isBlank()) {
                    String sanitizedComment = col.comment.replace("*/", "* /");
                    w.println("    /**");
                    w.println("     * " + sanitizedComment);
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
                if (isLogicalDeleteColumn(lowerCol)) {
                    w.println("    @LogicalDelete");
                }
                if (isCreatedDateColumn(lowerCol)) {
                    w.println("    @CreatedDate");
                }
                if (isLastModifiedDateColumn(lowerCol)) {
                    w.println("    @LastModifiedDate");
                }
                w.println("    private " + simpleType + " " + fieldName + ";");
                w.println();
            }

            if (!useLombok) {
                // No-arg constructor
                w.println("    public " + className + "() {");
                w.println("    }");
                w.println();

                // Standard getters and setters
                for (ColumnInfo col : columns) {
                    String javaType = javaTypes.get(col);
                    String simpleType = simpleTypeName(javaType);
                    String lowerCol = col.name.toLowerCase();
                    String fieldName = toCamelCase(lowerCol);
                    String pascalField = toPascalCase(lowerCol);

                    String getterPrefix = "boolean".equals(simpleType) ? "is" : "get";
                    w.println("    public " + simpleType + " " + getterPrefix + pascalField + "() {");
                    w.println("        return this." + fieldName + ";");
                    w.println("    }");
                    w.println();

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
        LOG.log(Level.INFO, "JDBC entity file written: {0}", file.getAbsolutePath());
    }

    private static void writeRepository(String entityPackage, String repositoryPackage, String outputDir,
                                        String tableName, List<ColumnInfo> columns, Set<String> primaryKeys,
                                        String[] prefixes, boolean override) throws IOException {
        String trimmedName = applyTrimPrefix(tableName, prefixes);
        String entityClassName = toPascalCase(trimmedName);
        String repositoryClassName = entityClassName + "Repository";
        String lowerTable = tableName.toLowerCase();

        // Build case-insensitive PK set and find PK column info
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
            LOG.log(Level.INFO, "JDBC repository file skipped (already exists): {0}", file.getAbsolutePath());
            return;
        }

        try (PrintWriter w = newLinePrinter(file)) {

            w.println("package " + repositoryPackage + ";");
            w.println();
            Set<String> repoImports = new LinkedHashSet<>();
            repoImports.add(entityPackage + "." + entityClassName);
            repoImports.add("io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor");
            repoImports.add("io.github.jsbxyyx.jdbcdsl.JdbcDslRepository");
            repoImports.add("org.springframework.beans.factory.annotation.Autowired");
            repoImports.add("org.springframework.stereotype.Repository");
            writeImports(w, repoImports);
            w.println();
            w.println("/**");
            w.println(" * JDBC repository binding for table: " + lowerTable + ".");
            w.println(" * Common CRUD, query, and builder operations are inherited from JdbcDslRepository.");
            w.println(" */");
            w.println("@Repository");
            w.println("public class " + repositoryClassName + " extends JdbcDslRepository<"
                    + entityClassName + ", " + pkJavaType + "> {");
            w.println();
            w.println("    @Autowired");
            w.println("    public " + repositoryClassName + "(JdbcDslExecutor jdbcDslExecutor) {");
            w.println("        super(" + entityClassName + ".class, jdbcDslExecutor);");
            w.println("    }");
            w.println("}");
        }
        LOG.log(Level.INFO, "JDBC repository file written: {0}", file.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // PrintWriter factory — always uses LF (\n) regardless of platform
    // -------------------------------------------------------------------------

    private static PrintWriter newLinePrinter(File file) throws IOException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            @Override
            public void println() {
                print('\n');
            }
            @Override
            public void println(String x) {
                print(x);
                print('\n');
            }
        };
    }

    // -------------------------------------------------------------------------
    // Import ordering helper
    // -------------------------------------------------------------------------

    /**
     * Writes the collected imports to {@code w} using the following import order:
     * <ol>
     *   <li>Module imports (io.github.jsbxyyx.*) – in insertion order</li>
     *   <li>Blank line (only if both the previous group and the next group are non-empty)</li>
     *   <li>All other non-java/javax imports (org.*, lombok.*, jakarta.*, com.*, etc.) – in insertion order</li>
     *   <li>Blank line (only if both the previous group and the next group are non-empty)</li>
     *   <li>javax.* and java.* imports – in insertion order</li>
     *   <li>Blank line (only if both the previous group and the next group are non-empty)</li>
     *   <li>static imports – in insertion order</li>
     * </ol>
     */
    private static void writeImports(PrintWriter w, Collection<String> imports) {
        List<String> moduleImports = new ArrayList<>();
        List<String> otherImports = new ArrayList<>();
        List<String> javaImports = new ArrayList<>();
        List<String> staticImports = new ArrayList<>();

        for (String imp : imports) {
            if (imp.startsWith("static ")) {
                staticImports.add(imp);
            } else if (imp.startsWith("java.") || imp.startsWith("javax.")) {
                javaImports.add(imp);
            } else if (imp.startsWith("io.github.jsbxyyx.")) {
                moduleImports.add(imp);
            } else {
                otherImports.add(imp);
            }
        }

        for (String imp : moduleImports) {
            w.println("import " + imp + ";");
        }

        if (!moduleImports.isEmpty() && !otherImports.isEmpty()) {
            w.println();
        }

        for (String imp : otherImports) {
            w.println("import " + imp + ";");
        }

        boolean afterOtherBlock = !moduleImports.isEmpty() || !otherImports.isEmpty();
        if (afterOtherBlock && !javaImports.isEmpty()) {
            w.println();
        }

        for (String imp : javaImports) {
            w.println("import " + imp + ";");
        }

        boolean afterJavaBlock = afterOtherBlock || !javaImports.isEmpty();
        if (afterJavaBlock && !staticImports.isEmpty()) {
            w.println();
        }

        for (String imp : staticImports) {
            w.println("import " + imp + ";");
        }
    }

    // -------------------------------------------------------------------------
    // PK helpers
    // -------------------------------------------------------------------------

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

    private static String resolvePkFieldName(List<ColumnInfo> columns, Set<String> pkLower) {
        if (pkLower.size() == 1) {
            return toCamelCase(pkLower.iterator().next());
        }
        return "id";
    }

    private static String resolvePkColumnName(List<ColumnInfo> columns, Set<String> pkLower) {
        if (pkLower.size() == 1) {
            return pkLower.iterator().next();
        }
        return "id";
    }

    /** Returns the Number method to use for converting the generated key based on the PK type. */
    private static String pkNumberMethod(String pkJavaType) {
        return switch (pkJavaType) {
            case "Long" -> "longValue";
            case "Integer" -> "intValue";
            default -> "longValue";
        };
    }

    // -------------------------------------------------------------------------
    // Name conversion helpers
    // -------------------------------------------------------------------------

    static String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    static String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /**
     * Capitalises the first character of an already-camelCase string, leaving the rest unchanged.
     * e.g. {@code "createdAt"} → {@code "CreatedAt"}.
     * This must be used instead of {@link #toPascalCase} when the input is already camelCase
     * (toPascalCase lowercases every character that does not follow an underscore, which would
     * destroy the existing capitalisation).
     */
    static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static String applyTrimPrefix(String tableName, String[] prefixes) {
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
    // Column-name pattern helpers for special annotations
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when a column name (lower-case) maps to a logical-delete flag.
     * Pattern: {@code deleted}.
     */
    static boolean isLogicalDeleteColumn(String lowerColName) {
        return "deleted".equals(lowerColName);
    }

    /**
     * Returns {@code true} when a column name (lower-case) maps to a creation-time field.
     * Pattern: {@code created_at}.
     */
    static boolean isCreatedDateColumn(String lowerColName) {
        return "created_at".equals(lowerColName);
    }

    /**
     * Returns {@code true} when a column name (lower-case) maps to an update-time field.
     * Pattern: {@code updated_at}.
     */
    static boolean isLastModifiedDateColumn(String lowerColName) {
        return "updated_at".equals(lowerColName);
    }

    static String toJavaType(String sqlType) {
        if (sqlType == null) {
            return "Object";
        }
        String upper = sqlType.toUpperCase();
        int paren = upper.indexOf('(');
        if (paren >= 0) {
            upper = upper.substring(0, paren).trim();
        }
        return switch (upper) {
            case "VARCHAR", "CHAR", "TEXT", "CLOB", "LONGVARCHAR",
                    "NVARCHAR", "NCHAR", "NCLOB", "LONGNVARCHAR",
                    "CHARACTER VARYING", "CHARACTER",
                    "TINYTEXT", "MEDIUMTEXT", "LONGTEXT" -> "String";
            case "INTEGER", "INT", "INT4" -> "Integer";
            case "BIGINT", "INT8" -> "Long";
            case "SMALLINT", "TINYINT", "INT2" -> "Short";
            case "FLOAT", "FLOAT4", "REAL" -> "Float";
            case "DOUBLE", "FLOAT8", "DOUBLE PRECISION" -> "Double";
            case "DECIMAL", "NUMERIC" -> "java.math.BigDecimal";
            case "BOOLEAN", "BOOL", "BIT" -> "Boolean";
            case "DATE" -> "java.time.LocalDate";
            case "TIME" -> "java.time.LocalTime";
            case "TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE", "DATETIME", "TIMESTAMP(6)" -> "java.time.LocalDateTime";
            case "TIMESTAMP WITH TIME ZONE" -> "java.time.OffsetDateTime";
            default -> "Object";
        };
    }

    static String simpleTypeName(String fullTypeName) {
        int dot = fullTypeName.lastIndexOf('.');
        return dot < 0 ? fullTypeName : fullTypeName.substring(dot + 1);
    }

    // -------------------------------------------------------------------------
    // Internal column metadata
    // -------------------------------------------------------------------------

    static final class ColumnInfo {
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
