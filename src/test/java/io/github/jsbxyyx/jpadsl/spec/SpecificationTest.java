package io.github.jsbxyyx.jpadsl.spec;

import io.github.jsbxyyx.jpadsl.TestApplication;
import io.github.jsbxyyx.jpadsl.core.SpecificationDsl;
import io.github.jsbxyyx.jpadsl.example.entity.User;
import io.github.jsbxyyx.jpadsl.example.entity.User_;
import io.github.jsbxyyx.jpadsl.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class SpecificationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.saveAll(Arrays.asList(
            new User("John Doe", "john@example.com", 25, "ACTIVE", "ADMIN"),
            new User("Jane Smith", "jane@example.com", 30, "ACTIVE", "USER"),
            new User("Bob Johnson", "bob@example.com", 17, "INACTIVE", "USER")
        ));
    }

    @Test
    void testEqualSpecification() {
        Specification<User> spec = new EqualSpecification<>(User_.status, "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2);
    }

    @Test
    void testNotEqualSpecification() {
        Specification<User> spec = new NotEqualSpecification<>(User_.status, "ACTIVE");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1);
    }

    @Test
    void testLikeSpecification() {
        Specification<User> spec = new LikeSpecification<>(User_.name, "Doe");
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("John Doe");
    }

    @Test
    void testInSpecification() {
        Specification<User> spec = new InSpecification<>(User_.role, Arrays.asList("ADMIN", "USER"));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(3);
    }

    @Test
    void testBetweenSpecification() {
        Specification<User> spec = new BetweenSpecification<>(User_.age, 20, 28);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1); // Only John(25); Bob(17) is out, Jane(30) is out
    }

    @Test
    void testGreaterThanSpecification() {
        Specification<User> spec = new GreaterThanSpecification<>(User_.age, 20);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // John(25), Jane(30)
    }

    @Test
    void testLessThanSpecification() {
        Specification<User> spec = new LessThanSpecification<>(User_.age, 20);
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1); // Bob(17)
    }

    @Test
    void testAndSpecification() {
        Specification<User> spec = new AndSpecification<>(
            new EqualSpecification<>(User_.status, "ACTIVE"),
            new GreaterThanSpecification<>(User_.age, 28)
        );
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1); // Jane(30, ACTIVE)
    }

    @Test
    void testOrSpecification() {
        Specification<User> spec = new OrSpecification<>(
            new EqualSpecification<>(User_.status, "INACTIVE"),
            new LikeSpecification<>(User_.name, "Jane")
        );
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2);
    }

    @Test
    void testNotSpecification() {
        Specification<User> spec = new NotSpecification<>(
            new EqualSpecification<>(User_.status, "ACTIVE")
        );
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(1); // Bob(INACTIVE)
    }

    @Test
    void testSpecificationDslChaining() {
        Specification<User> spec = SpecificationDsl.<User, String>equal(User_.status, "ACTIVE")
                .and(SpecificationDsl.gt(User_.age, 24));
        List<User> result = userRepository.findAll(spec);
        assertThat(result).hasSize(2); // John(25), Jane(30)
    }
}
