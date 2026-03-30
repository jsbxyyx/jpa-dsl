# JPA DSL - Spring Data JPA Specification DSL 框架

## 项目介绍

JPA DSL 是一个基于 Spring Data JPA Specification 的流式查询 DSL 框架，提供了简洁、可读性强的 API 来构建复杂的 JPA 查询条件，避免了手写 Specification 的繁琐样板代码。

### 核心特性

- **SpecificationBuilder**：链式构建器，支持流式 API 组合多个查询条件
- **SpecificationDsl**：静态工厂方法，快速创建单个 Specification 实例
- **PageRequestBuilder**：分页排序构建器，简化 `PageRequest` 的创建
- **15 种内置 Specification**：覆盖常见查询场景（等于、不等于、模糊、范围、IN、IS NULL 等）
- **嵌套路径支持**：支持 `"address.city"` 这样的嵌套属性路径
- **逻辑组合**：支持 AND、OR、NOT 组合

---

## 项目结构

```
src/
├── main/java/io/github/jsbxyyx/jpadsl/
│   ├── core/
│   │   ├── Criteria.java                   # 查询条件封装
│   │   ├── CriteriaType.java               # 查询条件类型枚举
│   │   ├── JoinType.java                   # Join 类型枚举（包装 JPA JoinType）
│   │   ├── PageRequestBuilder.java         # 分页排序构建器
│   │   ├── SpecificationBuilder.java       # 链式 Specification 构建器
│   │   └── SpecificationDsl.java           # Specification 静态工厂方法
│   ├── spec/
│   │   ├── AbstractSpecification.java      # 抽象基类（含嵌套路径解析）
│   │   ├── AndSpecification.java
│   │   ├── BetweenSpecification.java
│   │   ├── EqualSpecification.java
│   │   ├── GreaterThanOrEqualSpecification.java
│   │   ├── GreaterThanSpecification.java
│   │   ├── InSpecification.java
│   │   ├── IsNotNullSpecification.java
│   │   ├── IsNullSpecification.java
│   │   ├── LessThanOrEqualSpecification.java
│   │   ├── LessThanSpecification.java
│   │   ├── LikeSpecification.java
│   │   ├── NotEqualSpecification.java
│   │   ├── NotSpecification.java
│   │   └── OrSpecification.java
│   └── example/
│       ├── entity/
│       │   ├── User.java
│       │   ├── Order.java
│       │   └── OrderItem.java
│       ├── repository/
│       │   ├── UserRepository.java
│       │   ├── OrderRepository.java
│       │   └── OrderItemRepository.java
│       └── service/
│           └── UserService.java            # DSL 使用示例
└── test/java/io/github/jsbxyyx/jpadsl/
    ├── core/
    │   ├── PageRequestBuilderTest.java
    │   └── SpecificationBuilderTest.java
    ├── spec/
    │   └── SpecificationTest.java
    └── example/
        └── UserServiceTest.java
```

---

## 核心 DSL API

### 支持的查询条件类型

| 方法 | 说明 | 示例 |
|------|------|------|
| `equal(field, value)` | 等于 | `equal("status", "ACTIVE")` |
| `notEqual(field, value)` | 不等于 | `notEqual("status", "DELETED")` |
| `like(field, value)` | 模糊匹配（自动添加 `%`） | `like("name", "John")` |
| `in(field, values)` | IN 集合 | `in("role", Arrays.asList("ADMIN", "USER"))` |
| `between(field, lower, upper)` | 范围查询 | `between("age", 18, 60)` |
| `greaterThan(field, value)` | 大于 | `greaterThan("age", 18)` |
| `lessThan(field, value)` | 小于 | `lessThan("age", 60)` |
| `greaterThanOrEqual(field, value)` | 大于等于 | `greaterThanOrEqual("age", 18)` |
| `lessThanOrEqual(field, value)` | 小于等于 | `lessThanOrEqual("age", 60)` |
| `isNull(field)` | IS NULL | `isNull("deletedAt")` |
| `isNotNull(field)` | IS NOT NULL | `isNotNull("email")` |
| `and(specs...)` | AND 组合 | `and(spec1, spec2)` |
| `or(specs...)` | OR 组合 | `or(spec1, spec2)` |
| `not(spec)` | NOT 取反 | `not(equal("status", "ACTIVE"))` |

