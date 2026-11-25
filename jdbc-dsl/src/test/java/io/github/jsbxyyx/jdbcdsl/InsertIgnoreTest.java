package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InsertIgnoreTest {

    @Test
    void mysqlRendersExplicitColumnsAndBoundValues() {
        var spec = InsertBuilder.into(TUser.class)
                .columns(TUser::getUsername, TUser::getEmail)
                .ignore()
                .build();
        var values = new LinkedHashMap<String, Object>();
        values.put("username", "alice");
        values.put("email", "alice@example.test");
        values.put("status", "ACTIVE");
        var rendered = new MySqlDialect().renderInsert(spec, EntityMetaReader.read(TUser.class), values);

        assertThat(spec.isIgnore()).isTrue();
        assertThat(spec.resolve(EntityMetaReader.read(TUser.class)).isIgnore()).isTrue();
        assertThat(rendered.getSql())
                .isEqualTo("INSERT IGNORE INTO t_user (username, email) VALUES (:username, :email)");
        assertThat(rendered.getParams())
                .containsOnlyKeys("username", "email")
                .containsEntry("username", "alice")
                .containsEntry("email", "alice@example.test");
    }

    @Test
    void unsupportedDialectRejectsBeforeExecutingSql() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcDslExecutor executor = new JdbcDslExecutor(jdbc, new H2Dialect());
        var spec = InsertBuilder.into(TUser.class).ignore().build();
        assertThatThrownBy(() -> executor.save(spec, new TUser("alice", null, 30, "ACTIVE")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("INSERT IGNORE");
        verifyNoInteractions(jdbc);
    }

    @Test
    void boundAndUnboundSpecsCannotAccidentallyUseTheWrongEntity() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcDslExecutor executor = new JdbcDslExecutor(jdbc, new MySqlDialect());
        TUser user = new TUser("alice", null, 30, "ACTIVE");
        var bound = InsertBuilder.of(user).columns(TUser::getUsername).ignore().build();

        assertThat(bound.getEntity()).isSameAs(user);
        assertThat(bound.resolve(EntityMetaReader.read(TUser.class)).getEntity())
                .isSameAs(user);
        assertThatThrownBy(() -> executor.save(bound, new TUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different entity");
        assertThatThrownBy(() -> executor.save(InsertBuilder.into(TUser.class).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no bound entity");
        assertThatThrownBy(() -> InsertBuilder.of(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(jdbc);
    }
}
