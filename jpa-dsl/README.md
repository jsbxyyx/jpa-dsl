# jpa-dsl

基于 Spring Data JPA 的类型安全 DSL。使用 JPA Static Metamodel（例如 User_.status）构建 Specification，并提供 Repository 扩展以执行批量更新、删除和简单 DTO 投影查询。

## 能力概览

- 通过 SpecificationBuilder 构建查询条件
- 通过 SpecificationDsl 组合 and、or、not 等条件
- 基于 JPA Metamodel 属性进行类型安全 JOIN
- 使用 PageRequestBuilder 构建分页与排序请求
- Repository 扩展：批量 UPDATE、批量 DELETE 和 DTO 构造器投影

## 环境与依赖

- Java 17+
- Spring Data JPA、Jakarta Persistence
- Hibernate hibernate-jpamodelgen 注解处理器

Maven：

~~~xml
<dependency>
    <groupId>io.github.jsbxyyx</groupId>
    <artifactId>jpa-dsl</artifactId>
    <version>${version}</version>
</dependency>
~~~

Spring/JPA 运行时依赖主要声明为 provided；应用应配置自己的 Spring Data JPA、JPA Provider 和数据源。

## 生成 JPA Static Metamodel

DSL 使用 User_.status 一类的静态元模型属性。模块 POM 已配置 hibernate-jpamodelgen。应用项目如果需要引用自己实体生成的 *_ 类，也应配置相应注解处理器并在编译时生成元模型。

示例实体：

~~~java
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    private String name;
    private Integer age;
    private String status;
}
~~~

## 快速开始：构建 Specification

~~~java
import io.github.jsbxyyx.jpadsl.SpecificationBuilder;
import org.springframework.data.jpa.domain.Specification;

Specification<User> spec = SpecificationBuilder.<User>builder()
    .eq(User_.status, "ACTIVE")
    .gte(User_.age, 18)
    .like(User_.name, "Ada")
    .build();

List<User> users = userRepository.findAll(spec);
~~~

若要根据输入值是否为空决定是否加入条件，可使用带 boolean condition 的重载：

~~~java
String keyword = request.getKeyword();

Specification<User> spec = SpecificationBuilder.<User>builder()
    .like(User_.name, keyword, keyword != null)
    .eq(User_.status, "ACTIVE")
    .build();
~~~

条件方法默认会添加谓词，即使值为 null；条件重载只有在 condition 为 true 时才添加谓词。

## 分页与排序

~~~java
Pageable pageable = PageRequestBuilder.builder()
    .page(0)
    .size(20)
    .sortBy(User_.name, Sort.Direction.ASC)
    .build();

Page<User> page = userRepository.findAll(spec, pageable);
~~~

## Repository 扩展

按需将扩展接口添加到 Spring Data Repository：

~~~java
public interface UserRepository extends
        JpaRepository<User, Long>,
        JpaSpecificationExecutor<User>,
        JpaUpdateExecutor<User>,
        JpaDeleteExecutor<User>,
        JpaSelectExecutor<User> {
}
~~~

- JpaUpdateExecutor / UpdateBuilder：类型安全的批量 UPDATE。
- JpaDeleteExecutor / DeleteBuilder：类型安全的批量 DELETE；DELETE 需要 WHERE 条件，防止意外全表删除。
- JpaSelectExecutor / SelectBuilder：简单 DTO 构造器投影。当前投影主要面向 root 实体字段，不应假设支持任意 JOIN 投影。

## 测试

~~~bash
./mvnw -pl jpa-dsl test
~~~

## 相关源码入口

- SpecificationBuilder / SpecificationDsl：查询条件
- PageRequestBuilder：分页与排序
- UpdateBuilder / JpaUpdateExecutor：批量更新
- DeleteBuilder / JpaDeleteExecutor：批量删除
- SelectBuilder / JpaSelectExecutor：DTO 投影查询
