package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.TestUserSelectRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.User_;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SelectBuilderTest {

    /** Minimal DTO for testing; matches constructor projection order. */
    record UserDto(Long id, String name, String email) {}

    /** DTO with just id + name (two fields). */
    record UserIdNameDto(Long id, String name) {}

    @Autowired
    private TestUserSelectRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testEntityManager.flush();

        userRepository.save(new User("Alice", "alice@example.com", 30, "ACTIVE"));
        userRepository.save(new User("Bob", "bob@example.com", 25, "INACTIVE"));
        userRepository.save(new User("Charlie", "charlie@example.com", 40, "ACTIVE"));
        userRepository.save(new User("David", "david@example.com", 35, "ACTIVE"));
        userRepository.save(new User("Eve", null, 22, "INACTIVE"));
        testEntityManager.flush();
    }

    // ------------------------------------------------------------------ //
    //  select — basic DTO projection
    // ------------------------------------------------------------------ //

    @Test
    void select_shouldReturnDtoList_noFilter() {
        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .mapTo(UserIdNameDto.class);

        List<UserIdNameDto> result = userRepository.select(spec);

        assertThat(result).hasSize(5);
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.id()).isNotNull();
            assertThat(dto.name()).isNotBlank();
        });
    }

    @Test
    void select_shouldReturnDtoList_withEqFilter() {
        Specification<User> where = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .build();

        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .where(where)
                .mapTo(UserIdNameDto.class);

        List<UserIdNameDto> result = userRepository.select(spec);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserIdNameDto::name)
                .containsExactlyInAnyOrder("Alice", "Charlie", "David");
    }

    @Test
    void select_shouldReturnDtoList_withLikeFilter() {
        Specification<User> where = SpecificationBuilder.<User>builder()
                .like(User_.name, "li")
                .build();

        SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name, User_.email)
                .where(where)
                .mapTo(UserDto.class);

        List<UserDto> result = userRepository.select(spec);

        // "Alice" and "Charlie" both contain "li"
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::name)
                .containsExactlyInAnyOrder("Alice", "Charlie");
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.id()).isNotNull();
            assertThat(dto.email()).isNotNull();
        });
    }

    @Test
    void select_shouldMapFieldsCorrectly() {
        Specification<User> where = SpecificationBuilder.<User>builder()
                .eq(User_.name, "Alice")
                .build();

        SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name, User_.email)
                .where(where)
                .mapTo(UserDto.class);

        List<UserDto> result = userRepository.select(spec);

        assertThat(result).hasSize(1);
        UserDto dto = result.get(0);
        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
    }

    // ------------------------------------------------------------------ //
    //  selectPage — pagination + count + sort
    // ------------------------------------------------------------------ //

    @Test
    void selectPage_shouldApplyPaginationAndCount() {
        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page = userRepository.selectPage(spec, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void selectPage_shouldApplyPaginationAndCountAndSort() {
        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page = userRepository.selectPage(
                spec, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(5);
        // Sorted DESC by name: Eve, David, Charlie, Bob, Alice
        assertThat(page.getContent()).extracting(UserIdNameDto::name)
                .containsExactly("Eve", "David", "Charlie", "Bob", "Alice");
    }

    @Test
    void selectPage_secondPage_shouldReturnCorrectContent() {
        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page = userRepository.selectPage(
                spec, PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "name")));

        // First page (page 0, size 2, ASC): Alice, Bob
        // Second page (page 1, size 2, ASC): Charlie, David
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).extracting(UserIdNameDto::name)
                .containsExactly("Charlie", "David");
    }

    @Test
    void selectPage_withWhereFilter_shouldApplyCountCorrectly() {
        Specification<User> where = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .build();

        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .where(where)
                .mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page = userRepository.selectPage(
                spec, PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(UserIdNameDto::name)
                .containsExactly("Alice", "Charlie");
    }

    // ------------------------------------------------------------------ //
    //  SelectSpec validation
    // ------------------------------------------------------------------ //

    @Test
    void selectSpec_noAttrs_shouldThrowException() {
        assertThatThrownBy(() ->
                SelectBuilder.from(User.class)
                        .mapTo(UserDto.class)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select");
    }

    @Test
    void selectSpec_nullDtoClass_shouldThrowException() {
        assertThatThrownBy(() ->
                SelectBuilder.from(User.class)
                        .select(User_.id, User_.name)
                        .mapTo(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dtoClass");
    }

    @Test
    void selectBuilder_nullEntityClass_shouldThrowException() {
        assertThatThrownBy(() -> SelectBuilder.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityClass");
    }
}
