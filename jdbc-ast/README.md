# jdbc-ast

通过 Java 表达式树（SQL AST）构建 SQL 的工具模块。它比高层实体 DSL 更直接地暴露表引用、列表达式、条件、聚合表达式和语句结构，适合需要显式控制 SQL 形态的场景。

## 能力概览

- SELECT、INSERT、UPDATE、DELETE 语句构建
- 类型化表引用与列方法引用
- JOIN、WHERE、GROUP BY、HAVING、ORDER BY 和分页
- 聚合函数、窗口函数、CASE、CAST、子查询和 CTE 等表达式
- SQL 渲染与命名参数
- 可选的 Spring JDBC 执行器 JdbcAstExecutor

## 环境与依赖

- Java 17+
- 核心 AST 和渲染 API 不要求 Spring Data JPA
- jakarta.persistence-api 是可选依赖，用于读取 @Table / @Column 等注解
- spring-jdbc 是可选依赖，仅在使用 JdbcAstExecutor 时需要

Maven：

~~~xml
<dependency>
    <groupId>io.github.jsbxyyx</groupId>
    <artifactId>jdbc-ast</artifactId>
    <version>${version}</version>
</dependency>
~~~

## 快速开始：构建 SELECT

假设 TUser 与 TOrder 是映射实体：

~~~java
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

import static io.github.jsbxyyx.jdbcast.builder.SQL.*;

TableRef<TUser> u = TableRef.of(TUser.class, "u");
TableRef<TOrder> o = TableRef.of(TOrder.class, "o");

SelectStatement statement = from(u)
    .join(o).on(u.col(TUser::getId).eq(o.col(TOrder::getUserId)))
    .select(
        u.col(TUser::getUsername),
        sum(o.col(TOrder::getAmount)).as("total")
    )
    .where(u.col(TUser::getStatus).eq("ACTIVE"))
    .groupBy(u.col(TUser::getId), u.col(TUser::getUsername))
    .having(sum(o.col(TOrder::getAmount)).gt(100))
    .orderBy(u.col(TUser::getUsername).asc())
    .build();
~~~

上例把列引用、JOIN、聚合、分组和筛选组合成 SelectStatement。实际渲染与执行方式取决于使用的 SqlRenderer、元数据解析器和数据库方言。

## 其他语句入口

~~~java
insertInto(User.class); // INSERT builder
update(User.class);     // UPDATE builder
deleteFrom(User.class); // DELETE builder
~~~

以上入口分别返回对应的语句构建器；请根据实际字段调用构建器 API 并完成构建。

## 元数据与 SQL 渲染

- TableRef：绑定实体类型与 SQL 表别名
- SFunction / PropertyRefResolver：解析 Java 属性方法引用
- SqlRenderer / AnsiSqlRenderer：将 AST 渲染为 SQL 和参数
- PaginationDialect：为不同数据库提供分页语法
- JdbcAstExecutor：可选的 Spring JDBC 执行入口

请优先使用表达式和条件 API 绑定值。只有在明确需要且已审查输入时才使用 raw SQL 片段；不要将外部输入直接拼接进 raw SQL。

## 测试

~~~bash
./mvnw -pl jdbc-ast test
~~~
