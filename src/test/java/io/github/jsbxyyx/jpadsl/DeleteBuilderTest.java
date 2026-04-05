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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(affected).isEqualTo(1);
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userRepository.findAll())
                .extracting(User::getStatus)
                .containsOnly("ACTIVE");
    }

    @Test
    void delete_withMultipleConditions_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE")
                .gt(User_.age, 35)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        List<User> remaining = userRepository.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(User::getName)
                .doesNotContain("Charlie");
    }

    @Test
    void delete_noMatchingRows_returnsZero() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.name, "NonExistent")
                .build();

        int affected = userRepository.delete(spec);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void delete_noWhereConditions_throwsException() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .build();

        // Spring JPA translates IllegalStateException to InvalidDataAccessApiUsageException
        assertThatThrownBy(() -> userRepository.delete(spec))
                .isInstanceOfAny(IllegalStateException.class,
                        InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("WHERE condition");
    }

    @Test
    void delete_withLikeCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .like(User_.name, "Ali")
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .doesNotContain("Alice");
    }

    @Test
    void delete_withInCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .in(User_.name, List.of("Alice", "Bob"))
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .containsOnly("Charlie");
    }

    @Test
    void delete_withBetweenCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .between(User_.age, 20, 30)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .containsOnly("Charlie");
    }

    @Test
    void delete_withIsNullCondition_deletesMatchingRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .isNull(User_.email)
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .doesNotContain("Charlie");
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

        assertThat(affected).isEqualTo(1);
        assertThat(userRepository.findAll()).hasSize(2);
    }

    @Test
    void delete_condition_falseSkipsWhereClause_otherPredicateApplied() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE", false)  // skipped
                .eq(User_.name, "Bob", true)         // applied
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .doesNotContain("Bob");
    }

    @Test
    void delete_condition_allFalse_noConditionRemains_throwsException() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE", false)
                .eq(User_.name, "Alice", false)
                .build();

        assertThatThrownBy(() -> userRepository.delete(spec))
                .isInstanceOfAny(IllegalStateException.class,
                        InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("WHERE condition");
    }

    @Test
    void delete_notIn_condition_deletesExpectedRows() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
                .notIn(User_.name, List.of("Alice"))
                .build();

        int affected = userRepository.delete(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userRepository.findAll())
                .extracting(User::getName)
                .containsOnly("Alice");
    }

}
