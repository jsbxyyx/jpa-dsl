package io.github.jsbxyyx.jpadsl.codegen;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

class EntityGeneratorTest {

    private static final String DB_URL = "jdbc:h2:mem:entitygen;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private SimpleDriverDataSource dataSource;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(DriverManager.getDriver(DB_URL), DB_URL, "sa", "");

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS user_order");
            stmt.execute("CREATE TABLE user_order (" + "  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  order_number VARCHAR(50)  NOT NULL,"
                    + "  amount      DECIMAL(10,2),"
                    + "  created_at  TIMESTAMP"
                    + ")");
            stmt.execute("COMMENT ON COLUMN user_order.id IS 'Primary key ID'");
            stmt.execute("COMMENT ON COLUMN user_order.order_number IS 'Order number'");
            stmt.execute("COMMENT ON COLUMN user_order.amount IS 'Order amount'");
            stmt.execute("COMMENT ON COLUMN user_order.created_at IS 'Creation time'");
        }

        outputDir = Files.createTempDirectory("entity-gen-test").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(outputDir);
    }

    // -------------------------------------------------------------------------
    // Generate-all-tables variant (legacy static API, Lombok default = true)
    // -------------------------------------------------------------------------

    @Test
    void testGenerateAllTables() throws Exception {
        EntityGenerator.generate(dataSource, "com.example.entity", outputDir.getAbsolutePath());

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());

        String content = readFile(entityFile);
        assertEntityContent(content);
    }

    // -------------------------------------------------------------------------
    // Generate-specific-tables variant (legacy static API)
    // -------------------------------------------------------------------------

    @Test
    void testGenerateSpecificTable() throws Exception {
        EntityGenerator.generate(dataSource, "com.example.entity", outputDir.getAbsolutePath(), "user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());

        String content = readFile(entityFile);
        assertEntityContent(content);
    }

    // -------------------------------------------------------------------------
    // Builder API — Lombok mode (default true)
    // -------------------------------------------------------------------------

    @Test
    void testLombokMode() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(true)
                .generate("user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        // Lombok annotations present
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.Data;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.experimental.Accessors;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Data"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Accessors(chain = true)"));

        // No getter/setter methods generated
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("public Long getId()"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("public UserOrder setId("));
    }

    // -------------------------------------------------------------------------
    // Builder API — non-Lombok mode
    // -------------------------------------------------------------------------

    @Test
    void testNoLombokMode() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        // No Lombok annotations
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@Data"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@Accessors"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("import lombok"));

        // Standard getter methods
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public Long getId()"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public String getOrderNumber()"));

        // Chain setter methods (return this)
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public UserOrder setId(Long id) {"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("return this;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public UserOrder setOrderNumber(String orderNumber) {"));
    }

    // -------------------------------------------------------------------------
    // Builder API — trimPrefix
    // -------------------------------------------------------------------------

    @Test
    void testTrimPrefix() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_user");
            stmt.execute("DROP TABLE IF EXISTS sys_config");
            stmt.execute("CREATE TABLE t_user (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute(
                    "CREATE TABLE sys_config (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, cfg_key VARCHAR(50))");
        }

        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .trimPrefix("t_", "sys_")
                .generate("t_user", "sys_config");

        // t_user -> User
        File userFile = new File(outputDir, "com/example/entity/User.java");
        org.junit.jupiter.api.Assertions.assertTrue(userFile.exists());
        String userContent = readFile(userFile);
        org.junit.jupiter.api.Assertions.assertTrue(userContent.contains("public class User {"));
        org.junit.jupiter.api.Assertions.assertTrue(userContent.contains("@Table(name = \"t_user\")"));

        // sys_config -> Config
        File configFile = new File(outputDir, "com/example/entity/Config.java");
        org.junit.jupiter.api.Assertions.assertTrue(configFile.exists());
        String configContent = readFile(configFile);
        org.junit.jupiter.api.Assertions.assertTrue(configContent.contains("public class Config {"));
        org.junit.jupiter.api.Assertions.assertTrue(configContent.contains("@Table(name = \"sys_config\")"));
    }

    // -------------------------------------------------------------------------
    // Builder API — generate specific tables only
    // -------------------------------------------------------------------------

    @Test
    void testGenerateSpecificTablesOnly() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_product");
            stmt.execute("DROP TABLE IF EXISTS t_category");
            stmt.execute("CREATE TABLE t_product (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("CREATE TABLE t_category (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, title VARCHAR(50))");
        }

        // Only generate user_order, not t_product or t_category
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        File productFile = new File(outputDir, "com/example/entity/TProduct.java");
        File categoryFile = new File(outputDir, "com/example/entity/TCategory.java");

        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        org.junit.jupiter.api.Assertions.assertFalse(productFile.exists());
        org.junit.jupiter.api.Assertions.assertFalse(categoryFile.exists());
    }

    // -------------------------------------------------------------------------
    // Builder API — outputDir default value ("src/main/java")
    // -------------------------------------------------------------------------

    @Test
    void testOutputDirDefault() throws Exception {
        File defaultOutputDir = new File("src/main/java");
        File entityFile = new File(defaultOutputDir, "com/example/entity/UserOrder.java");
        try {
            EntityGenerator.builder()
                    .dataSource(dataSource)
                    .entityPackage("com.example.entity")
                    // intentionally NOT calling .outputDir(...)
                    .generate("user_order");

            org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
            String content = readFile(entityFile);
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.entity;"));
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("public class UserOrder {"));
        } finally {
            // Clean up generated file and empty parent directories
            Files.deleteIfExists(entityFile.toPath());
            File entityDir = entityFile.getParentFile(); // entity
            File exampleDir = entityDir.getParentFile(); // example
            File comDir = exampleDir.getParentFile(); // com
            tryDeleteIfEmpty(entityDir);
            tryDeleteIfEmpty(exampleDir);
            tryDeleteIfEmpty(comDir);
        }
    }

    private void assertEntityContent(String content) {
        // Package declaration
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.entity;"));

        // Imports
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Column;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Entity;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.GeneratedValue;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.GenerationType;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Id;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Table;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import java.math.BigDecimal;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import java.time.LocalDateTime;"));

        // Lombok annotations (default useLombok=true via static generate())
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.Data;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.experimental.Accessors;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Data"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Accessors(chain = true)"));

        // Class-level annotations
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Entity"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Table(name = \"user_order\")"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public class UserOrder {"));

        // id field — primary key, auto-increment, not-null
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Id"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Column(name = \"id\", nullable = false)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private Long id;"));

        // order_number — not-null
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("@Column(name = \"order_number\", nullable = false)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private String orderNumber;"));

        // amount — nullable
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Column(name = \"amount\")"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private BigDecimal amount;"));

        // created_at — nullable
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Column(name = \"created_at\")"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private LocalDateTime createdAt;"));
    }

    // -------------------------------------------------------------------------
    // Builder API — repository generation
    // -------------------------------------------------------------------------

    @Test
    void testRepositoryGenerated() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        File repoFile = new File(outputDir, "com/example/repository/UserOrderRepository.java");
        org.junit.jupiter.api.Assertions.assertTrue(repoFile.exists());

        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.repository;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import com.example.entity.UserOrder;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.data.jpa.repository.JpaRepository;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import io.github.jsbxyyx.jpadsl.JpaUpdateExecutor;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import io.github.jsbxyyx.jpadsl.JpaDeleteExecutor;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.stereotype.Repository;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Repository"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public interface UserOrderRepository extends JpaRepository<UserOrder, Long>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("JpaSpecificationExecutor<UserOrder>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("JpaUpdateExecutor<UserOrder>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("JpaDeleteExecutor<UserOrder>"));
    }

    @Test
    void testRepositorySkippedWhenPackageNull() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                // repositoryPackage not set — defaults to null
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        File repoFile = new File(outputDir, "com/example/repository/UserOrderRepository.java");
        org.junit.jupiter.api.Assertions.assertFalse(repoFile.exists());
    }

    @Test
    void testRepositorySkippedWhenPackageEmpty() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("")
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        File repoFile = new File(outputDir, "com/example/repository/UserOrderRepository.java");
        org.junit.jupiter.api.Assertions.assertFalse(repoFile.exists());
    }

    @Test
    void testRepositorySkipIfExistsByDefault() throws Exception {
        File repoDir = new File(outputDir, "com/example/repository");
        repoDir.mkdirs();
        File repoFile = new File(repoDir, "UserOrderRepository.java");
        Files.write(repoFile.toPath(), "original content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                // repositoryOverride defaults to false
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertEquals("original content", content);
    }

    @Test
    void testRepositoryOverwriteWhenOverrideTrue() throws Exception {
        File repoDir = new File(outputDir, "com/example/repository");
        repoDir.mkdirs();
        File repoFile = new File(repoDir, "UserOrderRepository.java");
        Files.write(repoFile.toPath(), "original content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public interface UserOrderRepository"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("original content"));
    }

    @Test
    void testRepositoryWithTrimPrefix() throws Exception {
        try (java.sql.Connection conn = dataSource.getConnection();
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_product");
            stmt.execute("CREATE TABLE t_product (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
        }

        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .trimPrefix("t_")
                .generate("t_product");

        File repoFile = new File(outputDir, "com/example/repository/ProductRepository.java");
        org.junit.jupiter.api.Assertions.assertTrue(repoFile.exists());

        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import com.example.entity.Product;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public interface ProductRepository extends JpaRepository<Product, Long>"));
    }

    // -------------------------------------------------------------------------
    // Column comment -> Javadoc on entity fields
    // -------------------------------------------------------------------------

    @Test
    void testColumnCommentsGeneratedAsJavadoc() throws Exception {
        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        // Javadoc comments should appear for all commented columns
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("/**"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("     * Primary key ID"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("     * Order number"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("     * Order amount"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("     * Creation time"));
    }

    // -------------------------------------------------------------------------
    // Naming helpers
    // -------------------------------------------------------------------------

    @Test
    void testToPascalCase() {
        org.junit.jupiter.api.Assertions.assertEquals("UserOrder", EntityGenerator.toPascalCase("user_order"));
        org.junit.jupiter.api.Assertions.assertEquals("User", EntityGenerator.toPascalCase("user"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "OrderItemDetail", EntityGenerator.toPascalCase("order_item_detail"));
    }

    @Test
    void testToCamelCase() {
        org.junit.jupiter.api.Assertions.assertEquals("createdAt", EntityGenerator.toCamelCase("created_at"));
        org.junit.jupiter.api.Assertions.assertEquals("id", EntityGenerator.toCamelCase("id"));
        org.junit.jupiter.api.Assertions.assertEquals("orderNumber", EntityGenerator.toCamelCase("order_number"));
    }

    @Test
    void testToJavaType() {
        org.junit.jupiter.api.Assertions.assertEquals("String", EntityGenerator.toJavaType("VARCHAR"));
        org.junit.jupiter.api.Assertions.assertEquals("Long", EntityGenerator.toJavaType("BIGINT"));
        org.junit.jupiter.api.Assertions.assertEquals("Integer", EntityGenerator.toJavaType("INTEGER"));
        org.junit.jupiter.api.Assertions.assertEquals("Short", EntityGenerator.toJavaType("SMALLINT"));
        org.junit.jupiter.api.Assertions.assertEquals("java.math.BigDecimal", EntityGenerator.toJavaType("DECIMAL"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "java.time.LocalDateTime", EntityGenerator.toJavaType("TIMESTAMP"));
        org.junit.jupiter.api.Assertions.assertEquals("java.time.LocalDate", EntityGenerator.toJavaType("DATE"));
        org.junit.jupiter.api.Assertions.assertEquals("java.time.LocalTime", EntityGenerator.toJavaType("TIME"));
        org.junit.jupiter.api.Assertions.assertEquals("Boolean", EntityGenerator.toJavaType("BOOLEAN"));
        org.junit.jupiter.api.Assertions.assertEquals("byte[]", EntityGenerator.toJavaType("BLOB"));
        org.junit.jupiter.api.Assertions.assertEquals("Object", EntityGenerator.toJavaType("UNKNOWN_TYPE"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(File f) throws IOException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(f.toPath());
    }

    private static void tryDeleteIfEmpty(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                dir.delete();
            }
        }
    }
}
