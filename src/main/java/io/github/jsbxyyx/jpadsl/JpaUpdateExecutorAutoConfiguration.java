package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers {@link JpaUpdateExecutorImpl}
 * as a bean so that Spring Data JPA can discover it as a custom repository
 * fragment implementation for {@link JpaUpdateExecutor}.
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, JpaUpdateExecutor.class})
public class JpaUpdateExecutorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JpaUpdateExecutorImpl.class)
    public <T> JpaUpdateExecutorImpl<T> jpaUpdateExecutorImpl() {
        return new JpaUpdateExecutorImpl<>();
    }
}
