package io.github.jsbxyyx.jdbcdsl;

import java.time.LocalDateTime;

/**
 * 可插拔的时间提供者接口，用于 INSERT / UPDATE 自动填充时间戳字段。
 *
 * <p>默认实现为 {@code LocalDateTime::now}。
 * 在测试中可注入固定时间以保证断言的确定性：
 *
 * <pre>{@code
 * LocalDateTime fixedTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
 * executor.setTimeProvider(() -> fixedTime);
 * }</pre>
 *
 * @see JdbcDslExecutor#setTimeProvider(TimeProvider)
 */
@FunctionalInterface
public interface TimeProvider {

    /**
     * 返回当前时间。
     *
     * @return 当前 {@link LocalDateTime}
     */
    LocalDateTime now();
}
