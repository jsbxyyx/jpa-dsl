package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityMetaReader}.
 */
class EntityMetaReaderTest {

    @Test
    void read_TUser_returnsCorrectTableName() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        org.junit.jupiter.api.Assertions.assertEquals("t_user", meta.getTableName());
    }

    @Test
    void read_TUser_columnMappings() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        org.junit.jupiter.api.Assertions.assertEquals("username", meta.getColumnName("username"));
        org.junit.jupiter.api.Assertions.assertEquals("email", meta.getColumnName("email"));
        org.junit.jupiter.api.Assertions.assertEquals("age", meta.getColumnName("age"));
        org.junit.jupiter.api.Assertions.assertEquals("status", meta.getColumnName("status"));
    }

    @Test
    void read_TUser_idPropertyAndColumn() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        org.junit.jupiter.api.Assertions.assertEquals("id", meta.getIdPropertyName());
        org.junit.jupiter.api.Assertions.assertEquals("id", meta.getIdColumnName());
    }

    @Test
    void read_TOrder_columnMappings() {
        EntityMeta meta = EntityMetaReader.read(TOrder.class);
        org.junit.jupiter.api.Assertions.assertEquals("t_order", meta.getTableName());
        org.junit.jupiter.api.Assertions.assertEquals("order_no", meta.getColumnName("orderNo"));
        org.junit.jupiter.api.Assertions.assertEquals("amount", meta.getColumnName("amount"));
        org.junit.jupiter.api.Assertions.assertEquals("user_id", meta.getColumnName("userId"));
    }
}
