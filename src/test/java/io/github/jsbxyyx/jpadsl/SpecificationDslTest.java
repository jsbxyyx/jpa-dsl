package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.Order;
import io.github.jsbxyyx.jpadsl.testmodel.Order_;
import io.github.jsbxyyx.jpadsl.testmodel.TestOrderRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.User_;
import io.github.jsbxyyx.jpadsl.testmodel.TestUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static io.github.jsbxyyx.jpadsl.SpecificationDsl.and;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.between;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.equal;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.gt;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.gte;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.in;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.isNotNull;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.isNull;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.lt;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.like;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.not;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.notEqual;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.notIn;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.or;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecificationDslTest {

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestOrderRepository orderRepository;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("Alice", "alice@example.com", 30, "ACTIVE"));
        bob = userRepository.save(new User("Bob", "bob@example.com", 25, "INACTIVE"));
        charlie = userRepository.save(new User("Charlie", null, 40, "ACTIVE"));

        orderRepository.save(new Order("ORD-001", new BigDecimal("100.00"), "PAID", alice));
        orderRepository.save(new Order("ORD-002", new BigDecimal("250.00"), "PENDING", alice));
        orderRepository.save(new Order("ORD-003", new BigDecimal("50.00"), "PAID", bob));
    }

    @Test
    void equal_shouldFilterByStatus() {
        Specification<User> spec = equal(User_.status, "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void notEqual_shouldExcludeStatus() {
        Specification<User> spec = notEqual(User_.status, "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void like_shouldFilterByName() {
        Specification<User> spec = like(User_.name, "lic");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Alice");
    }

    @Test
    void gt_shouldFilterByAge() {
        Specification<User> spec = gt(User_.age, 25);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void lt_shouldFilterByAge() {
        Specification<User> spec = lt(User_.age, 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void gte_shouldFilterByAge() {
        Specification<User> spec = gte(User_.age, 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void between_shouldFilterByAge() {
        Specification<User> spec = between(User_.age, 25, 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void in_shouldFilterByNames() {
        Specification<User> spec = in(User_.name, Arrays.asList("Alice", "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void in_collection_shouldFilterByNames() {
        Specification<User> spec = in(User_.name, List.of("Alice", "Charlie"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void notIn_shouldExcludeNames() {
        Specification<User> spec = notIn(User_.name, Arrays.asList("Alice", "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void isNull_shouldFindUsersWithNullEmail() {
        Specification<User> spec = isNull(User_.email);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void isNotNull_shouldFindUsersWithNonNullEmail() {
        Specification<User> spec = isNotNull(User_.email);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void and_shouldCombinePredicates() {
        Specification<User> spec = and(equal(User_.status, "ACTIVE"), gt(User_.age, 35));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void or_shouldCombinePredicates() {
        Specification<User> spec = or(equal(User_.name, "Alice"), equal(User_.name, "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void not_shouldNegateSpec() {
        Specification<User> spec = not(equal(User_.status, "ACTIVE"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void nestedAndOr_shouldWorkCorrectly() {
        Specification<User> spec = and(
                equal(User_.status, "ACTIVE"),
                or(
                        gt(User_.age, 35),
                        like(User_.name, "lic")
                )
        );
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void orderQuery_amountBetween() {
        Specification<Order> spec = between(Order_.amount, new BigDecimal("50.00"), new BigDecimal("200.00"));
        List<Order> result = orderRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(Order::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-003");
    }
}
