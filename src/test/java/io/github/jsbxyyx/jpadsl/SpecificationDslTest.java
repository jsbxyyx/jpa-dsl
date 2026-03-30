package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.Order;
import io.github.jsbxyyx.jpadsl.testmodel.OrderRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static io.github.jsbxyyx.jpadsl.SpecificationDsl.and;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.between;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.equal;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.greaterThan;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.greaterThanOrEqual;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.in;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.isNotNull;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.isNull;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.lessThan;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.like;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.not;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.notEqual;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.notIn;
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.or;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecificationDslTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

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
        Specification<User> spec = equal("status", "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void notEqual_shouldExcludeStatus() {
        Specification<User> spec = notEqual("status", "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void like_shouldFilterByName() {
        Specification<User> spec = like("name", "A%");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Alice");
    }

    @Test
    void greaterThan_shouldFilterByAge() {
        Specification<User> spec = greaterThan("age", 25);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void lessThan_shouldFilterByAge() {
        Specification<User> spec = lessThan("age", 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void greaterThanOrEqual_shouldFilterByAge() {
        Specification<User> spec = greaterThanOrEqual("age", 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void between_shouldFilterByAge() {
        Specification<User> spec = between("age", 25, 30);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void in_shouldFilterByNames() {
        Specification<User> spec = in("name", Arrays.asList("Alice", "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void in_varargs_shouldFilterByNames() {
        Specification<User> spec = in("name", "Alice", "Charlie");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void notIn_shouldExcludeNames() {
        Specification<User> spec = notIn("name", Arrays.asList("Alice", "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void isNull_shouldFindUsersWithNullEmail() {
        // Charlie was saved with null email
        Specification<User> spec = isNull("email");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void isNotNull_shouldFindUsersWithNonNullEmail() {
        Specification<User> spec = isNotNull("email");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void and_shouldCombinePredicates() {
        Specification<User> spec = and(equal("status", "ACTIVE"), greaterThan("age", 35));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void or_shouldCombinePredicates() {
        Specification<User> spec = or(equal("name", "Alice"), equal("name", "Bob"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void not_shouldNegateSpec() {
        Specification<User> spec = not(equal("status", "ACTIVE"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void nestedAndOr_shouldWorkCorrectly() {
        Specification<User> spec = and(
                equal("status", "ACTIVE"),
                or(
                        greaterThan("age", 35),
                        like("name", "A%")
                )
        );
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void orderQuery_amountBetween() {
        Specification<Order> spec = between("amount", new BigDecimal("50.00"), new BigDecimal("200.00"));
        List<Order> result = orderRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(Order::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-003");
    }
}
