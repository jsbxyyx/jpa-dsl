package io.github.jsbxyyx.jpadsl.example.service;

import io.github.jsbxyyx.jpadsl.SpecificationBuilder;
import io.github.jsbxyyx.jpadsl.SpecificationDsl;
import io.github.jsbxyyx.jpadsl.PageRequestBuilder;
import io.github.jsbxyyx.jpadsl.example.entity.User;
import io.github.jsbxyyx.jpadsl.example.entity.User_;
import io.github.jsbxyyx.jpadsl.example.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Example service demonstrating type-safe DSL usage patterns with JPA Static Metamodel.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findActiveUsersByName(String nameKeyword) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal(User_.status, "ACTIVE")
                .like(User_.name, nameKeyword)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithComplexConditions(String status, int minAge, Collection<String> roles) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal(User_.status, status)
                .gte(User_.age, minAge)
                .in(User_.role, roles)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findByDsl(String name, int age) {
        Specification<User> spec = SpecificationDsl.<User, String>equal(User_.name, name)
                .and(SpecificationDsl.gt(User_.age, age));
        return userRepository.findAll(spec);
    }

    public List<User> findByNameOrEmail(String name, String email) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .predicate(SpecificationDsl.or(
                        SpecificationDsl.like(User_.name, name),
                        SpecificationDsl.like(User_.email, email)
                ))
                .build();
        return userRepository.findAll(spec);
    }

    public Page<User> findUsersPagedAndSorted(String status, int pageNum, int pageSize) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal(User_.status, status)
                .build();
        Pageable pageable = PageRequestBuilder.builder()
                .page(pageNum)
                .size(pageSize)
                .sortBy(User_.createdAt, Sort.Direction.DESC)
                .sortBy(User_.name, Sort.Direction.ASC)
                .build();
        return userRepository.findAll(spec, pageable);
    }

    public List<User> findUsersByAgeRange(int minAge, int maxAge) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .between(User_.age, minAge, maxAge)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithNullEmail() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNull(User_.email)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithNotNullEmail() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNotNull(User_.email)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findNonActiveUsers() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .not(SpecificationDsl.equal(User_.status, "ACTIVE"))
                .build();
        return userRepository.findAll(spec);
    }
}
