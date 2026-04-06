# jdbc-dsl 分支说明

## 分支用途

`jdbc-dsl` 是本仓库的**长期集成分支**，用于承载已合并到 `main` 的 JdbcTemplate DSL 功能集合。

所有 jdbc-dsl 相关代码位于 `io.github.jsbxyyx.jdbcdsl` 包中，与 `jpa-dsl`（`io.github.jsbxyyx.jpadsl`）**完全独立、互不影响**，可同时启用。

---

## 已实现功能

### 1. SFunction 方法引用（静态编译字段安全）

所有字段引用**必须使用 `SFunction` 方法引用**（如 `User::getName`），禁止字符串字段名，在编译期即可发现字段名拼写错误和类型不匹配。

```java
SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
        .select(User::getId, User::getUsername)
        .where(w -> w.eq(User::getStatus, "ACTIVE").gt(User::getAge, 18))
        .mapTo(UserDto.class);
```

### 2. JSort / JPageable（仅导出到 Spring，不接受从 Spring 输入）

- `JSort<T>`：类型安全排序，只接受 `SFunction` 方法引用，提供 `toSpringSort()` 输出适配。
- `JPageable<T>`：类型安全分页，只接受 `JSort<T>`，提供 `toSpringPageable()` 输出适配。
- **不提供** `fromSpringPageable` / `fromSpringSort` 反向输入，避免字符串字段破坏静态编译约束。

```java
JSort<User> sort = JSort.byAsc(User::getUsername).andDesc(User::getAge);
JPageable<User> pageable = JPageable.of(0, 10, sort);

Page<UserDto> page = executor.selectPage(spec, pageable);

// 仅作为输出适配（供 UI / 通用组件使用）
Pageable springPageable = pageable.toSpringPageable();
Sort springSort = sort.toSpringSort();
```

### 3. Dialect 根据 DataSource 自动识别

Spring Boot 自动配置（`JdbcDslAutoConfiguration`）会从 `DataSource` 的 JDBC 元数据自动识别数据库方言：

| 数据库产品 | 选用方言 | 分页语法 |
|-----------|---------|---------|
| MySQL | `MySqlDialect` | `LIMIT :_limit OFFSET :_offset` |
| MariaDB | `MySqlDialect`（与 MySQL 相同） | `LIMIT :_limit OFFSET :_offset` |
| 无法识别 | `MySqlDialect`（默认，输出 WARN 日志） | `LIMIT :_limit OFFSET :_offset` |

如需覆盖，在应用上下文中暴露自定义 `Dialect` Bean 即可。

### 4. Spring Boot 配置开关（互不影响）

两套 DSL 各自有独立的 Spring Boot 开关，默认均为启用：

```yaml
jpadsl:
  enabled: true   # 默认 true，设为 false 可禁用 jpa-dsl 自动配置

jdbcdsl:
  enabled: true   # 默认 true，设为 false 可禁用 jdbc-dsl 自动配置
```

- 同时启用时，`JpaSelectExecutor`（JPA 路径）和 `JdbcDslExecutor`（JDBC 路径）可同时注入使用，互不干扰。
- Bean 名称、类型、包名均完全隔离。

---

## 包结构

```
io.github.jsbxyyx.jdbcdsl/
├── SFunction.java              # 可序列化方法引用函数接口
├── PropertyRef.java            # 字段引用（ownerClass + propertyName）
├── PropertyRefResolver.java    # SFunction → PropertyRef 解析（含缓存）
├── EntityMeta.java             # 实体元数据（表名、列映射、ID）
├── EntityMetaReader.java       # 读取 @Table/@Column/@Id 注解（含缓存）
├── WhereBuilder.java           # WHERE 条件构建器
├── SelectBuilder.java          # SELECT DSL 入口
├── SelectSpec.java             # 不可变查询规格
├── JSort.java                  # 类型安全排序（SFunction 方法引用）
├── JPageable.java              # 类型安全分页
├── JOrder.java                 # 单字段排序项
├── JoinSpec.java               # JOIN 规格
├── JoinType.java               # INNER / LEFT / RIGHT
├── OnBuilder.java              # JOIN ON 条件构建器
├── RenderedSql.java            # 渲染结果（SQL + 参数）
├── SqlRenderer.java            # SQL 渲染引擎
├── JdbcDslExecutor.java        # 执行器
├── JdbcDslAutoConfiguration.java  # Spring Boot 自动配置（Dialect 自动识别）
└── dialect/
    ├── Dialect.java            # 分页方言接口
    ├── MySqlDialect.java       # MySQL/MariaDB 方言
    └── Sql2008Dialect.java     # SQL:2008 标准方言
```

---

## 相关文档

- 详细用法见 [README.md](../README.md#jdbc-dsl--jdbctemplate-自研-dsl)
