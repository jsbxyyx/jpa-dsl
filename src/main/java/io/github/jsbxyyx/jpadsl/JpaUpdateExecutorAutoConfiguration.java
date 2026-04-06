package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers {@link JpaUpdateFragmentsContributor}
 * so that {@link JpaUpdateExecutorImpl} is automatically contributed as a repository
 * fragment to any Spring Data JPA repository extending {@link JpaUpdateExecutor}.
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, JpaUpdateExecutor.class})
@ConditionalOnProperty(prefix = "jpadsl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaUpdateExecutorAutoConfiguration {

    @Bean
    public JpaUpdateFragmentsContributor jpaUpdateFragmentsContributor() {
        return new JpaUpdateFragmentsContributor();
    }
}
