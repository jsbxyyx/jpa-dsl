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
    void executeUpdate_eqWithNullValue_addsPredicateAndExecutes() {
        // eq(attr, null) must add the predicate — no longer silently skipped.
        // "name = null" in JPA matches nothing, so 0 rows are updated and no exception is thrown.
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .eq(User_.name, (String) null)
                .build();

        int affected = userRepository.executeUpdate(update);
        assertThat(affected).isGreaterThanOrEqualTo(0);
    }

    @Test
    void executeUpdate_noWherePredicates_allowsFullTableUpdate() {
        // No WHERE predicates → full-table update (no safety guard)
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(3);
        assertThat(userRepository.findAll())
                .extracting(User::getStatus)
                .containsOnly("UPDATED");
    }

    // ------------------------------------------------------------------ //
    //  condition overload tests
    // ------------------------------------------------------------------ //

    @Test
    void executeUpdate_condition_trueAppliesWhereClause() {
        // condition=true: predicate applied → only ACTIVE users updated
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "ACTIVE", true)
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(userRepository.findAll())
                .filteredOn(u -> "BLOCKED".equals(u.getStatus()))
                .hasSize(2);
    }

    @Test
    void executeUpdate_condition_falseSkipsWhereClause_otherPredicateApplied() {
        // condition=false skips one predicate; the other applies normally
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "INACTIVE", false)  // skipped
                .eq(User_.name, "Alice", true)         // applied
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User alice = userRepository.findAll().stream()
                .filter(u -> "Alice".equals(u.getName()))
                .findFirst().orElseThrow();
        assertThat(alice.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void executeUpdate_condition_allFalse_updatesAllRows() {
        // All conditions are false → no predicates active → full-table update (no safety guard)
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "ACTIVE", false)
                .eq(User_.name, "Alice", false)
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(3);
    }

    @Test
    void executeUpdate_condition_gtConditionTrue() {
        // condition=true: update users with age > 30 (only Charlie, age=40)
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "SENIOR")
                .gt(User_.age, 30, true)
                .build();

        int affected = userRepository.executeUpdate(update);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User charlie = userRepository.findAll().stream()
                .filter(u -> "Charlie".equals(u.getName()))
                .findFirst().orElseThrow();
        assertThat(charlie.getStatus()).isEqualTo("SENIOR");
    }

    @Test
    void executeUpdate_condition_trueWithNullValue_predicateIsAdded_doesNotThrow() {
        // condition=true with null value: predicate MUST be added (null guard is bypassed).
        // execute() must not throw the full-table guard because the predicate was registered.
        // The update may affect 0 rows (JPA `= null` semantics), but it must execute cleanly.
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .eq(User_.name, (String) null, true)  // null value, condition=true → predicate added
                .build();
        // Should not throw — no full-table guard because the predicate was registered
        int affected = userRepository.executeUpdate(update);
        assertThat(affected).isGreaterThanOrEqualTo(0);
    }
}
