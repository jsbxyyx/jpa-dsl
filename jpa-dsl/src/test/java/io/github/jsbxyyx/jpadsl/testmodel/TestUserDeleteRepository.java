package io.github.jsbxyyx.jpadsl.testmodel;

import io.github.jsbxyyx.jpadsl.JpaDeleteExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestUserDeleteRepository extends
        JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaDeleteExecutor<User> {
}
