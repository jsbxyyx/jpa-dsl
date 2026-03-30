package com.jpadsl.example.service;

import com.jpadsl.core.PageRequestBuilder;
import com.jpadsl.core.SpecificationBuilder;
import com.jpadsl.core.SpecificationDsl;
import com.jpadsl.example.entity.User;
import com.jpadsl.example.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Example service demonstrating DSL usage patterns.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findActiveUsersByName(String nameKeyword) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", "ACTIVE")
                .like("name", nameKeyword)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithComplexConditions(String status, int minAge, List<String> roles) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", status)
                .greaterThanOrEqual("age", minAge)
                .in("role", roles)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findByDsl(String name, int age) {
        Specification<User> spec = SpecificationDsl.<User>equal("name", name)
                .and(SpecificationDsl.greaterThan("age", age));
        return userRepository.findAll(spec);
    }

    public List<User> findByNameOrEmail(String name, String email) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .or(
                    SpecificationDsl.like("name", name),
                    SpecificationDsl.like("email", email)
                )
                .build();
        return userRepository.findAll(spec);
    }

    public Page<User> findUsersPagedAndSorted(String status, int pageNum, int pageSize) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .equal("status", status)
                .build();
        PageRequest pageRequest = PageRequestBuilder.builder()
                .page(pageNum)
                .size(pageSize)
                .sortBy("createdAt", Sort.Direction.DESC)
                .sortBy("name", Sort.Direction.ASC)
                .build();
        return userRepository.findAll(spec, pageRequest);
    }

    public List<User> findUsersByAgeRange(int minAge, int maxAge) {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .between("age", minAge, maxAge)
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithNullEmail() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNull("email")
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findUsersWithNotNullEmail() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .isNotNull("email")
                .build();
        return userRepository.findAll(spec);
    }

    public List<User> findNonActiveUsers() {
        Specification<User> spec = SpecificationBuilder.<User>builder()
                .not(SpecificationDsl.equal("status", "ACTIVE"))
                .build();
        return userRepository.findAll(spec);
    }
}
