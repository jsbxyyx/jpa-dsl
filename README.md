# JPA DSL — Spring Data JPA Specification DSL SDK

[![Java](https://img.shields.io/badge/Java-17+-blue)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green)](https://spring.io/projects/spring-boot)

## 项目介绍

JPA DSL 是一个基于 **Spring Data JPA Specification** 的流式查询 DSL SDK，提供**编译期类型安全**的 API 来构建复杂的 JPA 查询条件。

- **类型安全**：所有字段引用使用 JPA Static Metamodel（如 `User_.status`），在编译期即可发现字段名拼写错误和类型不匹配。
- **流式链式 API**：`SpecificationBuilder` 支持链式调用，构建复杂的复合查询。
- **静态工厂 API**：`SpecificationDsl` 提供静态方法，适合 `import static` 简洁书写。
- **类型安全 Join**：支持 `SingularAttribute`（ManyToOne/OneToOne）和 `ListAttribute`/`SetAttribute`（OneToMany/ManyToMany）的类型安全 JOIN 操作。
- **分页排序**：`PageRequestBuilder` 支持通过 metamodel 属性排序。

---

## 快速开始

### 1. 配置 hibernate-jpamodelgen（必须）

在 `pom.xml` 中配置 `hibernate-jpamodelgen` 注解处理器，用于自动生成 `*_` 元模型类：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.hibernate.orm</groupId>
                <artifactId>hibernate-jpamodelgen</artifactId>
                <version>6.4.4.Final</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

配置后编译项目，Hibernate 会在 `target/generated-sources/annotations` 目录下自动生成如 `User_`、`Order_` 等元模型类。

### 2. 定义实体

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String status;
    private String role;

    @OneToMany(mappedBy = "user")
    private List<Order> orders;
    // ... getters/setters
}
```

### 3. 使用 SpecificationBuilder

```java
// 使用 User_.xxx 元模型属性，编译期类型安全
Specification<User> spec = SpecificationBuilder.<User>builder()
    .eq(User_.status, "ACTIVE")
    .like(User_.name, "John")       // 自动添加 %，即 %John%
    .gte(User_.age, 18)
    .build();

List<User> users = userRepository.findAll(spec);
```

### 4. 使用 SpecificationDsl（静态方法）

```java
import static io.github.jsbxyyx.jpadsl.SpecificationDsl.*;

Specification<User> spec = and(
    eq(User_.status, "ACTIVE"),
    or(
        gt(User_.age, 60),
        lt(User_.age, 18)
    )
);
```

### 5. 分页排序

```java
Pageable pageable = PageRequestBuilder.builder()
    .page(0)
    .size(20)
    .sortBy(User_.createdAt, Sort.Direction.DESC)   // 类型安全排序
    .sortBy(User_.name, Sort.Direction.ASC)
    .build();

Page<User> page = userRepository.findAll(spec, pageable);
```

---

## API 参考

### SpecificationBuilder / SpecificationDsl

| 方法 | 说明 | 示例 |
|------|------|------|
| `eq(attr, value)` | 等于；value 为任意值（含 null）时均加入条件 | `eq(User_.status, "ACTIVE")` |
| `eq(attr, value, condition)` | 仅当 condition 为 true 时应用 eq | `eq(User_.status, status, status != null)` |
| `ne(attr, value)` | 不等于；value 为任意值（含 null）时均加入条件 | `ne(User_.status, "DELETED")` |
| `ne(attr, value, condition)` | 仅当 condition 为 true 时应用 ne | `ne(User_.status, val, flag)` |
| `like(attr, value)` | 模糊匹配，自动包裹 `%`；value 为 null 时传入 null pattern | `like(User_.name, "John")` → `%John%` |
| `like(attr, value, condition)` | 仅当 condition 为 true 时应用 like | `like(User_.name, kw, kw != null)` |
| `likeIgnoreCase(attr, value)` | 不区分大小写模糊匹配 | `likeIgnoreCase(User_.name, "john")` |
| `likeIgnoreCase(attr, value, condition)` | 仅当 condition 为 true 时应用 likeIgnoreCase | |
| `in(attr, values)` | IN 集合（单值属性） | `in(User_.role, List.of("ADMIN", "USER"))` |
| `in(attr, values, condition)` | 仅当 condition 为 true 时应用 in | |
| `notIn(attr, values)` | NOT IN 集合 | `notIn(User_.role, List.of("GUEST"))` |
| `notIn(attr, values, condition)` | 仅当 condition 为 true 时应用 notIn | |
| `between(attr, lower, upper)` | 范围查询 | `between(User_.age, 18, 60)` |
| `between(attr, lower, upper, condition)` | 仅当 condition 为 true 时应用 between | |
| `gt(attr, value)` | 大于 | `gt(User_.age, 18)` |
| `gt(attr, value, condition)` | 仅当 condition 为 true 时应用 gt | |
| `gte(attr, value)` | 大于等于 | `gte(User_.age, 18)` |
| `gte(attr, value, condition)` | 仅当 condition 为 true 时应用 gte | |
| `lt(attr, value)` | 小于 | `lt(User_.age, 60)` |
| `lt(attr, value, condition)` | 仅当 condition 为 true 时应用 lt | |
| `lte(attr, value)` | 小于等于 | `lte(User_.age, 60)` |
| `lte(attr, value, condition)` | 仅当 condition 为 true 时应用 lte | |
| `isNull(attr)` | IS NULL | `isNull(User_.deletedAt)` |
| `isNull(attr, condition)` | 仅当 condition 为 true 时应用 isNull | |
| `isNotNull(attr)` | IS NOT NULL | `isNotNull(User_.email)` |
| `isNotNull(attr, condition)` | 仅当 condition 为 true 时应用 isNotNull | |
| `and(specs...)` | AND 组合 | `and(spec1, spec2)` |
| `or(specs...)` | OR 组合 | `or(spec1, spec2)` |
| `not(spec)` | NOT 取反 | `not(eq(User_.status, "ACTIVE"))` |

> **condition 参数重载：** 每个条件方法都提供带 `boolean condition` 参数的重载版本。当 `condition` 为 `false` 时，该条件被完全跳过（相当于未调用）；为 `true` 时，该条件无条件加入（包括 null 值）。
>
> 基础方法（不带 condition）与 condition 重载的区别：
>
> | 调用方式 | value=非null | value=null |
> |----------|-------------|------------|
> | `eq(attr, value)` | 加入条件 | **加入条件** |
> | `eq(attr, value, true)` | 加入条件 | 加入条件 |
> | `eq(attr, value, false)` | 跳过 | 跳过 |
>
> 若需要根据值是否为 null 决定是否加入条件，请使用 condition 重载：
>
> ```java
> String keyword = request.getKeyword(); // 可能为 null
> Specification<User> spec = SpecificationBuilder.<User>builder()
>     .eq(User_.status, "ACTIVE")
>     .like(User_.name, keyword, keyword != null)  // keyword 为 null 时整条件跳过
>     .build();
> ```

### Join 操作

```java
// ManyToOne join（如 Order.user）
Specification<Order> spec = SpecificationBuilder.<Order>builder()
    .join(Order_.user, JoinType.LEFT,
          (join, query, cb, predicates) ->
              predicates.add(cb.equal(join.get(User_.status), "ACTIVE")))
    .build();

// OneToMany join（如 User.orders）
Specification<User> spec = SpecificationBuilder.<User>builder()
    .join(User_.orders, JoinType.INNER,
          (join, query, cb, predicates) ->
              predicates.add(cb.equal(join.get(Order_.status), "PAID")))
    .build();
```

### JpaUpdateExecutor / UpdateBuilder — 批量 UPDATE

`JpaUpdateExecutor<T>` 是类比 `JpaSpecificationExecutor<T>` 的 Repository 混入接口，让 Repository 获得类型安全的批量 UPDATE 能力，**用户无需直接接触 `EntityManager`**。

#### 1. 声明 Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaUpdateExecutor<User> {   // ← 新增
}
```

#### 2. 在 Service 中使用

```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public int deactivateOldUsers() {
        UpdateBuilder<User> update = UpdateBuilder.<User>builder(User.class)
            .set(User_.status, "INACTIVE")      // SET 子句（null 值会将列置为 NULL）
            .eq(User_.status, "ACTIVE")         // WHERE 条件
            .lt(User_.age, 18)
            .build();
        return userRepository.executeUpdate(update);
    }
}
```

#### UpdateBuilder API

| 方法 | 说明 |
|------|------|
| `UpdateBuilder.builder(entityClass)` | 工厂方法，必须指定实体类 |
| `set(attr, value)` | 添加 SET 子句；`null` 值将列置为 NULL |
| `set(attr, value, condition)` | 仅当 condition 为 true 时添加 SET 子句 |
| `eq / ne / like / likeIgnoreCase` | WHERE 等值 / 不等值 / 模糊匹配条件 |
| `gt / gte / lt / lte` | WHERE 比较条件 |
| `between(attr, lower, upper)` | WHERE 范围条件 |
| `in / notIn(attr, values)` | WHERE IN / NOT IN 条件 |
| `isNull / isNotNull(attr)` | WHERE NULL 检查条件 |
| `build()` | 返回构建完成的 `UpdateBuilder`（传入 `executeUpdate()`）|

> SET 子句中 `null` 值会将数据库列置为 NULL。WHERE 条件中的 `null` 值与其他值一样无条件加入（生成 `= NULL` 表达式）。若需跳过某条件，请使用 condition 重载（传入 `false`）。

### JpaDeleteExecutor / DeleteBuilder — 批量 DELETE

`JpaDeleteExecutor<T>` 是类比 `JpaUpdateExecutor<T>` 的 Repository 混入接口，让 Repository 获得类型安全的批量 DELETE 能力，**用户无需直接接触 `EntityManager`**。

> **安全保护**：`DeleteBuilder` 要求至少有一个 WHERE 条件，否则调用 `delete()` 时抛出 `IllegalStateException`，防止意外全表删除。

#### 1. 声明 Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaDeleteExecutor<User> {   // ← 新增
}
```

#### 2. 在 Service 中使用

```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public int deleteInactiveUsers() {
        DeleteSpec<User> spec = DeleteBuilder.<User>builder(User.class)
            .eq(User_.status, "INACTIVE")   // WHERE 条件（至少一个）
            .lt(User_.age, 18)
            .build();
        return userRepository.delete(spec);
    }
}
```

#### DeleteBuilder API

| 方法 | 说明 |
|------|------|
| `DeleteBuilder.builder(entityClass)` | 工厂方法，必须指定实体类 |
| `eq / ne / like / likeIgnoreCase` | WHERE 等值 / 不等值 / 模糊匹配条件 |
| `gt / gte / lt / lte` | WHERE 比较条件 |
| `between(attr, lower, upper)` | WHERE 范围条件 |
| `in / notIn(attr, values)` | WHERE IN / NOT IN 条件 |
| `isNull / isNotNull(attr)` | WHERE NULL 检查条件 |
| `build()` | 返回构建完成的 `DeleteSpec`（传入 `delete()`）|

> 每个条件方法都提供带 `boolean condition` 参数的重载版本。`condition=false` 时条件被完全跳过；`condition=true` 时无条件加入。

### PageRequestBuilder

```java
Pageable pageable = PageRequestBuilder.builder()
    .page(0)                                              // 页码（从 0 开始）
    .size(20)                                             // 每页大小
    .sortBy(User_.createdAt, Sort.Direction.DESC)         // 类型安全排序
    .build();
