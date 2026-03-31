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
    // Generate-all-tables variant
    // -------------------------------------------------------------------------

    @Test
    void testGenerateAllTables() throws Exception {
        EntityGenerator.generate(dataSource, "com.example.entity", outputDir.getAbsolutePath());

        File entityFile = new File(outputDir,
                "com/example/entity/UserOrder.java");
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        assertEntityContent(content);
    }

    // -------------------------------------------------------------------------
    // Generate-specific-tables variant
    // -------------------------------------------------------------------------

    @Test
    void testGenerateSpecificTable() throws Exception {
        EntityGenerator.generate(dataSource, "com.example.entity", outputDir.getAbsolutePath(),
                "user_order");

        File entityFile = new File(outputDir,
                "com/example/entity/UserOrder.java");
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        assertEntityContent(content);
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
}
