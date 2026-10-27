package io.github.jsbxyyx.jpadsl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for jpa-dsl auto-configuration switches ({@code jpadsl.enabled}).
 */
class JpaDslAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JpaSelectExecutorAutoConfiguration.class,
                    JpaUpdateExecutorAutoConfiguration.class,
                    JpaDeleteExecutorAutoConfiguration.class));

    // ------------------------------------------------------------------ //
    //  jpadsl.enabled switch
    // ------------------------------------------------------------------ //

    @Test
    void whenJpaDslEnabled_byDefault_contributorsAreRegistered() {
        contextRunner.run(context -> {
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaSelectFragmentsContributor.class).size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaUpdateFragmentsContributor.class).size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaDeleteFragmentsContributor.class).size());
        });
    }

    @Test
    void whenJpaDslEnabled_explicitly_contributorsAreRegistered() {
        contextRunner.withPropertyValues("jpadsl.enabled=true").run(context -> {
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaSelectFragmentsContributor.class).size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaUpdateFragmentsContributor.class).size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    context.getBeansOfType(JpaDeleteFragmentsContributor.class).size());
        });
    }

    @Test
    void whenJpaDslDisabled_noContributorsAreRegistered() {
        contextRunner.withPropertyValues("jpadsl.enabled=false").run(context -> {
            org.junit.jupiter.api.Assertions.assertTrue(
                    context.getBeansOfType(JpaSelectFragmentsContributor.class).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(
                    context.getBeansOfType(JpaUpdateFragmentsContributor.class).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(
                    context.getBeansOfType(JpaDeleteFragmentsContributor.class).isEmpty());
        });
    }
}
