package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcDslAutoConfiguration}: enabled/disabled switches and Dialect auto-detection.
 */
class JdbcDslAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    JdbcDslAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:jdbcdsl_autoconfig_test;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    // ------------------------------------------------------------------ //
    //  jdbcdsl.enabled switch
    // ------------------------------------------------------------------ //

    @Test
    void whenJdbcDslEnabled_byDefault_beansAreRegistered() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JdbcDslExecutor.class);
            assertThat(context).hasSingleBean(Dialect.class);
        });
    }

    @Test
    void whenJdbcDslEnabled_explicitly_beansAreRegistered() {
        contextRunner
                .withPropertyValues("jdbcdsl.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcDslExecutor.class);
                    assertThat(context).hasSingleBean(Dialect.class);
                });
    }

    @Test
    void whenJdbcDslDisabled_noBeansAreRegistered() {
        contextRunner
                .withPropertyValues("jdbcdsl.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcDslExecutor.class);
                    assertThat(context).doesNotHaveBean(Dialect.class);
                });
    }

    // ------------------------------------------------------------------ //
    //  Dialect auto-detection
    // ------------------------------------------------------------------ //

    @Test
    void dialectDetection_withH2_returnsH2Dialect() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Dialect.class);
            assertThat(context.getBean(Dialect.class)).isInstanceOf(H2Dialect.class);
        });
    }

    @Test
    void whenCustomDialectProvided_autoDetectionIsSkipped() {
        contextRunner
                .withUserConfiguration(CustomDialectConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Dialect.class);
                    assertThat(context.getBean(Dialect.class)).isInstanceOf(MySqlDialect.class);
                    // JdbcDslExecutor should still be created using the custom dialect
                    assertThat(context).hasSingleBean(JdbcDslExecutor.class);
                });
    }

    @Test
    void whenCustomExecutorProvided_autoConfigExecutorIsSkipped() {
        contextRunner
                .withUserConfiguration(CustomExecutorConfig.class)
                .run(context -> {
                    // Only one executor bean (the custom one)
                    assertThat(context).hasSingleBean(JdbcDslExecutor.class);
                });
    }

    // ------------------------------------------------------------------ //
    //  Helper configurations
    // ------------------------------------------------------------------ //

    @Configuration
    static class CustomDialectConfig {
        @Bean
        public Dialect dialect() {
            return new MySqlDialect();
        }
    }

    @Configuration
    static class CustomExecutorConfig {
        @Bean
        public JdbcDslExecutor myExecutor(
                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
            return new JdbcDslExecutor(jdbc, new MySqlDialect());
        }
    }
}
