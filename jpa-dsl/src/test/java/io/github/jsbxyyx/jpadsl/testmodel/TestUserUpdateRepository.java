package io.github.jsbxyyx.jpadsl.testmodel;

import io.github.jsbxyyx.jpadsl.JpaUpdateExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestUserUpdateRepository extends
        JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaUpdateExecutor<User> {
}
