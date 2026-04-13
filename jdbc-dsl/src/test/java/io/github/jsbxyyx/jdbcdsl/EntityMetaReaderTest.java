package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import io.github.jsbxyyx.jdbcdsl.entity.TOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EntityMetaReader}.
 */
class EntityMetaReaderTest {

    @Test
    void read_TUser_returnsCorrectTableName() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        assertThat(meta.getTableName()).isEqualTo("t_user");
    }

    @Test
    void read_TUser_columnMappings() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        assertThat(meta.getColumnName("username")).isEqualTo("username");
        assertThat(meta.getColumnName("email")).isEqualTo("email");
        assertThat(meta.getColumnName("age")).isEqualTo("age");
        assertThat(meta.getColumnName("status")).isEqualTo("status");
    }

    @Test
    void read_TUser_idPropertyAndColumn() {
        EntityMeta meta = EntityMetaReader.read(TUser.class);
        assertThat(meta.getIdPropertyName()).isEqualTo("id");
        assertThat(meta.getIdColumnName()).isEqualTo("id");
    }

    @Test
    void read_TOrder_columnMappings() {
        EntityMeta meta = EntityMetaReader.read(TOrder.class);
        assertThat(meta.getTableName()).isEqualTo("t_order");
        assertThat(meta.getColumnName("orderNo")).isEqualTo("order_no");
        assertThat(meta.getColumnName("amount")).isEqualTo("amount");
        assertThat(meta.getColumnName("userId")).isEqualTo("user_id");
    }
}
