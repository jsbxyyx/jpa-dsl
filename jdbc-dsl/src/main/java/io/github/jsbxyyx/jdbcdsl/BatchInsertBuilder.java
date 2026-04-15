package io.github.jsbxyyx.jdbcdsl;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量 INSERT 的流式构建器，用于创建 {@link BatchInsertSpec}。
 *
 * <p>示例用法（类型安全方法引用）：
 * <pre>{@code
 * BatchInsertSpec<TUser> spec = BatchInsertBuilder.of(TUser.class, userList)
 *     .columns(TUser::getUsername, TUser::getEmail)
 *     .build();
 * int[] affected = executor.executeBatchInsert(spec);
 * }</pre>
 *
 * <p>示例用法（不指定列名，插入全部列）：
 * <pre>{@code
 * BatchInsertSpec<TUser> spec = BatchInsertBuilder.of(TUser.class, userList).build();
 * int[] affected = executor.executeBatchInsert(spec);
 * }</pre>
 *
 * @param <T> 实体类型
 */
public final class BatchInsertBuilder<T> {

    private final Class<T> entityClass;
    private final List<T> rows;
    private final List<String> columnNames = new ArrayList<>();

    private BatchInsertBuilder(Class<T> entityClass, List<T> rows) {
        this.entityClass = entityClass;
        this.rows = rows;
    }

    /**
     * 创建针对给定实体类和行列表的批量插入构建器。
     *
     * @param entityClass 实体类
     * @param rows        待插入的行列表
     * @param <T>         实体类型
     * @return 新的 {@link BatchInsertBuilder}
     */
    public static <T> BatchInsertBuilder<T> of(Class<T> entityClass, List<T> rows) {
        return new BatchInsertBuilder<>(entityClass, rows);
    }

    /**
     * 通过类型安全方法引用指定要插入的列。
     *
     * @param props getter 方法引用，用于标识要插入的列
     * @return {@code this}（链式调用）
     */
    @SafeVarargs
    public final BatchInsertBuilder<T> columns(SFunction<T, ?>... props) {
        EntityMeta meta = EntityMetaReader.read(entityClass);
        for (SFunction<T, ?> prop : props) {
            PropertyRef ref = PropertyRefResolver.resolve(prop);
            String colName = meta.getColumnName(ref.propertyName());
            columnNames.add(colName != null ? colName : ref.propertyName());
        }
        return this;
    }

    /**
     * 构建 {@link BatchInsertSpec}。
     *
     * @return 不可变的批量插入规格描述符
     */
    public BatchInsertSpec<T> build() {
        return new BatchInsertSpec<>(entityClass, rows, columnNames);
    }
}
