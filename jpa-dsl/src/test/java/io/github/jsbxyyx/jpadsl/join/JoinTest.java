package io.github.jsbxyyx.jpadsl.join;

import io.github.jsbxyyx.jpadsl.SpecificationBuilder;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for both FK-based joins (via metamodel association attributes) and
 * no-FK joins (via {@link io.github.jsbxyyx.jpadsl.core.JoinCondition}).
 */
@DataJpaTest
class JoinTest {

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

        alice   = userRepository.save(new User("Alice",   "alice@example.com",   30, "ACTIVE"));
        bob     = userRepository.save(new User("Bob",     "bob@example.com",     25, "INACTIVE"));
        charlie = userRepository.save(new User("Charlie", "charlie@example.com", 40, "ACTIVE"));

        // Alice has two orders: one PAID, one PENDING
        orderRepository.save(new Order("ORD-001", new BigDecimal("100.00"), "PAID",    alice));
        orderRepository.save(new Order("ORD-002", new BigDecimal("200.00"), "PENDING", alice));
        // Bob has one PAID order
        orderRepository.save(new Order("ORD-003", new BigDecimal("50.00"),  "PAID",    bob));
        // Charlie has no orders
    }

    // ------------------------------------------------------------------ //
    //  FK-based join (via metamodel association attribute)
    // ------------------------------------------------------------------ //

    /**
     * INNER join: only users that have at least one PAID order should be returned.
     */
    @Test
    void fkJoin_inner_filterByOrderStatus() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .join(User_.orders, JoinType.INNER, (join, query, cb, predicates) ->
                        predicates.add(cb.equal(join.get(Order_.status), "PAID")))
                .build();

        List<User> result = userRepository.findAll(spec);

        // Alice appears once (she has exactly one PAID order), Bob appears once
        List<String> names = result.stream().map(User::getName).toList();
        assertThat(names).containsExactlyInAnyOrder("Alice", "Bob");
    }

    /**
     * INNER join: only users with at least one PENDING order should appear.
     */
    @Test
    void fkJoin_inner_filterByPendingStatus() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .join(User_.orders, JoinType.INNER, (join, query, cb, predicates) ->
                        predicates.add(cb.equal(join.get(Order_.status), "PENDING")))
                .build();

        List<User> result = userRepository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
    }

    /**
     * Combining an FK join with an additional predicate on the driving entity.
     */
    @Test
    void fkJoin_withAdditionalFilter() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .join(User_.orders, JoinType.INNER, (join, query, cb, predicates) ->
                        predicates.add(cb.equal(join.get(Order_.status), "PAID")))
                .build();

        List<User> result = userRepository.findAll(spec);

        // Only Alice is ACTIVE and has a PAID order
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
    }

    // ------------------------------------------------------------------ //
    //  No-FK join (via JoinCondition — Hibernate 6 / standard fallback)
    // ------------------------------------------------------------------ //

    /**
     * No-FK join: join {@code User} and {@code Order} by matching {@code User.id} to
     * {@code Order.userId} without relying on a mapped association attribute.
     * Only users that have at least one matching order are returned.
     */
    @Test
    void noFkJoin_joinByUserId() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .join(Order.class, JoinType.INNER, (userRoot, orderJoin, cb) ->
                        cb.equal(userRoot.get(User_.id), orderJoin.get(Order_.userId)))
                .build();

        List<User> result = userRepository.findAll(spec);

        // Alice (2 orders) and Bob (1 order) have orders, so both should appear.
        // Charlie has no orders → never appears.
        // JPA deduplicates entity instances, so Alice appears at most once per distinct identity.
        List<String> distinctNames = result.stream().map(User::getName).distinct().toList();
        assertThat(distinctNames).containsExactlyInAnyOrder("Alice", "Bob");
    }

    /**
     * No-FK join with an additional condition in the ON clause: only users who have
     * a PAID order joined by userId should be returned.
     */
    @Test
    void noFkJoin_joinByUserIdWithStatusFilter() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .join(Order.class, JoinType.INNER, (userRoot, orderJoin, cb) ->
                        cb.and(
                                cb.equal(userRoot.get(User_.id), orderJoin.get(Order_.userId)),
                                cb.equal(orderJoin.get(Order_.status), "PAID")
                        ))
                .build();

        List<User> result = userRepository.findAll(spec);

        List<String> names = result.stream().map(User::getName).distinct().toList();
        assertThat(names).containsExactlyInAnyOrder("Alice", "Bob");
    }

    /**
     * No-FK join combined with a filter on the driving entity.
     */
    @Test
    void noFkJoin_withUserStatusFilter() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .join(Order.class, JoinType.INNER, (userRoot, orderJoin, cb) ->
                        cb.and(
                                cb.equal(userRoot.get(User_.id), orderJoin.get(Order_.userId)),
                                cb.equal(orderJoin.get(Order_.status), "PAID")
                        ))
                .build();

        List<User> result = userRepository.findAll(spec);

        // Only Alice is ACTIVE and has a PAID order
        List<String> names = result.stream().map(User::getName).distinct().toList();
        assertThat(names).containsExactlyInAnyOrder("Alice");
    }

    // ------------------------------------------------------------------ //
    //  JoinStrategyResolver sanity check
    // ------------------------------------------------------------------ //

    /**
     * Verifies that the strategy resolver returns a non-null strategy in all environments.
     */
    @Test
    void strategyResolver_returnsNonNull() {
        assertThat(JoinStrategyResolver.resolve()).isNotNull();
    }

    /**
     * In this test environment (Spring Boot 3 / Hibernate 6+), the Hibernate strategy
     * should be resolved.
     */
    @Test
    void strategyResolver_usesHibernateStrategyInTestEnv() {
        assertThat(JoinStrategyResolver.resolve()).isInstanceOf(HibernateJoinStrategy.class);
    }
}
