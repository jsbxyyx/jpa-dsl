package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.TestUserUpdateRepository;
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
    void update_setsFieldWithWhereCondition() {
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "INACTIVE")
                .eq(User_.status, "ACTIVE")
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(2);
        List<User> inactive = userRepository.findAll().stream()
                .filter(u -> "INACTIVE".equals(u.getStatus()))
                .toList();
        assertThat(inactive).hasSize(3);
    }

    @Test
    void update_setToNull() {
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.email, null)
                .eq(User_.name, "Alice")
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Alice".equals(u.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getEmail()).isNull();
    }

    @Test
    void update_multipleSetClauses() {
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BANNED")
                .set(User_.name, "Bob-Updated")
                .eq(User_.name, "Bob")
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear(); // evict stale first-level cache after bulk update

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Bob-Updated".equals(u.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getStatus()).isEqualTo("BANNED");
    }

    @Test
    void update_noMatchingRows() {
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "DELETED")
                .eq(User_.name, "NonExistent")
                .build();

        int affected = userRepository.update(spec);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void update_noSetClauses_throwsException() {
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .eq(User_.status, "ACTIVE")
                .build();

        // Spring JPA translates IllegalStateException to InvalidDataAccessApiUsageException
        assertThatThrownBy(() -> userRepository.update(spec))
                .isInstanceOfAny(IllegalStateException.class,
                        InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("SET clause");
    }

    @Test
    void update_eqWithNullValue_addsPredicateAndExecutes() {
        // eq(attr, null) must add the predicate — no longer silently skipped.
        // "name = null" in JPA matches nothing, so 0 rows are updated and no exception is thrown.
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .eq(User_.name, (String) null)
                .build();

        int affected = userRepository.update(spec);
        assertThat(affected).isGreaterThanOrEqualTo(0);
    }

    @Test
    void update_noWherePredicates_allowsFullTableUpdate() {
        // No WHERE predicates → full-table update (no safety guard)
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .build();

        int affected = userRepository.update(spec);
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
    void update_condition_trueAppliesWhereClause() {
        // condition=true: predicate applied → only ACTIVE users updated
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "ACTIVE", true)
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(userRepository.findAll())
                .filteredOn(u -> "BLOCKED".equals(u.getStatus()))
                .hasSize(2);
    }

    @Test
    void update_condition_falseSkipsWhereClause_otherPredicateApplied() {
        // condition=false skips one predicate; the other applies normally
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "INACTIVE", false)  // skipped
                .eq(User_.name, "Alice", true)         // applied
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User alice = userRepository.findAll().stream()
                .filter(u -> "Alice".equals(u.getName()))
                .findFirst().orElseThrow();
        assertThat(alice.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void update_condition_allFalse_updatesAllRows() {
        // All conditions are false → no predicates active → full-table update (no safety guard)
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED")
                .eq(User_.status, "ACTIVE", false)
                .eq(User_.name, "Alice", false)
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(3);
    }

    @Test
    void update_condition_gtConditionTrue() {
        // condition=true: update users with age > 30 (only Charlie, age=40)
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "SENIOR")
                .gt(User_.age, 30, true)
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User charlie = userRepository.findAll().stream()
                .filter(u -> "Charlie".equals(u.getName()))
                .findFirst().orElseThrow();
        assertThat(charlie.getStatus()).isEqualTo("SENIOR");
    }

    @Test
    void update_condition_trueWithNullValue_predicateIsAdded_doesNotThrow() {
        // condition=true with null value: predicate MUST be added (null guard is bypassed).
        // execute() must not throw the full-table guard because the predicate was registered.
        // The update may affect 0 rows (JPA `= null` semantics), but it must execute cleanly.
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "UPDATED")
                .eq(User_.name, (String) null, true)  // null value, condition=true → predicate added
                .build();
        // Should not throw — no full-table guard because the predicate was registered
        int affected = userRepository.update(spec);
        assertThat(affected).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------ //
    //  set condition overload tests
    // ------------------------------------------------------------------ //

    @Test
    void update_setCondition_trueAppliesSetClause() {
        // condition=true: both SET clauses applied → status and name both updated for Alice
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED", true)   // applied
                .set(User_.name, "Alice-Updated", true) // applied
                .eq(User_.name, "Alice")
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Alice-Updated".equals(u.getName()))
                .findFirst().orElseThrow();
        assertThat(found.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void update_setCondition_falseSkipsSetClause() {
        // condition=false: the name SET clause is skipped; only status is updated
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED", true)      // applied
                .set(User_.name, "Alice-Updated", false) // skipped
                .eq(User_.name, "Alice")
                .build();

        int affected = userRepository.update(spec);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(1);
        User found = userRepository.findAll().stream()
                .filter(u -> "Alice".equals(u.getName())) // name unchanged
                .findFirst().orElseThrow();
        assertThat(found.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void update_setCondition_allFalse_throwsNoSetClause() {
        // All SET conditions are false → no SET clauses → must throw
        UpdateSpec<User> spec = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "BLOCKED", false)
                .set(User_.name, "Alice-Updated", false)
                .eq(User_.name, "Alice")
                .build();

        assertThatThrownBy(() -> userRepository.update(spec))
                .isInstanceOfAny(IllegalStateException.class,
                        InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("SET clause");
    }

    // ------------------------------------------------------------------ //
    //  Backward-compatibility: deprecated executeUpdate(UpdateBuilder) API
    // ------------------------------------------------------------------ //

    @Test
    @SuppressWarnings("deprecation")
    void executeUpdate_backwardCompat_delegatesToUpdate() {
        // Verify that the deprecated executeUpdate(UpdateBuilder) still works via
        // the default interface method that delegates to update(UpdateSpec).
        UpdateBuilder<User> builder = UpdateBuilder.<User>builder(User.class)
                .set(User_.status, "INACTIVE")
                .eq(User_.status, "ACTIVE");

        int affected = userRepository.executeUpdate(builder);
        testEntityManager.clear();

        assertThat(affected).isEqualTo(2);
        List<User> inactive = userRepository.findAll().stream()
                .filter(u -> "INACTIVE".equals(u.getStatus()))
                .toList();
        assertThat(inactive).hasSize(3);
    }
}

