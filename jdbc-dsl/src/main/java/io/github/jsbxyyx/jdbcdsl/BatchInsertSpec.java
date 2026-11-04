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
    private final List<ColumnSelection> selections;

    BatchInsertSpec(Class<T> entityClass, List<T> rows, List<ColumnSelection> selections) {
        this.entityClass = entityClass;
        this.rows = List.copyOf(rows);
        this.selections = List.copyOf(selections);
        this.columnNames =
                this.selections.stream().map(ColumnSelection::columnName).toList();
    }

    InsertSpec<T> insertSpec(EntityMeta meta) {
        return InsertSpec.fromSelections(entityClass, selections).resolve(meta);
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
     * 类型安全选择的列名为构建时的旧全局策略结果，执行器使用自身命名策略重新解析属性。
     */
    public List<String> getColumnNames() {
        return columnNames;
    }
}
