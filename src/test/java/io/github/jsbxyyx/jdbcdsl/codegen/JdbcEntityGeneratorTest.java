package io.github.jsbxyyx.jdbcdsl.codegen;

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

class JdbcEntityGeneratorTest {

    private static final String DB_URL = "jdbc:h2:mem:jdbcentitygen;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private SimpleDriverDataSource dataSource;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(
                DriverManager.getDriver(DB_URL), DB_URL, "sa", "");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS stock_all");
            stmt.execute(
                "CREATE TABLE stock_all (" +
                "  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  stock_code VARCHAR(20)  NOT NULL," +
                "  stock_name VARCHAR(100)," +
                "  price      DECIMAL(10,4)," +
                "  status     VARCHAR(20)" +
                ")"
            );
        }

        outputDir = Files.createTempDirectory("jdbc-entity-gen-test").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(outputDir);
    }

    // -------------------------------------------------------------------------
    // Entity generation — Lombok mode (default true)
    // -------------------------------------------------------------------------

    @Test
    void entityWithLombok_containsDataAnnotation() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(true)
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        assertThat(content).contains("package com.example.entity;");
        assertThat(content).contains("import lombok.Data;");
        assertThat(content).contains("import lombok.NoArgsConstructor;");
        assertThat(content).contains("@Data");
        assertThat(content).contains("@NoArgsConstructor");
        assertThat(content).contains("import jakarta.persistence.Table;");
        assertThat(content).contains("import jakarta.persistence.Column;");
        assertThat(content).contains("import jakarta.persistence.Id;");
        assertThat(content).contains("@Table(name = \"stock_all\")");
        assertThat(content).contains("@Id");
        assertThat(content).contains("@Column(name = \"id\"");
        assertThat(content).contains("@Column(name = \"stock_code\"");
        assertThat(content).contains("private Long id;");
        assertThat(content).contains("private String stockCode;");
        assertThat(content).contains("private String stockName;");
        assertThat(content).contains("public class StockAll {");
        // No getters/setters generated when using Lombok
        assertThat(content).doesNotContain("public String getStockCode()");
        assertThat(content).doesNotContain("public void setStockCode(");
    }

    // -------------------------------------------------------------------------
    // Entity generation — no Lombok mode
    // -------------------------------------------------------------------------

    @Test
    void entityWithoutLombok_containsGettersAndSetters() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        assertThat(content).contains("package com.example.entity;");
        // No Lombok annotations
        assertThat(content).doesNotContain("import lombok.Data;");
        assertThat(content).doesNotContain("@Data");
        // Standard bean structure
        assertThat(content).contains("public StockAll() {");
        assertThat(content).contains("public String getStockCode()");
        assertThat(content).contains("public void setStockCode(String stockCode)");
        assertThat(content).contains("public Long getId()");
        assertThat(content).contains("public void setId(Long id)");
        // JPA annotations still present
        assertThat(content).contains("@Table(name = \"stock_all\")");
        assertThat(content).contains("@Id");
    }

    // -------------------------------------------------------------------------
    // Entity generation — BigDecimal field
    // -------------------------------------------------------------------------

    @Test
    void entity_decimalColumn_mapsToBigDecimal() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        String content = readFile(entityFile);
        assertThat(content).contains("import java.math.BigDecimal;");
        assertThat(content).contains("private BigDecimal price;");
    }

    // -------------------------------------------------------------------------
    // Entity generation — trimPrefix
    // -------------------------------------------------------------------------

    @Test
    void entity_trimPrefix_removesPrefix() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_product");
            stmt.execute("CREATE TABLE t_product (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .trimPrefix("t_")
                .generate("t_product");

        File entityFile = new File(outputDir, "com/example/entity/Product.java");
        assertThat(entityFile).exists();
        String content = readFile(entityFile);
        assertThat(content).contains("public class Product {");
        assertThat(content).contains("@Table(name = \"t_product\")");
    }

    // -------------------------------------------------------------------------
    // Repository generation
    // -------------------------------------------------------------------------

    @Test
    void repository_generated_containsRequiredImportsAndMethods() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        assertThat(repoFile).exists();

        String content = readFile(repoFile);
        assertThat(content).contains("package com.example.repository;");
        assertThat(content).contains("import com.example.entity.StockAll;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.DeleteSpec;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.JPageable;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.SelectSpec;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.UpdateSpec;");
        assertThat(content).contains("import org.springframework.stereotype.Repository;");
        assertThat(content).contains("@Repository");
        assertThat(content).contains("public class StockAllRepository {");
        // Methods
        assertThat(content).contains("public void save(StockAll entity)");
        assertThat(content).contains("public int updateById(StockAll entity)");
        assertThat(content).contains("public int update(UpdateSpec<StockAll> spec)");
        assertThat(content).contains("public int deleteById(Long id)");
        assertThat(content).contains("public int delete(DeleteSpec<StockAll> spec)");
        assertThat(content).contains("public <R> List<R> list(SelectSpec<StockAll, R> spec)");
        assertThat(content).contains("public <R> Page<R> page(SelectSpec<StockAll, R> spec, JPageable<StockAll> pageable)");
        // Delegates
        assertThat(content).contains("jdbcDslExecutor.executeUpdate(spec)");
        assertThat(content).contains("jdbcDslExecutor.executeDelete(spec)");
        assertThat(content).contains("jdbcDslExecutor.select(spec)");
        assertThat(content).contains("jdbcDslExecutor.selectPage(spec, pageable)");
    }

    @Test
    void repository_skippedWhenPackageNull() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        assertThat(repoFile).doesNotExist();
    }

    @Test
    void repository_skippedIfExistsByDefault() throws Exception {
        File repoDir = new File(outputDir, "com/example/repository");
        repoDir.mkdirs();
        File repoFile = new File(repoDir, "StockAllRepository.java");
        Files.write(repoFile.toPath(), "original content".getBytes(StandardCharsets.UTF_8));

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        String content = readFile(repoFile);
        assertThat(content).isEqualTo("original content");
    }

    @Test
    void repository_overwrittenWhenOverrideTrue() throws Exception {
        File repoDir = new File(outputDir, "com/example/repository");
        repoDir.mkdirs();
        File repoFile = new File(repoDir, "StockAllRepository.java");
        Files.write(repoFile.toPath(), "original content".getBytes(StandardCharsets.UTF_8));

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        String content = readFile(repoFile);
        assertThat(content).contains("public class StockAllRepository");
        assertThat(content).doesNotContain("original content");
    }

    @Test
    void repository_withTrimPrefix_usesCorrectEntityName() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_item");
            stmt.execute("CREATE TABLE t_item (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .trimPrefix("t_")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_item");

        File repoFile = new File(outputDir, "com/example/repository/ItemRepository.java");
        assertThat(repoFile).exists();
        String content = readFile(repoFile);
        assertThat(content).contains("import com.example.entity.Item;");
        assertThat(content).contains("public class ItemRepository {");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
