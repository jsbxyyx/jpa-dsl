package io.github.jsbxyyx.jpadsl.testmodel;

import io.github.jsbxyyx.jpadsl.JpaSelectExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestUserSelectRepository extends
        JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaSelectExecutor<User> {
}
