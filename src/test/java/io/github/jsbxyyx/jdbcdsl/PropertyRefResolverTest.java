package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PropertyRefResolver}.
 */
class PropertyRefResolverTest {

    @Test
    void resolve_getter_returnsPropertyName() {
        PropertyRef ref = PropertyRefResolver.resolve(TUser::getUsername);
        assertThat(ref.ownerClass()).isEqualTo(TUser.class);
        assertThat(ref.propertyName()).isEqualTo("username");
    }

    @Test
    void resolve_getId_returnsId() {
        PropertyRef ref = PropertyRefResolver.resolve(TUser::getId);
        assertThat(ref.propertyName()).isEqualTo("id");
    }

    @Test
    void resolve_lambda_throwsIllegalArgumentException() {
        SFunction<TUser, String> lambda = u -> u.getUsername() + "x";
        assertThatThrownBy(() -> PropertyRefResolver.resolve(lambda))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method reference");
    }

    record SimpleRecord(String name, int count) {}

    @Test
    void resolve_recordAccessor_returnsPropertyName() {
        PropertyRef ref = PropertyRefResolver.resolve(SimpleRecord::name);
        assertThat(ref.ownerClass()).isEqualTo(SimpleRecord.class);
        assertThat(ref.propertyName()).isEqualTo("name");
    }
}
