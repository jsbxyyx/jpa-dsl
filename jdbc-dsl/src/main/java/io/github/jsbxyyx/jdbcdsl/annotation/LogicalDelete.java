package io.github.jsbxyyx.jdbcdsl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为逻辑删除标志列。
 *
 * <p>当 {@link io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor#executeLogicalDelete} 被调用时，
 * 执行器会将该列更新为 {@link #deletedValue()}，而非真正删除行。
 *
 * <p>当 {@code jdbcdsl.logical-delete-auto-filter=true}（默认）时，SELECT 查询会自动附加
 * {@code AND col = normalValue} 以过滤已逻辑删除的行。
 *
 * <p>每个实体类最多只能有一个字段标注本注解。
 *
 * <p>示例用法：
 * <pre>{@code
 * @Column(name = "deleted")
 * @LogicalDelete(deletedValue = "1", normalValue = "0")
 * private Integer deleted;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogicalDelete {

    /**
     * 写入数据库时表示"已删除"的值。默认 {@code "1"}。
     */
    String deletedValue() default "1";

    /**
     * 写入数据库时表示"未删除"的值。默认 {@code "0"}。
     */
    String normalValue() default "0";
}
