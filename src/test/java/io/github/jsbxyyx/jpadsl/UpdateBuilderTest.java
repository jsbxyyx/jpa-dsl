package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.TestUserUpdateRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.User_;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaUpdateExecutorImpl.class)
class UpdateBuilderTest {

    @Autowired
    private TestUserUpdateRepository userRepository;

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
    void executeUpdate_setsFieldWithWhereCondition() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "INACTIVE")
                .eq(User_.status, "ACTIVE")
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(2);
        List<User> inactive = userRepository.findAll().stream()
                .filter(u -> "INACTIVE".equals(u.getStatus()))
                .toList();
        assertThat(inactive).hasSize(3);
    }

    @Test
    void executeUpdate_setToNull() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.email, null)
                .eq(User_.name, "Alice")
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Alice".equals(u.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getEmail()).isNull();
    }

    @Test
    void executeUpdate_multipleSetClauses() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BANNED")
                .set(User_.name, "Bob-Updated")
                .eq(User_.name, "Bob")
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Bob-Updated".equals(u.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getStatus()).isEqualTo("BANNED");
    }

    @Test
    void executeUpdate_noMatchingRows() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "DELETED")
                .eq(User_.name, "NonExistent")
                .build();

        int affected = userRepository.executeUpdate(update);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void executeUpdate_noSetClauses_throwsException() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE")
                .build();

        // Spring JPA translates IllegalStateException to InvalidDataAccessApiUsageException
        assertThatThrownBy(() -> userRepository.executeUpdate(update))
                .isInstanceOfAny(IllegalStateException.class,
                        InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("SET clause");
    }

    @Test
    void executeUpdate_nullValueInWhereSilentlySkipped() {
        // null value in eq() should not add a predicate — matches all rows
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .eq(User_.status, null)
                .build();

        int affected = userRepository.executeUpdate(update);

        // No WHERE clause added, so all rows are updated
        assertThat(affected).isEqualTo(3);
    }
}
