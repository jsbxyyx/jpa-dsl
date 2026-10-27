package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.TestUserDeleteRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.User_;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.List;

@DataJpaTest
class DeleteBuilderTest {

    @Autowired
    private TestUserDeleteRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testEntityManager.flush();

        userRepository.save(new User("Alice", "alice@example.com", 30, "ACTIVE"));
        userRepository.save(new User("Bob", "bob@example.com", 25, "INACTIVE"));
        userRepository.save(new User("Charlie", null, 40, "ACTIVE"));
        testEntityManager.flush();
    }

    @Test
    void delete_withWhereCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "INACTIVE")
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                2, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("ACTIVE")),
                new java.util.HashSet<>(
                        (userRepository.findAll()).stream().map(User::getStatus).toList()));
    }

    @Test
    void delete_withMultipleConditions_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE")
                .gt(User_.age, 35)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        List<User> remaining = userRepository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining.size());
        org.junit.jupiter.api.Assertions.assertFalse(
                (remaining).stream().map(User::getName).toList().contains("Charlie"));
    }

    @Test
    void delete_noMatchingRows_returnsZero() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.name, "NonExistent")
                .build();

        int affected = userRepository.delete(spec);

        org.junit.jupiter.api.Assertions.assertEquals(0, affected);
    }

    @Test
    void delete_noWhereConditions_throwsException() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class).build();

        // Spring JPA translates IllegalStateException to InvalidDataAccessApiUsageException
        Throwable assertionThrown1 =
                org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> userRepository.delete(spec));
        org.junit.jupiter.api.Assertions.assertTrue(IllegalStateException.class.isInstance(assertionThrown1)
                || InvalidDataAccessApiUsageException.class.isInstance(assertionThrown1));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown1.getMessage().contains("WHERE condition"));
    }

    @Test
    void delete_withLikeCondition_deletesMatchingRows() {
        DeleteSpec<User> spec =
                DeleteBuilder.<User>builder(User.class).like(User_.name, "Ali").build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                2, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertFalse(
                (userRepository.findAll()).stream().map(User::getName).toList().contains("Alice"));
    }

    @Test
    void delete_withInCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .in(User_.name, List.of("Alice", "Bob"))
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(2, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                1, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("Charlie")),
                new java.util.HashSet<>(
                        (userRepository.findAll()).stream().map(User::getName).toList()));
    }

    @Test
    void delete_withBetweenCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .between(User_.age, 20, 30)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(2, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                1, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("Charlie")),
                new java.util.HashSet<>(
                        (userRepository.findAll()).stream().map(User::getName).toList()));
    }

    @Test
    void delete_withIsNullCondition_deletesMatchingRows() {
        DeleteSpec<User> spec =
                DeleteBuilder.<User>builder(User.class).isNull(User_.email).build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                2, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertFalse(
                (userRepository.findAll()).stream().map(User::getName).toList().contains("Charlie"));
    }

    // ------------------------------------------------------------------ //
    //  condition overload tests
    // ------------------------------------------------------------------ //

    @Test
    void delete_condition_trueAppliesWhereClause() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "INACTIVE", true)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                2, userRepository.findAll().size());
    }

    @Test
    void delete_condition_falseSkipsWhereClause_otherPredicateApplied() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE", false) // skipped
                .eq(User_.name, "Bob", true) // applied
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(1, affected);
        org.junit.jupiter.api.Assertions.assertFalse(
                (userRepository.findAll()).stream().map(User::getName).toList().contains("Bob"));
    }

    @Test
    void delete_condition_allFalse_noConditionRemains_throwsException() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE", false)
                .eq(User_.name, "Alice", false)
                .build();

        Throwable assertionThrown2 =
                org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> userRepository.delete(spec));
        org.junit.jupiter.api.Assertions.assertTrue(IllegalStateException.class.isInstance(assertionThrown2)
                || InvalidDataAccessApiUsageException.class.isInstance(assertionThrown2));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown2.getMessage().contains("WHERE condition"));
    }

    @Test
    void delete_notIn_condition_deletesExpectedRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .notIn(User_.name, List.of("Alice"))
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        org.junit.jupiter.api.Assertions.assertEquals(2, affected);
        org.junit.jupiter.api.Assertions.assertEquals(
                1, userRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("Alice")),
                new java.util.HashSet<>(
                        (userRepository.findAll()).stream().map(User::getName).toList()));
    }
}
