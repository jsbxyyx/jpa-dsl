package io.github.jsbxyyx.jpadsl.example;

import io.github.jsbxyyx.jpadsl.TestApplication;
import io.github.jsbxyyx.jpadsl.example.entity.Order;
import io.github.jsbxyyx.jpadsl.example.entity.User;
import io.github.jsbxyyx.jpadsl.example.repository.OrderRepository;
import io.github.jsbxyyx.jpadsl.example.repository.UserRepository;
import io.github.jsbxyyx.jpadsl.example.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
        User john = userRepository.save(new User("John Doe", "john@example.com", 25, "ACTIVE", "ADMIN"));
        User jane = userRepository.save(new User("Jane Smith", "jane@example.com", 30, "ACTIVE", "USER"));
        User bob = userRepository.save(new User("Bob Johnson", "bob@example.com", 17, "INACTIVE", "USER"));
        User alice = userRepository.save(new User("Alice Brown", null, 22, "ACTIVE", "USER"));
        User charlie = userRepository.save(new User("Charlie Wilson", "charlie@example.com", 35, "PENDING", "MANAGER"));

        orderRepository.save(new Order("ORD-001", new BigDecimal("100.00"), "COMPLETED", john));
        orderRepository.save(new Order("ORD-002", new BigDecimal("200.00"), "PENDING", jane));
    }

    @Test
    void testFindActiveUsersByName() {
        List<User> result = userService.findActiveUsersByName("John");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("John Doe");
    }

    @Test
    void testFindUsersWithComplexConditions() {
        List<User> result = userService.findUsersWithComplexConditions("ACTIVE", 20, Arrays.asList("ADMIN", "USER"));
        assertThat(result).hasSize(3); // John(25,ACTIVE,ADMIN), Jane(30,ACTIVE,USER), Alice(22,ACTIVE,USER)
    }

    @Test
    void testFindByDsl() {
        List<User> result = userService.findByDsl("John Doe", 20);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("John Doe");
    }

    @Test
    void testFindByNameOrEmail() {
        List<User> result = userService.findByNameOrEmail("John", "jane");
        assertThat(result).hasSize(3); // John Doe (name), Bob Johnson (name contains "John"), Jane Smith (email like "jane")
    }

    @Test
    void testFindUsersPagedAndSorted() {
        Page<User> result = userService.findUsersPagedAndSorted("ACTIVE", 0, 2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindUsersByAgeRange() {
        List<User> result = userService.findUsersByAgeRange(20, 30);
        assertThat(result).hasSize(3); // John(25), Jane(30), Alice(22)
    }

    @Test
    void testFindUsersWithNullEmail() {
        List<User> result = userService.findUsersWithNullEmail();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice Brown");
    }

    @Test
    void testFindUsersWithNotNullEmail() {
        List<User> result = userService.findUsersWithNotNullEmail();
        assertThat(result).hasSize(4);
    }

    @Test
    void testFindNonActiveUsers() {
        List<User> result = userService.findNonActiveUsers();
        assertThat(result).hasSize(2); // Bob(INACTIVE), Charlie(PENDING)
    }
}
