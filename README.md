# jpa-dsl · jdbc-dsl · jdbc-ast

Java 17+ 类型安全查询 DSL 工具集，使用 Maven 多模块构建。三个模块面向不同场景，可按需单独引入。

## 模块导航

| 模块 | 适用场景 | 文档 |
|---|---|---|
| **jpa-dsl** | 基于 Spring Data JPA Specification 构建条件，并提供批量更新、删除和 DTO 投影 | [模块 README](jpa-dsl/README.md) |
| **jdbc-dsl** | 基于 NamedParameterJdbcTemplate 构建类型安全 SQL，支持 CRUD、JOIN、分页、排序和投影 | [模块 README](jdbc-dsl/README.md) |
| **jdbc-ast** | 通过 SQL AST 表达式和语句树构建 SQL，适合显式控制 SQL 结构 | [模块 README](jdbc-ast/README.md) |

## 如何选择

- 已使用 Spring Data JPA，希望复用 Repository 和 JPA Metamodel：选择 **jpa-dsl**。
- 希望使用 JDBC 直接执行 SQL，并通过 Java 方法引用表达实体属性：选择 **jdbc-dsl**。
- 需要更底层的 SQL AST，显式控制表达式和语句结构：选择 **jdbc-ast**。

## 环境要求

- Java 17+
- Maven（仓库提供 Maven Wrapper）
- Spring 相关依赖版本以根目录和各模块的 pom.xml 为准

## 构建与测试

在仓库根目录执行：

~~~bash
# 编译所有模块
./mvnw -DskipTests package

# 运行所有模块测试
./mvnw test

# 单独测试一个模块
./mvnw -pl jdbc-dsl test
./mvnw -pl jpa-dsl test
./mvnw -pl jdbc-ast test
~~~

Windows 可使用 mvnw.cmd 替代 ./mvnw。

## 仓库结构

~~~text
.
├── pom.xml
├── jdbc-dsl/
│   ├── pom.xml
│   └── README.md
├── jpa-dsl/
│   ├── pom.xml
│   └── README.md
└── jdbc-ast/
    ├── pom.xml
    └── README.md
~~~

## Maven 依赖

三个模块分别发布，只需引入所需模块。版本号请使用项目发布版本或 Maven Central 上可用的版本。

~~~xml
<dependency>
    <groupId>io.github.jsbxyyx</groupId>
    <artifactId>jdbc-dsl</artifactId>
    <version>${version}</version>
</dependency>
~~~

将 artifactId 替换为 jpa-dsl 或 jdbc-ast 即可使用其他模块。运行时所需的 Spring/JPA 依赖请参照对应模块的 README 和 POM。

## 迁移提示

如果从早期同时包含 JPA DSL 与 JDBC DSL 的单一构件升级，请按实际使用的 API 添加对应模块依赖。主要包名分别为 io.github.jsbxyyx.jpadsl、io.github.jsbxyyx.jdbcdsl 和 io.github.jsbxyyx.jdbcast。