```

---

## 复杂查询示例

```java
// 查询: 状态为 ACTIVE，年龄在 18-60 之间，邮箱不为空，名字包含 "John"
Specification<User> spec = SpecificationBuilder.<User>builder()
    .eq(User_.status, "ACTIVE")
    .between(User_.age, 18, 60)
    .isNotNull(User_.email)
    .like(User_.name, "John")
    .build();

// 使用静态方法嵌套 AND / OR
Specification<User> spec = and(
    eq(User_.status, "ACTIVE"),
    or(
        between(User_.age, 18, 25),
        gte(User_.age, 60)
    ),
    not(eq(User_.role, "GUEST"))
);

// 连表查询：查找关联活跃用户的订单
Specification<Order> spec = SpecificationBuilder.<Order>builder()
    .join(Order_.user, JoinType.INNER,
          (join, query, cb, predicates) -> {
              predicates.add(cb.equal(join.get(User_.status), "ACTIVE"));
              predicates.add(cb.gte(join.get(User_.age), 18));
          })
    .gte(Order_.amount, new BigDecimal("100.00"))
    .build();
```

---

## 项目结构

```
src/
├── main/java/io/github/jsbxyyx/jpadsl/
│   ├── SpecificationBuilder.java       # 主要链式构建器（推荐使用）
│   ├── SpecificationDsl.java           # 静态工厂方法
│   ├── PageRequestBuilder.java         # 分页排序构建器
│   ├── UpdateBuilder.java              # 批量 UPDATE 构建器
│   ├── UpdateSpec.java                 # UpdateBuilder#build() 的不可变产品对象
│   ├── JpaUpdateExecutor.java          # Repository 混入接口（UPDATE）
│   ├── JpaUpdateExecutorImpl.java      # 默认实现（内部注入 EntityManager）
│   ├── DeleteBuilder.java              # 批量 DELETE 构建器
│   ├── DeleteSpec.java                 # DeleteBuilder#build() 的不可变产品对象
│   ├── JpaDeleteExecutor.java          # Repository 混入接口（DELETE）
│   ├── JpaDeleteExecutorImpl.java      # 默认实现（内部注入 EntityManager）
│   ├── core/
│   │   ├── SpecificationBuilder.java   # core 包链式构建器
│   │   ├── SpecificationDsl.java       # core 包静态工厂
│   │   ├── PageRequestBuilder.java     # core 包分页构建器
│   │   ├── Criteria.java               # 查询条件封装（辅助 DTO）
│   │   ├── CriteriaType.java           # 查询条件类型枚举
│   │   └── JoinType.java               # JoinType 包装枚举
│   ├── spec/
│   │   ├── AbstractSpecification.java
│   │   ├── EqualSpecification.java
│   │   ├── LikeSpecification.java
│   │   └── ...（其他 Specification 实现类）
│   └── example/
│       ├── entity/                     # 示例实体（User / Order / OrderItem）
│       ├── repository/                 # 示例 Repository
│       └── service/                    # 示例 Service（演示 DSL 用法）
└── test/java/io/github/jsbxyyx/jpadsl/
    ├── SpecificationBuilderTest.java   # Builder API 集成测试
    ├── SpecificationDslTest.java       # DSL 静态方法集成测试
    ├── PageRequestBuilderTest.java     # 分页排序单元测试
    ├── UpdateBuilderTest.java          # 批量 UPDATE 集成测试
    ├── DeleteBuilderTest.java          # 批量 DELETE 集成测试
    ├── core/                           # core 包测试
    ├── example/                        # UserService 集成测试
    └── spec/                           # Specification 实现类测试
