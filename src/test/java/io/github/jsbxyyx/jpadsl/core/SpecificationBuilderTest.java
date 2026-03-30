package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.TestApplication;
import io.github.jsbxyyx.jpadsl.example.entity.User;
import io.github.jsbxyyx.jpadsl.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class SpecificationBuilderTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.saveAll(Arrays.asList(
            new User("John Doe", "john@example.com", 25, "ACTIVE", "ADMIN"),
            new User("Jane Smith", "jane@example.com", 30, "ACTIVE", "USER"),
            new User("Bob Johnson", "bob@example.com", 17, "INACTIVE", "USER"),
            new User("Alice Brown", null, 22, "ACTIVE", "USER"),
            new User("Charlie Wilson", "charlie@example.com", 35, "PENDING", "MANAGER")
        ));
    }

    @Test
    void testEqualCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", "ACTIVE")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(u -> "ACTIVE".equals(u.getStatus()));
    }

    @Test
    void testNotEqualCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .notEqual("status", "ACTIVE")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(u -> "ACTIVE".equals(u.getStatus()));
    }

    @Test
    void testLikeCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .like("name", "John")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // John Doe and Bob Johnson
    }

    @Test
    void testInCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .in("status", Arrays.asList("ACTIVE", "PENDING"))
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(4);
    }

    @Test
    void testBetweenCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .between("age", 20, 30)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3); // John(25), Jane(30), Alice(22)
        assertThat(result).allMatch(u -> u.getAge() >= 20 && u.getAge() <= 30);
    }

    @Test
    void testGreaterThanCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .greaterThan("age", 25)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // Jane(30), Charlie(35)
        assertThat(result).allMatch(u -> u.getAge() > 25);
    }

    @Test
    void testLessThanCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .lessThan("age", 25)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // Bob(17), Alice(22)
        assertThat(result).allMatch(u -> u.getAge() < 25);
    }

    @Test
    void testGreaterThanOrEqualCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .greaterThanOrEqual("age", 25)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3); // John(25), Jane(30), Charlie(35)
        assertThat(result).allMatch(u -> u.getAge() >= 25);
    }

    @Test
    void testLessThanOrEqualCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .lessThanOrEqual("age", 25)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3); // John(25), Bob(17), Alice(22)
        assertThat(result).allMatch(u -> u.getAge() <= 25);
    }

    @Test
    void testIsNullCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNull("email")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1); // Alice Brown
        assertThat(result.get(0).getName()).isEqualTo("Alice Brown");
    }

    @Test
    void testIsNotNullCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNotNull("email")
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(4);
        assertThat(result).allMatch(u -> u.getEmail() != null);
    }

    @Test
    void testOrCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .or(
                    SpecificationDsl.equal("status", "INACTIVE"),
                    SpecificationDsl.equal("status", "PENDING")
                )
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2);
    }

    @Test
    void testNotCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .not(SpecificationDsl.equal("status", "ACTIVE"))
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(u -> "ACTIVE".equals(u.getStatus()));
    }

    @Test
    void testCombinedConditions() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", "ACTIVE")
                .greaterThan("age", 24)
                .build();
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // John(25,ACTIVE), Jane(30,ACTIVE)
    }

    @Test
    void testPaginationAndSorting() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", "ACTIVE")
                .build();
        var pageRequest = PageRequestBuilder.builder()
                .page(0)
                .size(2)
                .sortBy("age", Sort.Direction.DESC)
                .build();
        Page<User> page = userRepository.findAll(spec, pageRequest);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        // Sorted by age DESC, first should be Jane(30)
        assertThat(page.getContent().get(0).getName()).isEqualTo("Jane Smith");
    }
}
