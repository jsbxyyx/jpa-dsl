package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void resetJdbcDslConfig() {
        // Reset the static config after each test to avoid cross-test pollution
        JdbcDslConfig.setAllowEmptyWhere(false);
    }

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
    //  JdbcDslExecutor single-arg constructor dialect detection
    // ------------------------------------------------------------------ //

    /**
     * {@code new JdbcDslExecutor(jdbc)} must auto-detect the dialect from the DataSource instead
     * of hard-coding {@link io.github.jsbxyyx.jdbcdsl.dialect.Sql2008Dialect}. When the
     * DataSource is H2, the constructor should pick {@link H2Dialect}.
     */
    @Test
    void singleArgConstructor_withH2DataSource_usesH2Dialect() {
        contextRunner.run(context -> {
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc =
                    context.getBean(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);

            // Build executor using the single-arg constructor — dialect should be auto-detected
            JdbcDslExecutor executorUnderTest = new JdbcDslExecutor(jdbc);

            // Verify via the publicly observable behaviour: H2Dialect uses LIMIT/OFFSET syntax,
            // Sql2008Dialect uses "OFFSET … ROWS FETCH NEXT … ROWS ONLY".
            // We inspect the Dialect directly through reflection to keep the test simple.
            java.lang.reflect.Field dialectField =
                    JdbcDslExecutor.class.getDeclaredField("dialect");
            dialectField.setAccessible(true);
            Dialect detected = (Dialect) dialectField.get(executorUnderTest);
            assertThat(detected).isInstanceOf(H2Dialect.class);
        });
    }

    // ------------------------------------------------------------------ //
    //  jdbcdsl.allow-empty-where global switch
    // ------------------------------------------------------------------ //

    @Test
    void allowEmptyWhere_defaultFalse_updateBuilder_noWhere_throwsIllegalState() {
        // Default: JdbcDslConfig.allowEmptyWhere = false; UpdateBuilder.build() without WHERE must throw
        contextRunner.run(context -> {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                    UpdateBuilder.from(TUser.class)
                            .set(TUser::getStatus, "X")
                            .build()
            );
        });
    }

    @Test
    void allowEmptyWhere_setTrue_updateBuilder_noWhere_doesNotThrow() {
        contextRunner
                .withPropertyValues("jdbcdsl.allow-empty-where=true")
                .run(context -> {
                    // After context loads, JdbcDslConfig.isAllowEmptyWhere() must be true
                    assertThat(JdbcDslConfig.isAllowEmptyWhere()).isTrue();
                    UpdateSpec<?> spec = UpdateBuilder.from(TUser.class)
                            .set(TUser::getStatus, "X")
                            .build();
                    assertThat(spec).isNotNull();
                });
    }

    @Test
    void allowEmptyWhere_setTrue_deleteBuilder_noWhere_doesNotThrow() {
        contextRunner
                .withPropertyValues("jdbcdsl.allow-empty-where=true")
                .run(context -> {
                    DeleteSpec<?> spec = DeleteBuilder.from(TUser.class)
                            .build();
                    assertThat(spec).isNotNull();
                });
    }

    @Test
    void allowEmptyWhere_defaultFalse_deleteBuilder_noWhere_throwsIllegalState() {
        contextRunner.run(context -> {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                    DeleteBuilder.from(TUser.class).build()
            );
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
