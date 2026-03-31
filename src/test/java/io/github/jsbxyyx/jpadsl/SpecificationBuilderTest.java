package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.Order;
import io.github.jsbxyyx.jpadsl.testmodel.Order_;
import io.github.jsbxyyx.jpadsl.testmodel.TestOrderRepository;
import io.github.jsbxyyx.jpadsl.testmodel.User;
import io.github.jsbxyyx.jpadsl.testmodel.User_;
import io.github.jsbxyyx.jpadsl.testmodel.TestUserRepository;
import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecificationBuilderTest {

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
    void builder_eqPredicate() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void builder_multipleCriteria() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .gt(User_.age, 35)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void builder_likeAndGte() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .like(User_.name, "lic")
                .gte(User_.age, 30)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Alice");
    }

    @Test
    void builder_between() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .between(User_.age, 25, 30)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void builder_in() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .in(User_.name, Arrays.asList("Alice", "Charlie"))
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void builder_notIn() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .notIn(User_.name, Arrays.asList("Alice", "Bob"))
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");
    }

    @Test
    void builder_isNullAndIsNotNull() {
        Specification<User> nullEmail = SpecificationBuilder.<User>builder()
                .isNull(User_.email)
                .build();
        List<User> withNullEmail = userRepository.findAll(nullEmail);
        assertThat(withNullEmail).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Charlie");

        Specification<User> notNullEmail = SpecificationBuilder.<User>builder()
                .isNotNull(User_.email)
                .build();
        List<User> withEmail = userRepository.findAll(notNullEmail);
        assertThat(withEmail).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void builder_ne() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .ne(User_.status, "ACTIVE")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Bob");
    }

    @Test
    void builder_lte() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .lte(User_.age, 30)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void builder_likeIgnoreCase() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .likeIgnoreCase(User_.name, "alice")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(User::getName)
                .containsExactly("Alice");
    }

    @Test
    void builder_orComposition() {
        Specification<User> orSpec = SpecificationDsl.or(
                SpecificationDsl.eq(User_.name, "Alice"),
                SpecificationDsl.eq(User_.name, "Charlie")
        );
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .predicate(orSpec)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void builder_leftJoin_findOrdersByUserStatus() {
        Specification<Order> spec = SpecificationBuilder.<Order>builder()
                .join(Order_.user, JoinType.LEFT,
                        (join, query, cb, predicates) ->
                                predicates.add(cb.equal(join.get(User_.status), "ACTIVE")))
                .build();
        List<Order> result = orderRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(Order::getOrderNo)
                .containsExactlyInAnyOrder("ORD-001", "ORD-002");
    }

    @Test
    void builder_innerJoin_findOrdersByUserName() {
        Specification<Order> spec = SpecificationBuilder.<Order>builder()
                .join(Order_.user, JoinType.INNER,
                        (join, query, cb, predicates) ->
                                predicates.add(cb.equal(join.get(User_.name), "Bob")))
                .build();
        List<Order> result = orderRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(Order::getOrderNo)
                .containsExactly("ORD-003");
    }

    @Test
    void builder_pluralJoin_findUsersWithPaidOrders() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .join(User_.orders, JoinType.INNER,
                        (join, query, cb, predicates) ->
                                predicates.add(cb.equal(join.get(Order_.status), "PAID")))
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void builder_nullValueIsIgnored() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, null)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3);
    }

    @Test
    void builder_emptyBuilder_returnsAll() {
        Specification<User> spec = SpecificationBuilder.<User>builder().build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3);
    }
}
