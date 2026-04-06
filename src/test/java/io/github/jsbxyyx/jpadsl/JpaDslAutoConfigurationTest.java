package io.github.jsbxyyx.jpadsl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(context).hasSingleBean(JpaSelectFragmentsContributor.class);
            assertThat(context).hasSingleBean(JpaUpdateFragmentsContributor.class);
            assertThat(context).hasSingleBean(JpaDeleteFragmentsContributor.class);
        });
    }

    @Test
    void whenJpaDslEnabled_explicitly_contributorsAreRegistered() {
        contextRunner
                .withPropertyValues("jpadsl.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaSelectFragmentsContributor.class);
                    assertThat(context).hasSingleBean(JpaUpdateFragmentsContributor.class);
                    assertThat(context).hasSingleBean(JpaDeleteFragmentsContributor.class);
                });
    }

    @Test
    void whenJpaDslDisabled_noContributorsAreRegistered() {
        contextRunner
                .withPropertyValues("jpadsl.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JpaSelectFragmentsContributor.class);
                    assertThat(context).doesNotHaveBean(JpaUpdateFragmentsContributor.class);
                    assertThat(context).doesNotHaveBean(JpaDeleteFragmentsContributor.class);
                });
    }
}
