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

class JdbcEntityGeneratorTest {

    private static final String DB_URL = "jdbc:h2:mem:jdbcentitygen;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private SimpleDriverDataSource dataSource;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(DriverManager.getDriver(DB_URL), DB_URL, "sa", "");

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS stock_all");
            stmt.execute("CREATE TABLE stock_all (" + "  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  stock_code VARCHAR(20)  NOT NULL,"
                    + "  stock_name VARCHAR(100),"
                    + "  price      DECIMAL(10,4),"
                    + "  status     VARCHAR(20)"
                    + ")");
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
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());

        String content = readFile(entityFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.entity;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.Data;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.NoArgsConstructor;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import lombok.experimental.Accessors;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Data"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@NoArgsConstructor"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Accessors(chain = true)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Table;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Column;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.Id;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Table(name = \"stock_all\")"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Id"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Column(name = \"id\""));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Column(name = \"stock_code\""));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private Long id;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private String stockCode;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private String stockName;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public class StockAll {"));
        // No getters/setters generated when using Lombok
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("public String getStockCode()"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("public void setStockCode("));
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
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());

        String content = readFile(entityFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.entity;"));
        // No Lombok annotations
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("import lombok.Data;"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@Data"));
        // Standard bean structure
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public StockAll() {"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public String getStockCode()"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public StockAll setStockCode(String stockCode)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("return this;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public Long getId()"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public StockAll setId(Long id)"));
        // JPA annotations still present
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Table(name = \"stock_all\")"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Id"));
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
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import java.math.BigDecimal;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("private BigDecimal price;"));
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
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public class Product {"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Table(name = \"t_product\")"));
    }

    // -------------------------------------------------------------------------
    // Repository generation
    // -------------------------------------------------------------------------

    @Test
    void repository_generated_bindsEntityAndIdToGenericBaseRepository() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        org.junit.jupiter.api.Assertions.assertTrue(repoFile.exists());

        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("package com.example.repository;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import com.example.entity.StockAll;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import io.github.jsbxyyx.jdbcdsl.JdbcDslRepository;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.beans.factory.annotation.Autowired;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.stereotype.Repository;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Repository"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public class StockAllRepository extends JdbcDslRepository<StockAll, Long> {"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public StockAllRepository(JdbcDslExecutor jdbcDslExecutor)"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Autowired"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("super(StockAll.class, jdbcDslExecutor);"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("public void save(StockAll entity)"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("INSERT INTO"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("UPDATE stock_all"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("DELETE FROM"));
    }

    @Test
    void repository_skippedWhenPackageNull() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        org.junit.jupiter.api.Assertions.assertFalse(repoFile.exists());
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
        org.junit.jupiter.api.Assertions.assertEquals("original content", content);
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
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("public class StockAllRepository"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("original content"));
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
        org.junit.jupiter.api.Assertions.assertTrue(repoFile.exists());
        String content = readFile(repoFile);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import com.example.entity.Item;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("public class ItemRepository extends JdbcDslRepository<Item, Long> {"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("super(Item.class, jdbcDslExecutor);"));
    }

    // -------------------------------------------------------------------------
    // Bug-fix tests
    // -------------------------------------------------------------------------

    /**
     * Repository generation must not duplicate field-specific access or SQL. Those details are
     * resolved by the generic base repository and executor at runtime.
     */
    @Test
    void repository_containsNoFieldSpecificAccessOrSql() throws Exception {
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .repositoryPackage("com.example.repository")
                .repositoryOverride(true)
                .outputDir(outputDir.getAbsolutePath())
                .generate("stock_all");

        File repoFile = new File(outputDir, "com/example/repository/StockAllRepository.java");
        String content = readFile(repoFile);

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("extends JdbcDslRepository<StockAll, Long>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("super(StockAll.class, jdbcDslExecutor);"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("getStockCode()"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("getStockName()"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("addValue(\"stockCode\""));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("addValue(\"stockName\""));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("INSERT INTO"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("UPDATE stock_all"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("DELETE FROM"));
    }

    /**
     * A non-auto-increment primary key must still produce the correct repository identifier type.
     * Insert details remain in the shared executor rather than the generated subclass.
     */
    @Test
    void repository_nonAutoIncrementPk_isIncludedInInsert() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_user");
            stmt.execute("CREATE TABLE t_user (" + "  id         BIGINT       NOT NULL,"
                    + // NOT auto-increment → business key
                    "  username   VARCHAR(50)  NOT NULL,"
                    + "  created_at DATETIME     NOT NULL,"
                    + "  PRIMARY KEY (id)"
                    + ")");
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

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("extends JdbcDslRepository<TUser, Long>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("super(TUser.class, jdbcDslExecutor);"));
        // No hard-coded SQL strings
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("INSERT INTO"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("UPDATE t_user"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("DELETE FROM"));
        // No KeyHolder — executor handles identity pk only
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("GeneratedKeyHolder"));
    }

    /**
     * For an IDENTITY primary key, generated repository code remains a thin type binding;
     * generated-key handling stays inside the shared executor.
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

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("extends JdbcDslRepository<StockAll, Long>"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("super(StockAll.class, jdbcDslExecutor);"));
        // No hardcoded INSERT, no KeyHolder in generated source
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("INSERT INTO"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("GeneratedKeyHolder"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("jdbcTemplate.update(sql, params, keyHolder)"));
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

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.GeneratedValue;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("import jakarta.persistence.GenerationType;"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"));
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

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@Id"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@GeneratedValue"));
    }

    // -------------------------------------------------------------------------
    // Column REMARKS as Javadoc and column order
    // -------------------------------------------------------------------------

    @Test
    void entity_columnWithComment_emitsJavadocBeforeField() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_comment_test");
            stmt.execute("CREATE TABLE t_comment_test ("
                    + "  id    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'primary key',"
                    + "  name  VARCHAR(100) COMMENT 'user name',"
                    + "  score INT"
                    + ")");
        }

        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .generate("t_comment_test");

        File entityFile = new File(outputDir, "com/example/entity/TCommentTest.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());

        String content = readFile(entityFile);
        // Comments on columns with REMARKS should appear as Javadoc
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("/**"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("* primary key"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("* user name"));
    }

    @Test
    void entity_columnWithoutComment_doesNotEmitEmptyJavadoc() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_comment_test");
            stmt.execute("CREATE TABLE t_comment_test ("
                    + "  id    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'primary key',"
                    + "  name  VARCHAR(100) COMMENT 'user name',"
                    + "  score INT"
                    + ")");
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
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) scorePos).compareTo(-1) > 0);
        String beforeScore = content.substring(Math.max(0, scorePos - 30), scorePos);
        org.junit.jupiter.api.Assertions.assertFalse(beforeScore.contains("*/"));
    }

    @Test
    void entity_fieldOrderMatchesDatabaseColumnOrder() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_order_test");
            stmt.execute(
                    "CREATE TABLE t_order_test (" + "  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                            + "  first_col  VARCHAR(50),"
                            + "  second_col VARCHAR(50),"
                            + "  third_col  INT"
                            + ")");
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

        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) idPos).compareTo(-1) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) firstPos).compareTo(idPos) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) secondPos).compareTo(firstPos) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) thirdPos).compareTo(secondPos) > 0);
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
        int tablePos = content.indexOf("import jakarta.persistence.Table;");
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) columnPos).compareTo(-1) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) tablePos).compareTo(-1) > 0);
        // Extract the text between those two import lines and verify no blank line
        int afterColumn = columnPos + "import jakarta.persistence.Column;".length();
        String between = content.substring(afterColumn, tablePos);
        org.junit.jupiter.api.Assertions.assertFalse(between.contains("\n\n"));

        // java.math.BigDecimal must appear after a blank line that follows the jakarta/lombok block
        int bigDecimalPos = content.indexOf("import java.math.BigDecimal;");
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) bigDecimalPos).compareTo(-1) > 0);
        // There must be an empty line immediately before the java.math.BigDecimal import
        org.junit.jupiter.api.Assertions.assertTrue(
                content.substring(0, bigDecimalPos).endsWith("\n\n"));
    }

    /**
     * In the repository, module imports must appear before Spring imports with a blank line
     * between the groups. The thin subclass must not need collection imports.
     */
    @Test
    void repository_importOrdering_moduleBeforeSpring() throws Exception {
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
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) modulePos).compareTo(-1) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(((java.lang.Comparable) springPos).compareTo(modulePos) > 0);
        // There must be a blank line between the last module import and the first org.springframework import
        org.junit.jupiter.api.Assertions.assertTrue(
                content.substring(modulePos, springPos).contains("\n\n"));

        org.junit.jupiter.api.Assertions.assertFalse(content.contains("import java.util.List;"));
    }

    // -------------------------------------------------------------------------
    // New-annotation patterns (@LogicalDelete, @CreatedDate, @LastModifiedDate)
    // -------------------------------------------------------------------------

    @Test
    void entity_deletedColumn_emitsLogicalDeleteAnnotation_withTimestamps() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_soft");
            stmt.execute("CREATE TABLE t_soft (" + "  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  name       VARCHAR(100),"
                    + "  deleted    INT DEFAULT 0,"
                    + "  created_at TIMESTAMP,"
                    + "  updated_at TIMESTAMP"
                    + ")");
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_soft");

        File entityFile = new File(outputDir, "com/example/entity/TSoft.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import io.github.jsbxyyx.jdbcdsl.annotation.LogicalDelete;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@LogicalDelete"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.data.annotation.CreatedDate;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@CreatedDate"));
        org.junit.jupiter.api.Assertions.assertTrue(
                content.contains("import org.springframework.data.annotation.LastModifiedDate;"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@LastModifiedDate"));
    }

    @Test
    void entity_deletedColumn_emitsLogicalDeleteAnnotation() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_del");
            stmt.execute("CREATE TABLE t_del (" + "  id      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  deleted INT DEFAULT 0"
                    + ")");
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_del");

        File entityFile = new File(outputDir, "com/example/entity/TDel.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@LogicalDelete"));
    }

    @Test
    void entity_createdAtUpdatedAtColumns_emitDateAnnotations() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_time");
            stmt.execute("CREATE TABLE t_time (" + "  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  created_at TIMESTAMP,"
                    + "  updated_at TIMESTAMP"
                    + ")");
        }
        JdbcEntityGenerator.builder()
                .dataSource(dataSource)
                .entityPackage("com.example.entity")
                .outputDir(outputDir.getAbsolutePath())
                .useLombok(false)
                .generate("t_time");

        File entityFile = new File(outputDir, "com/example/entity/TTime.java");
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@CreatedDate"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("@LastModifiedDate"));
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
        org.junit.jupiter.api.Assertions.assertTrue(entityFile.exists());
        String content = readFile(entityFile);

        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@LogicalDelete"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@CreatedDate"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("@LastModifiedDate"));
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
