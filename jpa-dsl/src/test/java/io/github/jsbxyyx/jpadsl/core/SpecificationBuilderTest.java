package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.TestApplication;
import io.github.jsbxyyx.jpadsl.example.entity.User;
import io.github.jsbxyyx.jpadsl.example.entity.User_;
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
                new User("Charlie Wilson", "charlie@example.com", 35, "PENDING", "MANAGER")));
    }

    @Test
    void testEqualCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().eq(User_.status, "ACTIVE").build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size());
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> "ACTIVE".equals(u.getStatus())));
    }

    @Test
    void testNotEqualCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().ne(User_.status, "ACTIVE").build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertFalse(result.stream().anyMatch(u -> "ACTIVE".equals(u.getStatus())));
    }

    @Test
    void testLikeCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().like(User_.name, "John").build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size()); // John Doe and Bob Johnson
    }

    @Test
    void testInCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .in(User_.status, Arrays.asList("ACTIVE", "PENDING"))
                .build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(4, result.size());
    }

    @Test
    void testBetweenCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().between(User_.age, 20, 30).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size()); // John(25), Jane(30), Alice(22)
        org.junit.jupiter.api.Assertions.assertTrue(
                result.stream().allMatch(u -> u.getAge() >= 20 && u.getAge() <= 30));
    }

    @Test
    void testGtCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().gt(User_.age, 25).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size()); // Jane(30), Charlie(35)
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> u.getAge() > 25));
    }

    @Test
    void testLtCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().lt(User_.age, 25).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size()); // Bob(17), Alice(22)
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> u.getAge() < 25));
    }

    @Test
    void testGteCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().gte(User_.age, 25).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size()); // John(25), Jane(30), Charlie(35)
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> u.getAge() >= 25));
    }

    @Test
    void testLteCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().lte(User_.age, 25).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(3, result.size()); // John(25), Bob(17), Alice(22)
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> u.getAge() <= 25));
    }

    @Test
    void testIsNullCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().isNull(User_.email).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size()); // Alice Brown
        org.junit.jupiter.api.Assertions.assertEquals(
                "Alice Brown", result.get(0).getName());
    }

    @Test
    void testIsNotNullCondition() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().isNotNull(User_.email).build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(4, result.size());
        org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(u -> u.getEmail() != null));
    }

    @Test
    void testOrCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .or(SpecificationDsl.eq(User_.status, "INACTIVE"), SpecificationDsl.eq(User_.status, "PENDING"))
                .build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
    }

    @Test
    void testNotCondition() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .not(SpecificationDsl.eq(User_.status, "ACTIVE"))
                .build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size());
        org.junit.jupiter.api.Assertions.assertFalse(result.stream().anyMatch(u -> "ACTIVE".equals(u.getStatus())));
    }

    @Test
    void testCombinedConditions() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .eq(User_.status, "ACTIVE")
                .gt(User_.age, 24)
                .build();
        List<User> result = userRepository.findAll(spec);
        org.junit.jupiter.api.Assertions.assertEquals(2, result.size()); // John(25,ACTIVE), Jane(30,ACTIVE)
    }

    @Test
    void testPaginationAndSorting() {
        Specification<User> spec =
                SpecificationBuilder.<User>builder().eq(User_.status, "ACTIVE").build();
        var pageRequest = PageRequestBuilder.builder()
                .page(0)
                .size(2)
                .sortBy(User_.age, Sort.Direction.DESC)
                .build();
        Page<User> page = userRepository.findAll(spec, pageRequest);
        org.junit.jupiter.api.Assertions.assertEquals(3, page.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
        // Sorted by age DESC: Jane(30), John(25), Alice(22) — first page has Jane and John
        org.junit.jupiter.api.Assertions.assertTrue(
                ((java.lang.Comparable) page.getContent().get(0).getAge())
                                .compareTo(page.getContent().get(1).getAge())
                        >= 0);
    }
}
