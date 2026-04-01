package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * {@link BeanPostProcessor} that automatically contributes {@link JpaUpdateExecutorImpl}
 * as a repository fragment to every Spring Data JPA repository that extends
 * {@link JpaUpdateExecutor}, regardless of the repository's package location.
 *
 * <p>Registered via Spring Boot auto-configuration so that user applications and
 * {@code @DataJpaTest} contexts pick it up without any extra configuration.
 */
public class JpaUpdateFragmentsContributor implements BeanPostProcessor, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RepositoryFactoryBeanSupport<?, ?, ?> factoryBean) {
            Class<?> repoInterface = factoryBean.getObjectType();
            if (repoInterface != null && JpaUpdateExecutor.class.isAssignableFrom(repoInterface)) {
                EntityManagerFactory emf = beanFactory.getBean(EntityManagerFactory.class);
                EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
                factoryBean.setRepositoryFragments(
                        RepositoryComposition.RepositoryFragments.just(new JpaUpdateExecutorImpl<>(em)));
            }
        }
        return bean;
    }
}
