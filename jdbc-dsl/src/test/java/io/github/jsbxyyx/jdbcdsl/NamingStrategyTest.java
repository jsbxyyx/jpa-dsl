package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.NoTableAnnotationEntity;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TProductNoAnnotation;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("userId")).isEqualTo("user_id");
    }

    @Test
    void snakeCase_multiWord() {
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("createdAt")).isEqualTo("created_at");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("orderNo")).isEqualTo("order_no");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("unitPrice")).isEqualTo("unit_price");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("stockQty")).isEqualTo("stock_qty");
    }

    @Test
    void snakeCase_alreadyLowercase() {
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("id")).isEqualTo("id");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("status")).isEqualTo("status");
    }

    @Test
    void snakeCase_className() {
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("UserInfo")).isEqualTo("user_info");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("TProduct")).isEqualTo("t_product");
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("NoTableAnnotationEntity"))
                .isEqualTo("no_table_annotation_entity");
    }

    @Test
    void snakeCase_nullAndEmpty() {
        assertThat(SnakeCaseNamingStrategy.toSnakeCase(null)).isNull();
        assertThat(SnakeCaseNamingStrategy.toSnakeCase("")).isEmpty();
    }

    // ------------------------------------------------------------------ //
    //  DefaultNamingStrategy: existing entities remain unchanged
    // ------------------------------------------------------------------ //

    @Test
    void defaultStrategy_existingEntities_unaffected() {
        // Default strategy is already active; explicit @Column/@Table annotations win anyway.
        EntityMeta userMeta = EntityMetaReader.read(TUser.class);
        assertThat(userMeta.getTableName()).isEqualTo("t_user");
        assertThat(userMeta.getColumnName("username")).isEqualTo("username");

        EntityMeta orderMeta = EntityMetaReader.read(TOrder.class);
        assertThat(orderMeta.getColumnName("orderNo")).isEqualTo("order_no"); // from @Column
        assertThat(orderMeta.getColumnName("userId")).isEqualTo("user_id");   // from @Column
    }

    // ------------------------------------------------------------------ //
    //  SnakeCaseNamingStrategy + EntityMetaReader integration
    // ------------------------------------------------------------------ //

    @Test
    void snakeCaseStrategy_unannotatedFields_convertedToSnakeCase() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        EntityMeta meta = EntityMetaReader.read(TProductNoAnnotation.class);

        // Explicit @Table name is preserved as-is.
        assertThat(meta.getTableName()).isEqualTo("t_product");

        // Fields without @Column: naming strategy applied.
        assertThat(meta.getColumnName("productName")).isEqualTo("product_name");
        assertThat(meta.getColumnName("unitPrice")).isEqualTo("unit_price");
        assertThat(meta.getColumnName("stockQty")).isEqualTo("stock_qty");
        assertThat(meta.getColumnName("id")).isEqualTo("id"); // single word, no change
    }

    @Test
    void snakeCaseStrategy_noTableAnnotation_tableNameConverted() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        EntityMeta meta = EntityMetaReader.read(NoTableAnnotationEntity.class);
        assertThat(meta.getTableName()).isEqualTo("no_table_annotation_entity");
        assertThat(meta.getColumnName("displayName")).isEqualTo("display_name");
    }

    @Test
    void snakeCaseStrategy_explicitColumnAnnotation_notOverridden() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        // TOrder has explicit @Column annotations; they must not be overridden.
        EntityMeta meta = EntityMetaReader.read(TOrder.class);
        assertThat(meta.getColumnName("orderNo")).isEqualTo("order_no"); // @Column(name="order_no")
        assertThat(meta.getColumnName("userId")).isEqualTo("user_id");   // @Column(name="user_id")
        assertThat(meta.getTableName()).isEqualTo("t_order");            // @Table(name="t_order")
    }

    @Test
    void switchingStrategy_cacheIsCleared() {
        // With default strategy, unannotated fields use property names.
        EntityMeta defaultMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        assertThat(defaultMeta.getColumnName("productName")).isEqualTo("productName");

        // Switch to snake_case: cache is cleared automatically.
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);
        EntityMeta snakeMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        assertThat(snakeMeta.getColumnName("productName")).isEqualTo("product_name");

        // Switch back: cache cleared again.
        JdbcDslConfig.setNamingStrategy(DefaultNamingStrategy.INSTANCE);
        EntityMeta restoredMeta = EntityMetaReader.read(TProductNoAnnotation.class);
        assertThat(restoredMeta.getColumnName("productName")).isEqualTo("productName");
    }

    // ------------------------------------------------------------------ //
    //  SQL rendering with snake_case strategy
    // ------------------------------------------------------------------ //

    @Test
    void snakeCaseStrategy_renderSelect_usesConvertedColumnNames() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        SelectSpec<TProductNoAnnotation, TProductNoAnnotation> spec =
                SelectBuilder.from(TProductNoAnnotation.class)
                        .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("t.product_name AS productName");
        assertThat(rendered.getSql()).contains("t.unit_price AS unitPrice");
        assertThat(rendered.getSql()).contains("t.stock_qty AS stockQty");
    }

    @Test
    void snakeCaseStrategy_renderSelect_whereUsesConvertedColumnName() {
        JdbcDslConfig.setNamingStrategy(SnakeCaseNamingStrategy.INSTANCE);

        SelectSpec<TProductNoAnnotation, TProductNoAnnotation> spec =
                SelectBuilder.from(TProductNoAnnotation.class)
                        .where(w -> w.eq(TProductNoAnnotation::getProductName, "Widget"))
                        .mapToEntity();

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("t.product_name = :p1");
    }
}
