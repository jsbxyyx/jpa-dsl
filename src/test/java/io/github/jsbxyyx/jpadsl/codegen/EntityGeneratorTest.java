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

import static org.assertj.core.api.Assertions.assertThat;

class EntityGeneratorTest {

    private static final String DB_URL = "jdbc:h2:mem:entitygen;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private SimpleDriverDataSource dataSource;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(
                DriverManager.getDriver(DB_URL), DB_URL, "sa", "");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS user_order");
            stmt.execute(
                "CREATE TABLE user_order (" +
                "  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  order_number VARCHAR(50)  NOT NULL," +
                "  amount      DECIMAL(10,2)," +
                "  created_at  TIMESTAMP" +
                ")"
            );
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
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        assertEntityContent(content);
    }

    // -------------------------------------------------------------------------
    // Generate-specific-tables variant (legacy static API)
    // -------------------------------------------------------------------------

    @Test
    void testGenerateSpecificTable() throws Exception {
        EntityGenerator.generate(dataSource, "com.example.entity", outputDir.getAbsolutePath(),
                "user_order");

        File entityFile = new File(outputDir, "com/example/entity/UserOrder.java");
        assertThat(entityFile).exists();

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
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        // Lombok annotations present
        assertThat(content).contains("import lombok.Data;");
        assertThat(content).contains("import lombok.experimental.Accessors;");
        assertThat(content).contains("@Data");
        assertThat(content).contains("@Accessors(chain = true)");

        // No getter/setter methods generated
        assertThat(content).doesNotContain("public Long getId()");
        assertThat(content).doesNotContain("public UserOrder setId(");
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
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        // No Lombok annotations
        assertThat(content).doesNotContain("@Data");
        assertThat(content).doesNotContain("@Accessors");
        assertThat(content).doesNotContain("import lombok");

        // Standard getter methods
        assertThat(content).contains("public Long getId()");
        assertThat(content).contains("public String getOrderNumber()");

        // Chain setter methods (return this)
        assertThat(content).contains("public UserOrder setId(Long id) {");
        assertThat(content).contains("return this;");
        assertThat(content).contains("public UserOrder setOrderNumber(String orderNumber) {");
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
            stmt.execute("CREATE TABLE sys_config (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, cfg_key VARCHAR(50))");
        }

        EntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .trimPrefix("t_", "sys_")
                .generate("t_user", "sys_config");

        // t_user -> User
        File userFile = new File(outputDir, "com/example/entity/User.java");
        assertThat(userFile).exists();
        String userContent = readFile(userFile);
        assertThat(userContent).contains("public class User {");
        assertThat(userContent).contains("@Table(name = \"t_user\")");

        // sys_config -> Config
        File configFile = new File(outputDir, "com/example/entity/Config.java");
        assertThat(configFile).exists();
        String configContent = readFile(configFile);
        assertThat(configContent).contains("public class Config {");
        assertThat(configContent).contains("@Table(name = \"sys_config\")");
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

        assertThat(entityFile).exists();
        assertThat(productFile).doesNotExist();
        assertThat(categoryFile).doesNotExist();
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

            assertThat(entityFile).exists();
            String content = readFile(entityFile);
            assertThat(content).contains("package com.example.entity;");
            assertThat(content).contains("public class UserOrder {");
        } finally {
            // Clean up generated file and empty parent directories
            Files.deleteIfExists(entityFile.toPath());
            File entityDir = entityFile.getParentFile();           // entity
            File exampleDir = entityDir.getParentFile();           // example
            File comDir = exampleDir.getParentFile();              // com
            tryDeleteIfEmpty(entityDir);
            tryDeleteIfEmpty(exampleDir);
            tryDeleteIfEmpty(comDir);
        }
    }

    private void assertEntityContent(String content) {
        // Package declaration
        assertThat(content).contains("package com.example.entity;");

        // Imports
        assertThat(content).contains("import jakarta.persistence.Column;");
        assertThat(content).contains("import jakarta.persistence.Entity;");
        assertThat(content).contains("import jakarta.persistence.GeneratedValue;");
        assertThat(content).contains("import jakarta.persistence.GenerationType;");
        assertThat(content).contains("import jakarta.persistence.Id;");
        assertThat(content).contains("import jakarta.persistence.Table;");
        assertThat(content).contains("import java.math.BigDecimal;");
        assertThat(content).contains("import java.time.LocalDateTime;");

        // Lombok annotations (default useLombok=true via static generate())
        assertThat(content).contains("import lombok.Data;");
        assertThat(content).contains("import lombok.experimental.Accessors;");
        assertThat(content).contains("@Data");
        assertThat(content).contains("@Accessors(chain = true)");

        // Class-level annotations
        assertThat(content).contains("@Entity");
        assertThat(content).contains("@Table(name = \"user_order\")");
        assertThat(content).contains("public class UserOrder {");

        // id field — primary key, auto-increment, not-null
        assertThat(content).contains("@Id");
        assertThat(content).contains("@GeneratedValue(strategy = GenerationType.IDENTITY)");
        assertThat(content).contains("@Column(name = \"id\", nullable = false)");
        assertThat(content).contains("private Long id;");

        // order_number — not-null
        assertThat(content).contains("@Column(name = \"order_number\", nullable = false)");
        assertThat(content).contains("private String orderNumber;");

        // amount — nullable
        assertThat(content).contains("@Column(name = \"amount\")");
        assertThat(content).contains("private BigDecimal amount;");

        // created_at — nullable
        assertThat(content).contains("@Column(name = \"created_at\")");
        assertThat(content).contains("private LocalDateTime createdAt;");
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
        assertThat(repoFile).exists();

        String content = readFile(repoFile);
        assertThat(content).contains("package com.example.repository;");
        assertThat(content).contains("import com.example.entity.UserOrder;");
        assertThat(content).contains("import org.springframework.data.jpa.repository.JpaRepository;");
        assertThat(content).contains("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;");
        assertThat(content).contains("import org.springframework.stereotype.Repository;");
        assertThat(content).contains("@Repository");
        assertThat(content).contains("public interface UserOrderRepository extends JpaRepository<UserOrder, Long>");
        assertThat(content).contains("JpaSpecificationExecutor<UserOrder>");
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
        assertThat(repoFile).doesNotExist();
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
        assertThat(repoFile).doesNotExist();
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
        assertThat(content).isEqualTo("original content");
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
        assertThat(content).contains("public interface UserOrderRepository");
        assertThat(content).doesNotContain("original content");
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
        assertThat(repoFile).exists();

        String content = readFile(repoFile);
        assertThat(content).contains("import com.example.entity.Product;");
        assertThat(content).contains("public interface ProductRepository extends JpaRepository<Product, Long>");
    }

    // -------------------------------------------------------------------------
    // Naming helpers
    // -------------------------------------------------------------------------

    @Test
    void testToPascalCase() {
        assertThat(EntityGenerator.toPascalCase("user_order")).isEqualTo("UserOrder");
        assertThat(EntityGenerator.toPascalCase("user")).isEqualTo("User");
        assertThat(EntityGenerator.toPascalCase("order_item_detail")).isEqualTo("OrderItemDetail");
    }

    @Test
    void testToCamelCase() {
        assertThat(EntityGenerator.toCamelCase("created_at")).isEqualTo("createdAt");
        assertThat(EntityGenerator.toCamelCase("id")).isEqualTo("id");
        assertThat(EntityGenerator.toCamelCase("order_number")).isEqualTo("orderNumber");
    }

    @Test
    void testToJavaType() {
        assertThat(EntityGenerator.toJavaType("VARCHAR")).isEqualTo("String");
        assertThat(EntityGenerator.toJavaType("BIGINT")).isEqualTo("Long");
        assertThat(EntityGenerator.toJavaType("INTEGER")).isEqualTo("Integer");
        assertThat(EntityGenerator.toJavaType("SMALLINT")).isEqualTo("Short");
        assertThat(EntityGenerator.toJavaType("DECIMAL")).isEqualTo("java.math.BigDecimal");
        assertThat(EntityGenerator.toJavaType("TIMESTAMP")).isEqualTo("java.time.LocalDateTime");
        assertThat(EntityGenerator.toJavaType("DATE")).isEqualTo("java.time.LocalDate");
        assertThat(EntityGenerator.toJavaType("TIME")).isEqualTo("java.time.LocalTime");
        assertThat(EntityGenerator.toJavaType("BOOLEAN")).isEqualTo("Boolean");
        assertThat(EntityGenerator.toJavaType("BLOB")).isEqualTo("byte[]");
        assertThat(EntityGenerator.toJavaType("UNKNOWN_TYPE")).isEqualTo("Object");
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
