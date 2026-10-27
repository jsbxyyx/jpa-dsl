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
        SelectSpec<User, UserIdNameDto> spec =
                SelectBuilder.from(User.class).select(User_.id, User_.name).mapTo(UserIdNameDto.class);

        List<UserIdNameDto> result = userRepository.select(spec);

        org.junit.jupiter.api.Assertions.assertEquals(5, result.size());
        (result).forEach(dto -> {
            org.junit.jupiter.api.Assertions.assertNotNull(dto.id());
            org.junit.jupiter.api.Assertions.assertFalse(dto.name().isBlank());
        });
    }

    @Test
    void select_shouldReturnDtoList_withEqFilter() {
        Specification<User> where =
                SpecificationBuilder.<User>builder().eq(User_.status, "ACTIVE").build();

        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .where(where)
                .mapTo(UserIdNameDto.class);

        List<UserIdNameDto> result = userRepository.select(spec);

        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("Alice", "Charlie", "David")),
                new java.util.HashSet<>(
                        (result).stream().map(UserIdNameDto::name).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                3, (result).stream().map(UserIdNameDto::name).toList().size());
    }

    @Test
    void select_shouldReturnDtoList_withLikeFilter() {
        Specification<User> where =
                SpecificationBuilder.<User>builder().like(User_.name, "li").build();

        SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name, User_.email)
                .where(where)
                .mapTo(UserDto.class);

        List<UserDto> result = userRepository.select(spec);

        // "Alice" and "Charlie" both contain "li"
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("Alice", "Charlie")),
                new java.util.HashSet<>((result).stream().map(UserDto::name).toList()));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, (result).stream().map(UserDto::name).toList().size());
        (result).forEach(dto -> {
            org.junit.jupiter.api.Assertions.assertNotNull(dto.id());
            org.junit.jupiter.api.Assertions.assertNotNull(dto.email());
        });
    }

    @Test
    void select_shouldMapFieldsCorrectly() {
        Specification<User> where =
                SpecificationBuilder.<User>builder().eq(User_.name, "Alice").build();

        SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name, User_.email)
                .where(where)
                .mapTo(UserDto.class);

        List<UserDto> result = userRepository.select(spec);

        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        UserDto dto = result.get(0);
        org.junit.jupiter.api.Assertions.assertNotNull(dto.id());
        org.junit.jupiter.api.Assertions.assertEquals("Alice", dto.name());
        org.junit.jupiter.api.Assertions.assertEquals("alice@example.com", dto.email());
    }

    // ------------------------------------------------------------------ //
    //  selectPage — pagination + count + sort
    // ------------------------------------------------------------------ //

    @Test
    void selectPage_shouldApplyPaginationAndCount() {
        SelectSpec<User, UserIdNameDto> spec =
                SelectBuilder.from(User.class).select(User_.id, User_.name).mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page = userRepository.selectPage(spec, PageRequest.of(0, 3));

        org.junit.jupiter.api.Assertions.assertEquals(3, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(5, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getTotalPages());
    }

    @Test
    void selectPage_shouldApplyPaginationAndCountAndSort() {
        SelectSpec<User, UserIdNameDto> spec =
                SelectBuilder.from(User.class).select(User_.id, User_.name).mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page =
                userRepository.selectPage(spec, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

        org.junit.jupiter.api.Assertions.assertEquals(5, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(5, page.getTotalElements());
        // Sorted DESC by name: Eve, David, Charlie, Bob, Alice
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("Eve", "David", "Charlie", "Bob", "Alice"),
                (page.getContent()).stream().map(UserIdNameDto::name).toList());
    }

    @Test
    void selectPage_secondPage_shouldReturnCorrectContent() {
        SelectSpec<User, UserIdNameDto> spec =
                SelectBuilder.from(User.class).select(User_.id, User_.name).mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page =
                userRepository.selectPage(spec, PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "name")));

        // First page (page 0, size 2, ASC): Alice, Bob
        // Second page (page 1, size 2, ASC): Charlie, David
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(5, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("Charlie", "David"),
                (page.getContent()).stream().map(UserIdNameDto::name).toList());
    }

    @Test
    void selectPage_withWhereFilter_shouldApplyCountCorrectly() {
        Specification<User> where =
                SpecificationBuilder.<User>builder().eq(User_.status, "ACTIVE").build();

        SelectSpec<User, UserIdNameDto> spec = SelectBuilder.from(User.class)
                .select(User_.id, User_.name)
                .where(where)
                .mapTo(UserIdNameDto.class);

        Page<UserIdNameDto> page =
                userRepository.selectPage(spec, PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name")));

        org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertIterableEquals(
                java.util.Arrays.asList("Alice", "Charlie"),
                (page.getContent()).stream().map(UserIdNameDto::name).toList());
    }

    // ------------------------------------------------------------------ //
    //  SelectSpec validation
    // ------------------------------------------------------------------ //

    @Test
    void selectSpec_noAttrs_shouldThrowException() {
        Throwable assertionThrown1 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> SelectBuilder.from(User.class).mapTo(UserDto.class));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown1.getMessage().contains("select"));
    }

    @Test
    void selectSpec_nullDtoClass_shouldThrowException() {
        Throwable assertionThrown2 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> SelectBuilder.from(User.class)
                        .select(User_.id, User_.name)
                        .mapTo(null));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown2.getMessage().contains("dtoClass"));
    }

    @Test
    void selectBuilder_nullEntityClass_shouldThrowException() {
        Throwable assertionThrown3 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> SelectBuilder.from(null));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown3.getMessage().contains("entityClass"));
    }
}
