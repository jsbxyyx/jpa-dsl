package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers {@link JpaDeleteFragmentsContributor}
 * so that {@link JpaDeleteExecutorImpl} is automatically contributed as a repository
 * fragment to any Spring Data JPA repository extending {@link JpaDeleteExecutor}.
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, JpaDeleteExecutor.class})
public class JpaDeleteExecutorAutoConfiguration {

    @Bean
    public JpaDeleteFragmentsContributor jpaDeleteFragmentsContributor() {
        return new JpaDeleteFragmentsContributor();
    }
}
