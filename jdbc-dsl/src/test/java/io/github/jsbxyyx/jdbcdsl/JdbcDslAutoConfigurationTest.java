package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.H2Dialect;
import io.github.jsbxyyx.jdbcdsl.dialect.MySqlDialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import jakarta.persistence.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link io.github.jsbxyyx.jdbcdsl.spring.JdbcDslAutoConfiguration}: enabled/disabled switches and Dialect auto-detection.
 */
class JdbcDslAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    io.github.jsbxyyx.jdbcdsl.spring.JdbcDslAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:jdbcdsl_autoconfig_test;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    @AfterEach
    void resetJdbcDslConfig() {
        // Reset the static config after each test to avoid cross-test pollution
        JdbcDslConfig.setAllowEmptyWhere(false);
        JdbcDslConfig.setAllowNullToPrimitiveDefault(false);
        JdbcDslConfig.setNamingStrategy(DefaultNamingStrategy.INSTANCE);
    }

    @Test
    void primitiveNullDefaultMappingIsDisabledByDefaultAndPropertyConfigurable() {
        contextRunner.run(context -> {
            assertThat(context.getBean(io.github.jsbxyyx.jdbcdsl.spring.JdbcDslProperties.class)
                            .isAllowNullToPrimitiveDefault())
                    .isFalse();
            assertThat(JdbcDslConfig.isAllowNullToPrimitiveDefault()).isFalse();
        });

        contextRunner
                .withPropertyValues("jdbcdsl.allow-null-to-primitive-default=true")
                .run(context -> {
                    assertThat(context.getBean(io.github.jsbxyyx.jdbcdsl.spring.JdbcDslProperties.class)
                                    .isAllowNullToPrimitiveDefault())
                            .isTrue();
                    assertThat(JdbcDslConfig.isAllowNullToPrimitiveDefault()).isTrue();
                });
    }

    @Test
    void mappingPropertiesApplyToExecutor() {
        contextRunner
                .withPropertyValues(
                        "jdbcdsl.mapping-mode=mybatis", "jdbcdsl.auto-mapping-unknown-column-behavior=failing")
                .run(context -> {
                    JdbcDslExecutor executor = context.getBean(JdbcDslExecutor.class);
                    assertThat(executor.queryOne(
                                    "SELECT CAST(42.5 AS DECIMAL(4,1))", java.util.Map.of(), Integer.class))
                            .isEqualTo(43);
                    assertThatThrownBy(() -> executor.queryOne(
                                    "SELECT 'alice' AS username, 1 AS extra", java.util.Map.of(), TUser.class))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("EXTRA");
                });
    }

    @Test
    void twoContextsKeepIndependentNullPolicies() {
        contextRunner
                .withPropertyValues("jdbcdsl.allow-null-to-primitive-default=false")
                .run(first -> contextRunner
                        .withPropertyValues("jdbcdsl.allow-null-to-primitive-default=true")
                        .run(second -> {
                            JdbcDslExecutor strict = first.getBean(JdbcDslExecutor.class);
                            JdbcDslExecutor permissive = second.getBean(JdbcDslExecutor.class);
                            assertThat(permissive.queryOne("SELECT NULL", java.util.Map.of(), int.class))
                                    .isZero();
                            assertThatThrownBy(() -> strict.queryOne("SELECT NULL", java.util.Map.of(), int.class))
                                    .isInstanceOf(IllegalStateException.class);
                        }));
    }

    @Test
    void contextsKeepNamingAndWriteProtectionIndependent() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:context_default;DB_CLOSE_DELAY=-1",
                        "jdbcdsl.naming-strategy=default",
                        "jdbcdsl.allow-empty-where=false")
                .run(first -> {
                    JdbcDslExecutor strict = first.getBean(JdbcDslExecutor.class);
                    NamedParameterJdbcTemplate firstJdbc = first.getBean(NamedParameterJdbcTemplate.class);
                    firstJdbc
                            .getJdbcTemplate()
                            .execute(
                                    "CREATE TABLE AccountItem (id BIGINT PRIMARY KEY, naturalKey VARCHAR(30) UNIQUE, displayName VARCHAR(30))");
                    InsertSpec<AccountItem> insert = InsertBuilder.into(AccountItem.class)
                            .columns(AccountItem::getId, AccountItem::getNaturalKey, AccountItem::getDisplayName)
                            .build();
                    BatchInsertSpec<AccountItem> batch = BatchInsertBuilder.of(
                                    AccountItem.class,
                                    List.of(new AccountItem(2L, "b", "two"), new AccountItem(3L, "c", "three")))
                            .columns(AccountItem::getId, AccountItem::getNaturalKey, AccountItem::getDisplayName)
                            .build();
                    UpsertSpec<AccountItem> upsert = UpsertBuilder.into(AccountItem.class)
                            .onConflict(AccountItem::getNaturalKey)
                            .doUpdate(AccountItem::getDisplayName)
                            .build();
                    SelectSpec<AccountItem, AccountItem> select = SelectBuilder.from(AccountItem.class)
                            .where(w -> w.eq(AccountItem::getNaturalKey, "a"))
                            .mapToEntity();
                    SelectSpec<AccountItem, AccountItem> joined = SelectBuilder.from(AccountItem.class, "a")
                            .select(AccountItem.class, "a")
                            .join(
                                    AccountItem.class,
                                    "b",
                                    JoinType.INNER,
                                    on -> on.eq(AccountItem::getNaturalKey, "a", AccountItem::getNaturalKey, "b"))
                            .where(w -> w.eq(AccountItem::getNaturalKey, "a", "a"))
                            .mapToEntity();
                    SelectSpec<AccountItem, Long> inner = SelectBuilder.from(AccountItem.class)
                            .select(AccountItem::getId)
                            .where(w -> w.eq(AccountItem::getNaturalKey, "a"))
                            .mapTo(Long.class);
                    SelectSpec<AccountItem, AccountItem> nested = SelectBuilder.from(AccountItem.class)
                            .where(w -> w.inSubquery(col(AccountItem::getId), inner))
                            .mapToEntity();
                    contextRunner
                            .withPropertyValues(
                                    "spring.datasource.url=jdbc:h2:mem:context_snake;DB_CLOSE_DELAY=-1",
                                    "jdbcdsl.naming-strategy=snake_case",
                                    "jdbcdsl.allow-empty-where=true")
                            .run(second -> {
                                JdbcDslExecutor permissive = second.getBean(JdbcDslExecutor.class);
                                second.getBean(NamedParameterJdbcTemplate.class)
                                        .getJdbcTemplate()
                                        .execute(
                                                "CREATE TABLE account_item (id BIGINT PRIMARY KEY, natural_key VARCHAR(30) UNIQUE, display_name VARCHAR(30))");
                                for (JdbcDslExecutor executor : List.of(strict, permissive)) {
                                    executor.save(insert, new AccountItem(1L, "a", "one"));
                                    assertThat(executor.executeBatchInsert(batch))
                                            .containsExactly(1, 1);
                                    executor.upsert(upsert, new AccountItem(1L, "a", "changed"));
                                    assertThat(executor.select(select).get(0).getDisplayName())
                                            .isEqualTo("changed");
                                    assertThat(executor.count(joined)).isEqualTo(1);
                                    assertThat(executor.select(joined)).hasSize(1);
                                    assertThat(executor.select(nested)).hasSize(1);
                                    assertThat(executor.select(SelectBuilder.from(AccountItem.class)
                                                    .select(AccountItem::getDisplayName)
                                                    .where(w -> w.eq(AccountItem::getNaturalKey, "a"))
                                                    .mapTo(String.class)))
                                            .containsExactly("changed");
                                    assertThat(executor.union(UnionSpec.of(select)
                                                    .union(select)
                                                    .build()))
                                            .hasSize(1);
                                    assertThat(executor.executeUpdate(executor.updateBuilder(AccountItem.class)
                                                    .set(AccountItem::getDisplayName, "updated")
                                                    .eq(AccountItem::getNaturalKey, "b")
                                                    .build()))
                                            .isEqualTo(1);
                                    assertThat(executor.executeBatchUpdate(
                                                    List.of(executor.updateBuilder(AccountItem.class)
                                                            .set(AccountItem::getDisplayName, "batch")
                                                            .eq(AccountItem::getNaturalKey, "b")
                                                            .build())))
                                            .containsExactly(1);
                                    assertThat(executor.executeBatchDelete(
                                                    List.of(executor.deleteBuilder(AccountItem.class)
                                                            .eq(AccountItem::getNaturalKey, "c")
                                                            .build())))
                                            .containsExactly(1);
                                }
                                strict.save(
                                        InsertBuilder.into(AccountItem.class)
                                                .columns("id", "naturalKey")
                                                .columns(AccountItem::getDisplayName)
                                                .build(),
                                        new AccountItem(4L, "d", "raw"));
                                permissive.save(
                                        InsertBuilder.into(AccountItem.class)
                                                .columns("id", "natural_key")
                                                .columns(AccountItem::getDisplayName)
                                                .build(),
                                        new AccountItem(4L, "d", "raw"));
                                for (JdbcDslExecutor executor : List.of(strict, permissive)) {
                                    assertThat(executor.executeDelete(executor.deleteBuilder(AccountItem.class)
                                                    .eq(AccountItem::getNaturalKey, "d")
                                                    .build()))
                                            .isEqualTo(1);
                                }
                                DeleteSpec<AccountItem> all = permissive
                                        .deleteBuilder(AccountItem.class)
                                        .build();
                                UpdateSpec<AccountItem> updateAll = permissive
                                        .updateBuilder(AccountItem.class)
                                        .set(AccountItem::getDisplayName, "all")
                                        .build();
                                assertThatThrownBy(() -> strict.deleteBuilder(AccountItem.class)
                                                .build())
                                        .isInstanceOf(IllegalStateException.class);
                                assertThatThrownBy(() -> strict.executeDelete(all))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThatThrownBy(() -> strict.executeUpdate(updateAll))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThatThrownBy(() -> strict.executeBatchDelete(List.of(all)))
                                        .isInstanceOf(IllegalStateException.class);
                                UpdateSpec<AccountItem> validUpdate = strict.updateBuilder(AccountItem.class)
                                        .set(AccountItem::getDisplayName, "should-not-execute")
                                        .eq(AccountItem::getNaturalKey, "b")
                                        .build();
                                assertThatThrownBy(() -> strict.executeBatchUpdate(List.of(validUpdate, updateAll)))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThat(strict.select(SelectBuilder.from(AccountItem.class)
                                                        .where(w -> w.eq(AccountItem::getNaturalKey, "b"))
                                                        .mapToEntity())
                                                .get(0)
                                                .getDisplayName())
                                        .isEqualTo("batch");
                                assertThatThrownBy(() -> strict.executeLogicalDelete(all))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThatThrownBy(() -> strict.executeDeleteReturning(
                                                all, AccountItem.class, AccountItem::getId))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThatThrownBy(() -> strict.executeUpdateReturning(
                                                updateAll, AccountItem.class, AccountItem::getId))
                                        .isInstanceOf(IllegalStateException.class);
                                runConcurrentSelects(strict, permissive, select);
                                assertThat(permissive.executeUpdate(updateAll)).isEqualTo(2);
                                assertThat(permissive.executeDelete(all)).isEqualTo(2);
                                assertThat(strict.count(select)).isEqualTo(1);
                            });
                });
    }

    private static void runConcurrentSelects(
            JdbcDslExecutor strict, JdbcDslExecutor permissive, SelectSpec<AccountItem, AccountItem> spec)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                JdbcDslExecutor executor = i % 2 == 0 ? strict : permissive;
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < 20; j++) {
                        assertThat(executor.select(spec).get(0).getDisplayName())
                                .isEqualTo("changed");
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    static class AccountItem {
        @Id
        private Long id;

        private String naturalKey;
        private String displayName;

        public AccountItem() {}

        AccountItem(Long id, String naturalKey, String displayName) {
            this.id = id;
            this.naturalKey = naturalKey;
            this.displayName = displayName;
        }

        public Long getId() {
            return id;
        }

        public String getNaturalKey() {
            return naturalKey;
        }

        public String getDisplayName() {
            return displayName;
        }
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
        contextRunner.withPropertyValues("jdbcdsl.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(JdbcDslExecutor.class);
            assertThat(context).hasSingleBean(Dialect.class);
        });
    }

    @Test
    void whenJdbcDslDisabled_noBeansAreRegistered() {
        contextRunner.withPropertyValues("jdbcdsl.enabled=false").run(context -> {
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
        contextRunner.withUserConfiguration(CustomDialectConfig.class).run(context -> {
            assertThat(context).hasSingleBean(Dialect.class);
            assertThat(context.getBean(Dialect.class)).isInstanceOf(MySqlDialect.class);
            // JdbcDslExecutor should still be created using the custom dialect
            assertThat(context).hasSingleBean(JdbcDslExecutor.class);
        });
    }

    @Test
    void whenCustomExecutorProvided_autoConfigExecutorIsSkipped() {
        contextRunner.withUserConfiguration(CustomExecutorConfig.class).run(context -> {
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
            java.lang.reflect.Field dialectField = JdbcDslExecutor.class.getDeclaredField("dialect");
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
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, () -> UpdateBuilder.from(TUser.class)
                            .set(TUser::getStatus, "X")
                            .build());
        });
    }

    @Test
    void allowEmptyWhere_setTrue_updateBuilder_noWhere_doesNotThrow() {
        contextRunner.withPropertyValues("jdbcdsl.allow-empty-where=true").run(context -> {
            // After context loads, JdbcDslConfig.isAllowEmptyWhere() must be true
            assertThat(JdbcDslConfig.isAllowEmptyWhere()).isTrue();
            UpdateSpec<?> spec =
                    UpdateBuilder.from(TUser.class).set(TUser::getStatus, "X").build();
            assertThat(spec).isNotNull();
        });
    }

    @Test
    void allowEmptyWhere_setTrue_deleteBuilder_noWhere_doesNotThrow() {
        contextRunner.withPropertyValues("jdbcdsl.allow-empty-where=true").run(context -> {
            DeleteSpec<?> spec = DeleteBuilder.from(TUser.class).build();
            assertThat(spec).isNotNull();
        });
    }

    @Test
    void allowEmptyWhere_defaultFalse_deleteBuilder_noWhere_throwsIllegalState() {
        contextRunner.run(context -> {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> DeleteBuilder.from(TUser.class).build());
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
        public JdbcDslExecutor myExecutor(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
            return new JdbcDslExecutor(jdbc, new MySqlDialect());
        }
    }
}
