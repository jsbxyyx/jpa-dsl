# jdbc-dsl

Java 17+ 类型安全 JDBC 查询 DSL，基于 Spring JDBC，使用 Maven 多模块构建。

## 模块

| 模块 | 说明 |
|---|---|
| **jdbc-dsl** | 类型安全 SQL 构建与执行，支持 CRUD、JOIN、分页、排序、投影、聚合、子查询、CTE、窗口函数及数据库方言 |
| **jdbc-integration-tests** | 面向真实数据库的集成测试 |

## 环境要求

- Java 17+
- Maven（仓库提供 Maven Wrapper）
- Spring 相关依赖版本以根目录和模块的 pom.xml 为准

## 构建与测试

在仓库根目录执行：

~~~bash
# 编译和测试所有模块
./mvnw verify

# 运行 DSL 单元测试
./mvnw -pl jdbc-dsl test

# 运行 MySQL 集成测试（需按集成测试 README 配置数据库）
./mvnw -pl jdbc-integration-tests -am -Pmysql-it verify
~~~

Windows 可使用 mvnw.cmd 替代 ./mvnw。

## 仓库结构

~~~text
.
├── pom.xml
├── jdbc-dsl/
│   ├── pom.xml
│   └── README.md
└── jdbc-integration-tests/
    ├── pom.xml
    └── README.md
~~~

## Maven 依赖

~~~xml
<dependency>
    <groupId>io.github.jsbxyyx</groupId>
    <artifactId>jdbc-dsl</artifactId>
    <version>${version}</version>
</dependency>
~~~

运行时所需的 Spring 依赖请参照 `jdbc-dsl/pom.xml` 与模块 README。