---

## SpecificationBuilder 使用

`SpecificationBuilder` 提供链式 API，所有条件默认以 **AND** 组合：

```java
// 基本用法
Specification<User> spec = SpecificationBuilder.<User>builder()
    .equal("status", "ACTIVE")
    .like("name", "John")
    .greaterThanOrEqual("age", 18)
    .build();

List<User> users = userRepository.findAll(spec);
```

```java
// 组合 OR 条件
Specification<User> spec = SpecificationBuilder.<User>builder()
    .equal("status", "ACTIVE")
    .or(
        SpecificationDsl.equal("role", "ADMIN"),
        SpecificationDsl.equal("role", "MANAGER")
    )
    .build();
```

```java
// 使用 NOT
Specification<User> spec = SpecificationBuilder.<User>builder()
    .not(SpecificationDsl.equal("status", "DELETED"))
    .build();
```

```java
// IN 查询
Specification<User> spec = SpecificationBuilder.<User>builder()
    .in("status", Arrays.asList("ACTIVE", "PENDING"))
    .build();
```

```java
// 范围查询
Specification<User> spec = SpecificationBuilder.<User>builder()
    .between("age", 18, 65)
    .build();
```

```java
// 带 JOIN 的查询
Specification<User> spec = SpecificationBuilder.<User>builder()
    .join("orders", JoinType.LEFT)
    .equal("status", "ACTIVE")
    .build();
```

```java
// 与分页结合
PageRequest pageRequest = PageRequestBuilder.builder()
    .page(0)
    .size(20)
    .sortBy("createdAt", Sort.Direction.DESC)
    .build();

Page<User> page = userRepository.findAll(spec, pageRequest);
```

---

## SpecificationDsl 使用

`SpecificationDsl` 提供静态工厂方法，可与 Spring Data JPA 的 `Specification` 链式方法结合使用：

```java
// 直接使用静态方法
Specification<User> spec = SpecificationDsl.equal("status", "ACTIVE");

// 利用 Specification 自带的 and/or/not 链式方法
Specification<User> spec = SpecificationDsl.<User>equal("status", "ACTIVE")
    .and(SpecificationDsl.greaterThan("age", 18))
    .and(SpecificationDsl.like("name", "John"));
```

```java
// 嵌套路径（支持关联属性）
Specification<User> spec = SpecificationDsl.equal("address.city", "Beijing");
```

---

## PageRequestBuilder 使用

```java
// 简单分页
PageRequest pageRequest = PageRequestBuilder.builder()
    .page(0)
    .size(10)
    .build();

// 带多字段排序的分页
PageRequest pageRequest = PageRequestBuilder.builder()
    .page(0)
    .size(20)
    .sortBy("createdAt", Sort.Direction.DESC)
    .sortBy("name", Sort.Direction.ASC)
    .build();
```

---

## 自定义 Specification 扩展

继承 `AbstractSpecification<T>` 可快速实现自定义查询条件，并使用其提供的 `resolvePath()` 方法解析嵌套路径：

```java
public class StartsWithSpecification<T> extends AbstractSpecification<T> {
    private final String field;
    private final String prefix;

    public StartsWithSpecification(String field, String prefix) {
        this.field = field;
        this.prefix = prefix;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.like(resolvePath(root, field), prefix + "%");
    }
}
```

自定义 Specification 可直接通过 `SpecificationBuilder.add()` 加入构建链：

```java
Specification<User> spec = SpecificationBuilder.<User>builder()
    .equal("status", "ACTIVE")
    .add(new StartsWithSpecification<>("name", "John"))
    .build();
```

---

## Repository 要求

使用 DSL 查询的 Repository 需同时继承 `JpaRepository` 和 `JpaSpecificationExecutor`：

```java
@Repository
public interface UserRepository
    extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}
```

---

## 运行测试

```bash
./mvnw test
```

测试覆盖：
- `SpecificationBuilderTest`：15 个测试，覆盖所有查询条件类型及分页排序
- `PageRequestBuilderTest`：4 个测试，覆盖分页和排序构建
- `SpecificationTest`：11 个测试，覆盖各 Specification 实现类
- `UserServiceTest`：9 个测试，覆盖 Service 层 DSL 使用场景