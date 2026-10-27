package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.NoTableAnnotationEntity;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TProductNoAnnotation;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NamingStrategy}, {@link DefaultNamingStrategy},
 * {@link SnakeCaseNamingStrategy}, and their integration with {@link EntityMetaReader}.
 */
class NamingStrategyTest {

    @AfterEach
    void resetToDefault() {
        // Always restore default strategy so other tests are not affected.
        JdbcDslConfig.setNamingStrategy(DefaultNamingStrategy.INSTANCE);
    }

    // ------------------------------------------------------------------ //
    //  SnakeCaseNamingStrategy.toSnakeCase unit tests
    // ------------------------------------------------------------------ //

    @Test
    void snakeCase_simpleProperty() {
        org.junit.jupiter.api.Assertions.assertEquals("user_id", SnakeCaseNamingStrategy.toSnakeCase("userId"));
    }

    @Test
    void snakeCase_multiWord() {
        org.junit.jupiter.api.Assertions.assertEquals("created_at", SnakeCaseNamingStrategy.toSnakeCase("createdAt"));
        org.junit.jupiter.api.Assertions.assertEquals("order_no", SnakeCaseNamingStrategy.toSnakeCase("orderNo"));
        org.junit.jupiter.api.Assertions.assertEquals("unit_price", SnakeCaseNamingStrategy.toSnakeCase("unitPrice"));
        org.junit.jupiter.api.Assertions.assertEquals("stock_qty", SnakeCaseNamingStrategy.toSnakeCase("stockQty"));
    }

    @Test
    void snakeCase_alreadyLowercase() {
        org.junit.jupiter.api.Assertions.assertEquals("id", SnakeCaseNamingStrategy.toSnakeCase("id"));
        org.junit.jupiter.api.Assertions.assertEquals("status", SnakeCaseNamingStrategy.toSnakeCase("status"));
    }

    @Test
    void snakeCase_className() {
        org.junit.jupiter.api.Assertions.assertEquals("user_info", SnakeCaseNamingStrategy.toSnakeCase("UserInfo"));
        org.junit.jupiter.api.Assertions.assertEquals("t_product", SnakeCaseNamingStrategy.toSnakeCase("TProduct"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "no_table_annotation_entity", SnakeCaseNamingStrategy.toSnakeCase("NoTableAnnotationEntity"));
    }

    @Test
    void snakeCase_nullAndEmpty() {
        org.junit.jupiter.api.Assertions.assertNull(SnakeCaseNamingStrategy.toSnakeCase(null));
        org.junit.jupiter.api.Assertions.assertTrue(
                SnakeCaseNamingStrategy.toSnakeCase("").isEmpty());
    }

    // ------------------------------------------------------------------ //
    //  DefaultNamingStrategy: existing entities remain unchanged
    // ------------------------------------------------------------------ //

    @Test
    void defaultStrategy_existingEntities_unaffected() {
        // Default strategy is already active; explicit @Column/@Table annotations win anyway.
        EntityMeta userMeta = EntityMetaReader.read(TUser.class);
        org.junit.jupiter.api.Assertions.assertEquals("t_user", userMeta.getTableName());
        org.junit.jupiter.api.Assertions.assertEquals("username", userMeta.getColumnName("username"));

        EntityMeta orderMeta = EntityMetaReader.read(TOrder.class);
        org.junit.jupiter.api.Assertions.assertEquals("order_no", orderMeta.getColumnName("orderNo")); // from @Column
        org.junit.jupiter.api.Assertions.assertEquals("user_id", orderMeta.getColumnName("userId")); // from @Column
    }

    // ------------------------------------------------------------------ //
    //  SnakeCaseNamingStrategy + EntityMetaReader integration
    // ------------------------------------------------------------------ //

    @Test
    void snakeCaseStrategy_unannotatedFields_convertedToSnakeCase() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        EntityMeta meta = EntityMetaReader.read(TProductNoAnnotation.class);

        // Explicit @Table name is preserved as-is.
        org.junit.jupiter.api.Assertions.assertEquals("t_product", meta.getTableName());

        // Fields without @Column: naming strategy applied.
        org.junit.jupiter.api.Assertions.assertEquals("product_name", meta.getColumnName("productName"));
        org.junit.jupiter.api.Assertions.assertEquals("unit_price", meta.getColumnName("unitPrice"));
        org.junit.jupiter.api.Assertions.assertEquals("stock_qty", meta.getColumnName("stockQty"));
        org.junit.jupiter.api.Assertions.assertEquals("id", meta.getColumnName("id")); // single word, no change
    }

    @Test
    void snakeCaseStrategy_noTableAnnotation_tableNameConverted() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        EntityMeta meta = EntityMetaReader.read(NoTableAnnotationEntity.class);
        org.junit.jupiter.api.Assertions.assertEquals("no_table_annotation_entity", meta.getTableName());
        org.junit.jupiter.api.Assertions.assertEquals("display_name", meta.getColumnName("displayName"));
    }

    @Test
    void snakeCaseStrategy_explicitColumnAnnotation_notOverridden() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        // TOrder has explicit @Column annotations; they must not be overridden.
        EntityMeta meta = EntityMetaReader.read(TOrder.class);
        org.junit.jupiter.api.Assertions.assertEquals(
                "order_no", meta.getColumnName("orderNo")); // @Column(name="order_no")
        org.junit.jupiter.api.Assertions.assertEquals(
                "user_id", meta.getColumnName("userId")); // @Column(name="user_id")
        org.junit.jupiter.api.Assertions.assertEquals("t_order", meta.getTableName()); // @Table(name="t_order")
    }

    @Test
    void switchingStrategy_cacheIsCleared() {
        // With default strategy, unannotated fields use property names.
        EntityMeta defaultMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        org.junit.jupiter.api.Assertions.assertEquals("productName", defaultMeta.getColumnName("productName"));

        // Switch to snake_case: cache is cleared automatically.
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);
        EntityMeta snakeMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        org.junit.jupiter.api.Assertions.assertEquals("product_name", snakeMeta.getColumnName("productName"));

        // Switch back: cache cleared again.
        JdbcDslConfig.setNamingStrategy(DefaultNamingStrategy.INSTANCE);
        EntityMeta restoredMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        org.junit.jupiter.api.Assertions.assertEquals("productName", restoredMeta.getColumnName("productName"));
    }

    // ------------------------------------------------------------------ //
    //  SQL rendering with snake_case strategy
    // ------------------------------------------------------------------ //

    @Test
    void snakeCaseStrategy_renderSelect_usesConvertedColumnNames() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        SelectSpec<TProductNoAnnotation, TProductNoAnnotation> spec =
                SelectBuilder.from(TProductNoAnnotation.class).mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.product_name AS productName"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.unit_price AS unitPrice"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.stock_qty AS stockQty"));
    }

    @Test
    void snakeCaseStrategy_renderSelect_whereUsesConvertedColumnName() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        SelectSpec<TProductNoAnnotation, TProductNoAnnotation> spec = SelectBuilder.from(TProductNoAnnotation.class)
                .where(w -> w.eq(TProductNoAnnotation::getProductName, "Widget"))
                .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("t.product_name = :p1"));
    }
}
