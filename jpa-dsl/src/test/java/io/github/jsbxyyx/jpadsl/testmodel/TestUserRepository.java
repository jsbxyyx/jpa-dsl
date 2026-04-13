package io.github.jsbxyyx.jpadsl.testmodel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}