```

---

## 验收条件

- ✅ `./mvnw test` 通过（262 个测试全部通过）
- ✅ 无 `target/**`、`*.class` 被提交
- ✅ 无 `src/main/resources/application.properties`
- ✅ 包名根路径：`io.github.jsbxyyx.jpadsl`
- ✅ API 使用 JPA Static Metamodel（`User_.status` 等），编译期类型安全
- ✅ 比较 API 已缩写：`gt / lt / gte / lte`
- ✅ `like` 自动包裹 `%`
- ✅ `in` 只支持单值属性 + `Collection<V>`
- ✅ 支持类型安全 Join（`SingularAttribute` 和 `ListAttribute`/`SetAttribute`）
- ✅ `PageRequestBuilder.sortBy(attr, direction)` 使用 metamodel 属性
- ✅ `JpaUpdateExecutor<T>` 支持类型安全批量 UPDATE，用户无需注入 `EntityManager`
- ✅ `JpaDeleteExecutor<T>` 支持类型安全批量 DELETE，用户无需注入 `EntityManager`
- ✅ `DeleteBuilder` 安全保护：无 WHERE 条件时拒绝执行，防止意外全表删除

---

## jdbc-dsl — JdbcTemplate 自研 DSL

`jdbc-dsl` 是一套完全独立的 **JdbcTemplate DSL**，位于 `io.github.jsbxyyx.jdbcdsl` 包，不依赖、不引用 `jpa-dsl` 代码。

### 设计原则

- **仅使用 `SFunction` 方法引用**（`User::getName`），禁止字符串字段名，保证静态编译安全。
- **不接受 Spring `Pageable/Sort` 作为输入**；但提供 `toSpringPageable()` / `toSpringSort()` 输出适配方法。
- 通过读取 `jakarta.persistence` 注解（`@Entity/@Table/@Column/@Id`）建立表/列元信息。
- 使用 `NamedParameterJdbcTemplate` 执行参数化 SQL（`:p1, :p2, ...`）。

### 快速示例

```java
// 1. 创建执行器
JdbcDslExecutor executor = new JdbcDslExecutor(namedParameterJdbcTemplate);

// 2. 构建 SelectSpec — 字段引用全部使用方法引用
SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
        .select(User::getId, User::getUsername)
        .where(w -> w
                .eq(User::getStatus, "ACTIVE")
                .gt(User::getAge, 18, someCondition))
        .orderBy(JSort.byAsc(User::getUsername).andDesc(User::getAge))
        .mapTo(UserDto.class);

// 3. 查询列表
List<UserDto> list = executor.select(spec);

// 4. 分页查询
JPageable<User> pageable = JPageable.of(0, 10, JSort.byAsc(User::getUsername));
Page<UserDto> page = executor.selectPage(spec, pageable);

// 5. 导出为 Spring 分页（仅输出，不接受输入）
Pageable springPageable = pageable.toSpringPageable();
Sort springSort = JSort.byAsc(User::getUsername).toSpringSort();
```

### WHERE DSL

```java
.where(w -> w
    .eq(User::getStatus, "ACTIVE")           // =
    .ne(User::getStatus, "DELETED")          // <>
    .gt(User::getAge, 18)                    // >
    .gte(User::getAge, 18)                   // >=
    .lt(User::getAge, 60)                    // <
    .lte(User::getAge, 60)                   // <=
    .like(User::getUsername, "ali")          // LIKE '%ali%'
    .in(User::getStatus, List.of("A","B"))   // IN (...)
    .notIn(User::getStatus, List.of("D"))    // NOT IN (...)
    .between(User::getAge, 20, 40)           // BETWEEN ? AND ?
    .isNull(User::getEmail)                  // IS NULL
    .isNotNull(User::getEmail)               // IS NOT NULL
    .or(sub -> sub                           // OR 嵌套
            .eq(User::getStatus, "A")
            .eq(User::getStatus, "B"))
    // condition 重载：false 时跳过该条件
    .eq(User::getStatus, "ACTIVE", someBoolean)
)
```

### JOIN

```java
SelectSpec<Order, OrderDto> spec = SelectBuilder.from(Order.class, "o")
        .select(Order::getId, Order::getOrderNo, Order::getAmount)
        .join(User.class, "u", JoinType.INNER,
                ob -> ob.eq(Order::getUserId, "o", User::getId, "u"))
        // 跨表 WHERE：使用 eq(SFunction, alias, value) 重载
        .where(w -> w.eq(User::getStatus, "u", "ACTIVE"))
        .mapTo(OrderDto.class);
```

> **注意**：包含 JOIN 的 `selectPage` 使用 `COUNT(*)` 计数，可能因重复行导致计数偏高。
> 如需精确计数，请在业务层自行实现 `COUNT(DISTINCT ...)` 查询。

### 分页方言

| 方言 | SQL 语法 | 适用数据库 |
|------|---------|-----------|
| `Sql2008Dialect`（默认）| `OFFSET :_offset ROWS FETCH NEXT :_limit ROWS ONLY` | H2, PostgreSQL, SQL Server |
| `MySqlDialect` | `LIMIT :_limit OFFSET :_offset` | MySQL, MariaDB, H2 |

```java
// 使用 MySQL 方言
JdbcDslExecutor executor = new JdbcDslExecutor(jdbcTemplate, new MySqlDialect());
```

### DTO 投影

`JdbcDslExecutor` 通过**构造器投影**映射结果：

- SELECT 列别名为 `c0, c1, ...`，与 `select(...)` 的字段顺序一致。
- 自动查找参数数量匹配的构造器并注入结果。

```java
// DTO 构造器参数顺序必须与 select() 中的字段顺序一致
public class UserDto {
    public UserDto(Long id, String username) { ... }
}

SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
        .select(User::getId, User::getUsername)  // c0=id, c1=username
        .mapTo(UserDto.class);
```

### 列名映射规则

- 有 `@Column(name = "xxx")`：使用指定列名。
- 无 `@Column` 注解：列名默认等于 Java 属性名（**不**做 snake_case 自动转换）。
- 如需 snake_case 映射，请在实体字段上显式添加 `@Column(name = "xxx")`。

### 包结构

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
└── dialect/
    ├── Dialect.java            # 分页方言接口
    ├── MySqlDialect.java       # MySQL/H2 兼容方言
    └── Sql2008Dialect.java     # SQL:2008 标准方言（默认）
```
