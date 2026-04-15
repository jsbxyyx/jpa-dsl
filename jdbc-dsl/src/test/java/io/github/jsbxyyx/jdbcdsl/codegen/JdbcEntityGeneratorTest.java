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
        assertThat(content).contains("import lombok.experimental.Accessors;");
        assertThat(content).contains("@Data");
        assertThat(content).contains("@NoArgsConstructor");
        assertThat(content).contains("@Accessors(chain = true)");
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
        assertThat(content).contains("public StockAll setStockCode(String stockCode)");
        assertThat(content).contains("return this;");
        assertThat(content).contains("public Long getId()");
        assertThat(content).contains("public StockAll setId(Long id)");
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
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.InsertSpec;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.JPageable;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.SelectSpec;");
        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.UpdateSpec;");
        assertThat(content).contains("import org.springframework.stereotype.Repository;");
        assertThat(content).contains("@Repository");
        assertThat(content).contains("public class StockAllRepository {");
        // Methods
        assertThat(content).contains("public void save(StockAll entity)");
        assertThat(content).contains("public void save(InsertSpec<StockAll> spec, StockAll entity)");
        assertThat(content).contains("public void saveNonNull(StockAll entity)");
        assertThat(content).contains("public int updateById(StockAll entity)");
        assertThat(content).contains("public int update(UpdateSpec<StockAll> spec)");
        assertThat(content).contains("public int deleteById(Long id)");
        assertThat(content).contains("public int delete(DeleteSpec<StockAll> spec)");
        assertThat(content).contains("public <R> List<R> list(SelectSpec<StockAll, R> spec)");
        assertThat(content).contains("public <R> List<R> list(SelectSpec<StockAll, R> spec, JPageable<StockAll> pageable)");
        assertThat(content).contains("public <R> R findOne(SelectSpec<StockAll, R> spec)");
        assertThat(content).contains("public <R> R findOne(SelectSpec<StockAll, R> spec, JPageable<StockAll> pageable)");
        assertThat(content).contains("public <R> Page<R> page(SelectSpec<StockAll, R> spec, JPageable<StockAll> pageable)");
        // Delegates to executor (no hard-coded SQL)
        assertThat(content).contains("jdbcDslExecutor.save(entity)");
        assertThat(content).contains("jdbcDslExecutor.save(spec, entity)");
        assertThat(content).contains("jdbcDslExecutor.saveNonNull(entity)");
        assertThat(content).contains("jdbcDslExecutor.updateById(entity)");
        assertThat(content).contains("jdbcDslExecutor.deleteById(StockAll.class, id)");
        assertThat(content).contains("jdbcDslExecutor.executeUpdate(spec)");
        assertThat(content).contains("jdbcDslExecutor.executeDelete(spec)");
        assertThat(content).contains("jdbcDslExecutor.select(spec)");
        assertThat(content).contains("jdbcDslExecutor.select(spec, pageable)");
        assertThat(content).contains("jdbcDslExecutor.findOne(spec)");
        assertThat(content).contains("jdbcDslExecutor.findOne(spec, pageable)");
        assertThat(content).contains("jdbcDslExecutor.selectPage(spec, pageable)");
        // No hard-coded table/column names in SQL strings
        assertThat(content).doesNotContain("INSERT INTO");
        assertThat(content).doesNotContain("UPDATE stock_all");
        assertThat(content).doesNotContain("DELETE FROM");
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
    // Bug-fix tests
    // -------------------------------------------------------------------------

    /**
     * The generated repository must delegate save/updateById/deleteById to
     * {@link io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor}. No SQL strings, getter calls, or
     * addValue calls should appear in the generated source — those details are handled by the
     * executor using entity metadata at runtime.
     */
    @Test
    void repository_getterNamesUseCamelCaseFieldName() throws Exception {
        // stock_all has stock_code, stock_name columns → fields stockCode, stockName
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        String content = readFile(repoFile);

        // Repository delegates to executor — no hard-coded getter calls or addValue calls
        assertThat(content).contains("jdbcDslExecutor.save(entity)");
        assertThat(content).contains("jdbcDslExecutor.updateById(entity)");
        assertThat(content).contains("jdbcDslExecutor.deleteById(StockAll.class, id)");
        // No hardcoded SQL or column strings
        assertThat(content).doesNotContain("getStockCode()");
        assertThat(content).doesNotContain("getStockName()");
        assertThat(content).doesNotContain("addValue(\"stockCode\"");
        assertThat(content).doesNotContain("addValue(\"stockName\"");
        assertThat(content).doesNotContain("INSERT INTO");
        assertThat(content).doesNotContain("UPDATE stock_all");
        assertThat(content).doesNotContain("DELETE FROM");
    }

    /**
     * The generated repository delegates to {@link io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor},
     * which uses entity annotations at runtime to decide whether to include the PK in INSERT.
     * For a non-auto-increment PK the executor will include it; the generated source only needs
     * to contain the correct executor delegation calls.
     */
    @Test
    void repository_nonAutoIncrementPk_isIncludedInInsert() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_user");
            stmt.execute(
                "CREATE TABLE t_user (" +
                "  id         BIGINT       NOT NULL," +   // NOT auto-increment → business key
                "  username   VARCHAR(50)  NOT NULL," +
                "  created_at DATETIME     NOT NULL," +
                "  PRIMARY KEY (id)" +
                ")"
            );
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_user");

        File repoFile = new File(outputDir, "com/example/repository/TUserRepository.java");
        String content = readFile(repoFile);

        // Repository delegates to executor — no hard-coded SQL
        assertThat(content).contains("jdbcDslExecutor.save(entity)");
        assertThat(content).contains("jdbcDslExecutor.updateById(entity)");
        assertThat(content).contains("jdbcDslExecutor.deleteById(TUser.class, id)");
        // No hard-coded SQL strings
        assertThat(content).doesNotContain("INSERT INTO");
        assertThat(content).doesNotContain("UPDATE t_user");
        assertThat(content).doesNotContain("DELETE FROM");
        // No KeyHolder — executor handles identity pk only
        assertThat(content).doesNotContain("GeneratedKeyHolder");
    }

    /**
     * The generated repository delegates to {@link io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor},
     * which uses entity annotations at runtime. For an IDENTITY pk the executor handles the
     * KeyHolder internally; the generated source must not contain any KeyHolder or hardcoded SQL.
     */
    @Test
    void repository_autoIncrementPk_isExcludedFromInsertAndUsesKeyHolder() throws Exception {
        // stock_all has AUTO_INCREMENT id
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        String content = readFile(repoFile);

        // Repository delegates to executor — executor handles IDENTITY pk and KeyHolder
        assertThat(content).contains("jdbcDslExecutor.save(entity)");
        // No hardcoded INSERT, no KeyHolder in generated source
        assertThat(content).doesNotContain("INSERT INTO");
        assertThat(content).doesNotContain("GeneratedKeyHolder");
        assertThat(content).doesNotContain("jdbcTemplate.update(sql, params, keyHolder)");
    }

    /**
     * Entity generation for an auto-increment PK column must emit
     * {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}.
     */
    @Test
    void entity_autoIncrementPk_hasGeneratedValueAnnotation() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        String content = readFile(entityFile);

        assertThat(content).contains("import jakarta.persistence.GeneratedValue;");
        assertThat(content).contains("import jakarta.persistence.GenerationType;");
        assertThat(content).contains("@GeneratedValue(strategy = GenerationType.IDENTITY)");
    }

    /**
     * Entity generation for a non-auto-increment PK must NOT emit {@code @GeneratedValue}.
     */
    @Test
    void entity_nonAutoIncrementPk_hasNoGeneratedValueAnnotation() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_biz");
            stmt.execute("CREATE TABLE t_biz (id BIGINT NOT NULL, name VARCHAR(50), PRIMARY KEY (id))");
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_biz");

        File entityFile = new File(outputDir, "com/example/entity/TBiz.java");
        String content = readFile(entityFile);

        assertThat(content).contains("@Id");
        assertThat(content).doesNotContain("@GeneratedValue");
    }

    // -------------------------------------------------------------------------
    // Column REMARKS as Javadoc and column order
    // -------------------------------------------------------------------------

    @Test
    void entity_columnWithComment_emitsJavadocBeforeField() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_comment_test");
            stmt.execute(
                "CREATE TABLE t_comment_test (" +
                "  id    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'primary key'," +
                "  name  VARCHAR(100) COMMENT 'user name'," +
                "  score INT" +
                ")"
            );
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_comment_test");

        File entityFile = new File(outputDir, "com/example/entity/TCommentTest.java");
        assertThat(entityFile).exists();

        String content = readFile(entityFile);
        // Comments on columns with REMARKS should appear as Javadoc
        assertThat(content).contains("/**");
        assertThat(content).contains("* primary key");
        assertThat(content).contains("* user name");
    }

    @Test
    void entity_columnWithoutComment_doesNotEmitEmptyJavadoc() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_comment_test");
            stmt.execute(
                "CREATE TABLE t_comment_test (" +
                "  id    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'primary key'," +
                "  name  VARCHAR(100) COMMENT 'user name'," +
                "  score INT" +
                ")"
            );
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_comment_test");

        File entityFile = new File(outputDir, "com/example/entity/TCommentTest.java");
        String content = readFile(entityFile);

        // The score column has no comment — no Javadoc block should appear immediately before it
        int scorePos = content.indexOf("private Integer score;");
        assertThat(scorePos).isGreaterThan(-1);
        String beforeScore = content.substring(Math.max(0, scorePos - 30), scorePos);
        assertThat(beforeScore).doesNotContain("*/");
    }

    @Test
    void entity_fieldOrderMatchesDatabaseColumnOrder() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_order_test");
            stmt.execute(
                "CREATE TABLE t_order_test (" +
                "  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  first_col  VARCHAR(50)," +
                "  second_col VARCHAR(50)," +
                "  third_col  INT" +
                ")"
            );
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_order_test");

        File entityFile = new File(outputDir, "com/example/entity/TOrderTest.java");
        String content = readFile(entityFile);

        // Fields must appear in CREATE TABLE column order: id, firstCol, secondCol, thirdCol
        int idPos = content.indexOf("private Long id;");
        int firstPos = content.indexOf("private String firstCol;");
        int secondPos = content.indexOf("private String secondCol;");
        int thirdPos = content.indexOf("private Integer thirdCol;");

        assertThat(idPos).isGreaterThan(-1);
        assertThat(firstPos).isGreaterThan(idPos);
        assertThat(secondPos).isGreaterThan(firstPos);
        assertThat(thirdPos).isGreaterThan(secondPos);
    }

    // -------------------------------------------------------------------------
    // Import ordering
    // -------------------------------------------------------------------------

    /**
     * java.* imports must be separated from the jakarta and lombok imports by a blank line.
     * No blank line should appear between imports within the same group.
     */
    @Test
    void entity_importOrdering_javaImportsAfterBlankLine() throws Exception {
        // stock_all has a DECIMAL column → java.math.BigDecimal import
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(true)
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        String content = readFile(entityFile);

        // jakarta.persistence.Column and jakarta.persistence.Table are in the same group —
        // they must NOT be separated by a blank line.
        int columnPos = content.indexOf("import jakarta.persistence.Column;");
        int tablePos  = content.indexOf("import jakarta.persistence.Table;");
        assertThat(columnPos).isGreaterThan(-1);
        assertThat(tablePos).isGreaterThan(-1);
        // Extract the text between those two import lines and verify no blank line
        int afterColumn = columnPos + "import jakarta.persistence.Column;".length();
        String between = content.substring(afterColumn, tablePos);
        assertThat(between).doesNotContain("\n\n");

        // java.math.BigDecimal must appear after a blank line that follows the jakarta/lombok block
        int bigDecimalPos = content.indexOf("import java.math.BigDecimal;");
        assertThat(bigDecimalPos).isGreaterThan(-1);
        // There must be an empty line immediately before the java.math.BigDecimal import
        assertThat(content.substring(0, bigDecimalPos)).endsWith("\n\n");
    }

    /**
     * In the repository, module imports (io.github.jsbxyyx.*) must appear before
     * other imports (org.springframework.*) with a blank line between them, and
     * java.util.List must appear after a blank line that follows the other-imports block.
     */
    @Test
    void repository_importOrdering_javaListAfterBlankLine() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        String content = readFile(repoFile);

        // Module imports must appear before org.springframework imports
        int modulePos = content.indexOf("import io.github.jsbxyyx.");
        int springPos = content.indexOf("import org.springframework.");
        assertThat(modulePos).isGreaterThan(-1);
        assertThat(springPos).isGreaterThan(modulePos);
        // There must be a blank line between the last module import and the first org.springframework import
        assertThat(content.substring(modulePos, springPos)).contains("\n\n");

        int listPos = content.indexOf("import java.util.List;");
        assertThat(listPos).isGreaterThan(-1);
        // There must be an empty line immediately before import java.util.List
        assertThat(content.substring(0, listPos)).endsWith("\n\n");
    }

    // -------------------------------------------------------------------------
    // New-annotation patterns (@LogicalDelete, @CreatedDate, @LastModifiedDate)
    // -------------------------------------------------------------------------

    @Test
    void entity_deletedColumn_emitsLogicalDeleteAnnotation_withTimestamps() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_soft");
            stmt.execute(
                "CREATE TABLE t_soft (" +
                "  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  name       VARCHAR(100)," +
                "  deleted    INT DEFAULT 0," +
                "  created_at TIMESTAMP," +
                "  updated_at TIMESTAMP" +
                ")"
            );
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_soft");

        File entityFile = new File(outputDir, "com/example/entity/TSoft.java");
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        assertThat(content).contains("import io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete;");
        assertThat(content).contains("@LogicalDelete");
        assertThat(content).contains("import org.springframework.data.annotation.CreatedDate;");
        assertThat(content).contains("@CreatedDate");
        assertThat(content).contains("import org.springframework.data.annotation.LastModifiedDate;");
        assertThat(content).contains("@LastModifiedDate");
    }

    @Test
    void entity_deletedColumn_emitsLogicalDeleteAnnotation() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_del");
            stmt.execute(
                "CREATE TABLE t_del (" +
                "  id      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  deleted INT DEFAULT 0" +
                ")"
            );
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_del");

        File entityFile = new File(outputDir, "com/example/entity/TDel.java");
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        assertThat(content).contains("@LogicalDelete");
    }

    @Test
    void entity_createdAtUpdatedAtColumns_emitDateAnnotations() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_time");
            stmt.execute(
                "CREATE TABLE t_time (" +
                "  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  created_at TIMESTAMP," +
                "  updated_at TIMESTAMP" +
                ")"
            );
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_time");

        File entityFile = new File(outputDir, "com/example/entity/TTime.java");
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        assertThat(content).contains("@CreatedDate");
        assertThat(content).contains("@LastModifiedDate");
    }

    @Test
    void entity_noSpecialColumns_doesNotEmitSpecialAnnotations() throws Exception {
        // stock_all has no is_deleted / created_at / updated_at columns
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("stock_all");

        File entityFile = new File(outputDir, "com/example/entity/StockAll.java");
        assertThat(entityFile).exists();
        String content = readFile(entityFile);

        assertThat(content).doesNotContain("@LogicalDelete");
        assertThat(content).doesNotContain("@CreatedDate");
        assertThat(content).doesNotContain("@LastModifiedDate");
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
