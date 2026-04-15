package io.github.jsbxyyx.jdbcdsl;

import java.util.List;

/**
 * 批量 INSERT 的不可变规格描述符。
 *
 * <p>若 {@link #getColumnNames()} 为空，则使用实体全部列（自动排除 IDENTITY 生成的主键）。
 * 若指定了列名，则仅插入指定列。
 *
 * <p>通过 {@link BatchInsertBuilder} 创建实例：
 * <pre>{@code
 * BatchInsertSpec<TUser> spec = BatchInsertBuilder.of(TUser.class, userList).build();
 * int[] affected = executor.executeBatchInsert(spec);
 * }</pre>
 *
 * @param <T> 实体类型
 */
public final class BatchInsertSpec<T> {

    private final Class<T> entityClass;
    private final List<T> rows;
    private final List<String> columnNames;

    BatchInsertSpec(Class<T> entityClass, List<T> rows, List<String> columnNames) {
        this.entityClass = entityClass;
        this.rows = List.copyOf(rows);
        this.columnNames = List.copyOf(columnNames);
    }

    /** 实体类。 */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /** 待插入的行列表（不可变副本）。 */
    public List<T> getRows() {
        return rows;
    }

    /**
     * 显式指定的列名列表，若为空则使用实体全部列（排除 IDENTITY 主键）。
     */
    public List<String> getColumnNames() {
        return columnNames;
    }
}
