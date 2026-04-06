package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers {@link JpaSelectFragmentsContributor}
 * so that {@link JpaSelectExecutorImpl} is automatically contributed as a repository
 * fragment to any Spring Data JPA repository extending {@link JpaSelectExecutor}.
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, JpaSelectExecutor.class})
@ConditionalOnProperty(prefix = "jpadsl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaSelectExecutorAutoConfiguration {

    @Bean
    public JpaSelectFragmentsContributor jpaSelectFragmentsContributor() {
        return new JpaSelectFragmentsContributor();
    }
}
