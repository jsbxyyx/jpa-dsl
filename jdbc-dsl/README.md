# jdbc-dsl

基于 Spring NamedParameterJdbcTemplate 的类型安全 JDBC DSL。通过实体属性方法引用（例如 User::getName）表达列引用，减少手写字段名和 SQL 拼接。

## 能力概览

- SELECT、条件查询、DTO/实体映射、排序与分页
- JOIN、分组聚合、HAVING、CTE 和 UNION 等查询能力
- INSERT、批量 INSERT、UPDATE、DELETE 和 Upsert 等写操作
- 使用命名参数绑定查询值
- 读取实体元数据，并支持命名策略与多种数据库方言
- Spring Boot 自动配置与 Repository 支持

具体能力以当前版本公开 API 为准。

## 环境与依赖

- Java 17+
- Spring JDBC（使用 NamedParameterJdbcTemplate）
- 实体元数据可读取 Jakarta Persistence 注解，如 @Entity、@Table、@Column 和 @Id

Maven：

~~~xml
<dependency>
    <groupId>io.github.jsbxyyx</groupId>
    <artifactId>jdbc-dsl</artifactId>
    <version>${version}</version>
</dependency>
~~~

模块中的 Spring 相关依赖主要声明为 provided。应用需要提供实际使用的运行时依赖、数据源和 JDBC 配置。

## 快速开始

假设 User 是数据库实体，UserDto 有与所选列对应的构造器：

~~~java
import io.github.jsbxyyx.jdbcdsl.JSort;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.SelectSpec;

SelectSpec<User, UserDto> spec = SelectBuilder
    .from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .orderBy(JSort.byAsc(User::getName))
    .mapTo(UserDto.class);
~~~

构建出的 SelectSpec 可交给模块提供的执行器或 Repository 执行。执行器需要使用应用配置的 NamedParameterJdbcTemplate。

## 常用查询示例

以下示例假设已经配置好 `JdbcDslExecutor executor`，并有映射实体 `User`。示例中的 DTO 需要提供无参构造器和与查询列对应的 setter；如果结果是单列标量，可以使用 `mapTo(Long.class)`、`mapTo(Integer.class)` 等类型。

### 1. 普通 SELECT：指定列、WHERE、ORDER BY

~~~java
SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
    .select(User::getId, User::getName, User::getStatus)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .orderBy(JSort.byAsc(User::getName))
    .mapTo(UserDto.class);

List<UserDto> users = executor.select(spec);
~~~

不指定 `select(...)` 时，可通过 `mapToEntity()` 将结果映射回实体：

~~~java
SelectSpec<User, User> spec = SelectBuilder.from(User.class)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapToEntity();

List<User> users = executor.select(spec);
~~~

### 2. 分页查询：返回 Spring Data `Page`

`selectPage` 会查询当前页数据并执行总数统计，返回内容、总记录数和总页数。页码从 `0` 开始。

~~~java
import org.springframework.data.domain.Page;

SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapTo(UserDto.class);

JPageable<User> pageable = JPageable.of(
    0,                              // 第 1 页（页码从 0 开始）
    20,                             // 每页 20 条
    JSort.byAsc(User::getName)      // 排序
);

Page<UserDto> page = executor.selectPage(spec, pageable);
List<UserDto> content = page.getContent();
long total = page.getTotalElements();
int totalPages = page.getTotalPages();
~~~

如果只想拿分页后的列表、不需要总数统计，使用 `executor.select(spec, pageable)`，返回 `List<R>`，不会额外执行 COUNT 查询。

~~~java
List<UserDto> content = executor.select(spec, pageable);
~~~

### 3. 查询最大值：`MAX`

聚合函数通过 `SqlFunctions` 提供。单列聚合结果可映射到对应的 Java 包装类型：

~~~java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.max;

SelectSpec<User, Integer> maxAgeSpec = SelectBuilder.from(User.class)
    .select(max(User::getAge))
    .mapTo(Integer.class);

List<Integer> values = executor.select(maxAgeSpec);
Integer maxAge = values.isEmpty() ? null : values.get(0);
~~~

如果还需要 WHERE 条件，可在 `mapTo(...)` 前追加：

~~~java
SelectSpec<User, Integer> maxAgeSpec = SelectBuilder.from(User.class)
    .select(max(User::getAge))
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapTo(Integer.class);

Integer maxActiveAge = executor.select(maxAgeSpec).stream()
    .findFirst()
    .orElse(null);
~~~

### 4. 查询数量：`COUNT`

**统计符合条件的记录数**，推荐使用执行器的 `count(spec)`，不需要自己构建 COUNT 表达式：

~~~java
SelectSpec<User, User> spec = SelectBuilder.from(User.class)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapToEntity();

long activeUserCount = executor.count(spec);
~~~

也可以将 `COUNT(*)` 作为 SELECT 聚合表达式，得到单列结果：

~~~java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar;

SelectSpec<User, Long> countSpec = SelectBuilder.from(User.class)
    .select(countStar())
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapTo(Long.class);

long activeUserCount = executor.select(countSpec).stream()
    .findFirst()
    .orElse(0L);
~~~

需要统计某一列非 NULL 的数量时，可使用 `count(User::getName)`；需要去重计数时，可使用 `countDistinct(User::getName)`。

### 5. 查询一条记录

`findOne` 最多返回一条记录；没有匹配项时返回 `null`。如果需要按条件取最新/最先的一条，配合 `orderBy` 使用：

~~~java
SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .orderBy(JSort.byDesc(User::getCreateTime))
    .mapTo(UserDto.class);

UserDto first = executor.findOne(spec);
~~~

### 6. JOIN 多表查询

假设 `User` 与 `Order` 通过 `User.id = Order.userId` 关联。JOIN 时需要为表设置别名，并在 `ON` 条件中明确指定左右两侧的别名。

#### INNER JOIN：查询用户及其订单字段

使用 `SqlFunctions.col(..., alias)` 投影不同表的字段；DTO 需要提供与查询结果列对应的 setter。

~~~java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;

SelectSpec<User, UserOrderDto> spec = SelectBuilder.from(User.class, "u")
    .select(
        col(User::getName, "u"),
        col(Order::getOrderNo, "o"),
        col(Order::getAmount, "o").as("orderAmount"))
    .join(Order.class, "o", JoinType.INNER,
        on -> on.eq(User::getId, "u", Order::getUserId, "o"))
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .where(Order.class, w -> w.eq(Order::getStatus, "PAID"))
    .mapTo(UserOrderDto.class);

List<UserOrderDto> rows = executor.select(spec);
~~~

这里的条件分别过滤用户表和订单表。也可以使用带别名的列引用直接过滤 JOIN 表字段：

~~~java
SelectSpec<User, UserOrderDto> spec = SelectBuilder.from(User.class, "u")
    .select(col(User::getName, "u"), col(Order::getOrderNo, "o"))
    .join(Order.class, "o", JoinType.INNER,
        on -> on.eq(User::getId, "u", Order::getUserId, "o"))
    .where(w -> w.eq(Order::getStatus, "o", "PAID"))
    .mapTo(UserOrderDto.class);
~~~

#### LEFT JOIN：查询所有用户及匹配的订单

LEFT JOIN 会保留左表用户，即使没有匹配订单；此时订单字段可能为 `null`。

~~~java
SelectSpec<User, UserOrderDto> spec = SelectBuilder.from(User.class, "u")
    .select(
        col(User::getName, "u"),
        col(Order::getOrderNo, "o").as("orderNo"))
    .join(Order.class, "o", JoinType.LEFT,
        on -> on.eq(User::getId, "u", Order::getUserId, "o"))
    .mapTo(UserOrderDto.class);

List<UserOrderDto> rows = executor.select(spec);
~~~

注意：如果在 LEFT JOIN 后的 `WHERE` 中限制右表字段（例如 `Order.status = 'PAID'`），没有匹配订单的用户通常会被过滤掉，效果可能接近 INNER JOIN。若想保留所有用户并只关联符合条件的订单，可考虑把限制条件放入 `ON` 条件中。

#### 直接查询 JOIN 表对应的实体

如果目标是返回关联表的完整实体，可以使用 `select(Order.class)`，并通过 `where(Order.class, ...)` 添加该实体的过滤条件：

~~~java
SelectSpec<User, Order> spec = SelectBuilder.from(User.class, "u")
    .select(Order.class)
    .join(Order.class, "o", JoinType.LEFT,
        on -> on.eq(User::getId, "u", Order::getUserId, "o"))
    .where(Order.class, w -> w.eq(Order::getStatus, "PAID"))
    .mapTo(Order.class);

List<Order> orders = executor.select(spec);
~~~

若同一实体被 JOIN 多次（自连接），请给每个关联设置不同别名，并在列引用或条件中显式指定别名，避免无法判断目标表。

### 分页与不分页查询

~~~java
// 第 1 页，每页 20 条
JPageable pageable = JPageable.of(0, 20);

// 不分页，按名称升序
JPageable allSorted = JPageable.unpaged(
    JSort.byAsc(User::getName)
);
~~~

分页查询 API（例如 selectPage）需要 paged 的 JPageable；普通列表查询可以使用 JPageable.unpaged()。请以当前版本的 JPageable、JdbcDslExecutor 和 Repository API 为准。

## INSERT、UPDATE 与 DELETE

### 1. INSERT：插入指定列

通过 `InsertBuilder.into(...).columns(...)` 指定要写入的列，再使用 `executor.save(spec, entity)` 执行。推荐使用实体 getter 方法引用，避免手写属性名：

~~~java
InsertSpec<User> insertSpec = InsertBuilder.into(User.class)
    .columns(User::getName, User::getStatus)
    .build();

User user = new User();
user.setName("Alice");
user.setStatus("ACTIVE");

executor.save(insertSpec, user);
~~~

未列入 `columns(...)` 的字段不会作为本次 INSERT 的指定列写入。也可以传入数据库列名字符串，例如 `.columns("user_name", "status")`；使用字符串时需填写实际数据库列名。

### 2. UPDATE：按条件更新

~~~java
UpdateSpec<User> updateSpec = UpdateBuilder.from(User.class)
    .set(User::getStatus, "DISABLED")
    .where(w -> w.eq(User::getName, "Alice"))
    .build();

int affectedRows = executor.executeUpdate(updateSpec);
~~~

可以连续调用多个 `set(...)` 更新多列。为避免意外全表更新，默认要求 UPDATE 提供 WHERE 条件；请在执行前确认更新条件符合预期。

### 3. DELETE：按条件删除

~~~java
DeleteSpec<User> deleteSpec = DeleteBuilder.from(User.class)
    .where(w -> w.eq(User::getName, "Alice"))
    .build();

int affectedRows = executor.executeDelete(deleteSpec);
~~~

`executeUpdate` 和 `executeDelete` 返回受影响的行数。DELETE 同样默认要求提供 WHERE 条件；不要在未确认影响范围时使用允许无条件删除的配置或不安全构建方式。

## SQL 函数

函数表达式通过 `SqlFunctions` 提供，可用于 SELECT 投影、WHERE 条件和聚合查询。以下示例使用静态导入：

~~~java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countDistinct;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.upper;
~~~

### 1. 字符串函数：UPPER

例如，查询邮箱不区分大小写匹配的用户：

~~~java
SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(upper(User::getEmail), "ALICE@EXAMPLE.COM"))
    .mapTo(UserDto.class);

List<UserDto> users = executor.select(spec);
~~~

函数也可以用于 SELECT 投影，例如 `.select(upper(User::getName).as("nameUpper"))`。具体可用函数请参考 `SqlFunctions`。

### 2. COUNT(DISTINCT)：统计不重复值

当 JOIN 结果中同一个用户可能出现多次，但业务需要统计不同用户数时，使用 `countDistinct(...)`：

~~~java
SelectSpec<User, Long> spec = SelectBuilder.from(User.class)
    .select(countDistinct(User::getId))
    .join(Order.class, "o", JoinType.INNER,
        on -> on.eq(User::getId, "t", Order::getUserId, "o"))
    .mapTo(Long.class);

Long distinctUserCount = executor.select(spec).stream()
    .findFirst()
    .orElse(0L);
~~~

`COUNT(DISTINCT u.id)` 统计不重复的用户 ID；与执行器的 `executor.count(spec)` 不同，它是 SELECT 中显式指定的聚合表达式。普通 JOIN 计数不会自动对根实体去重。

## 代码生成：从数据库表生成 Entity 和 Repository

`JdbcEntityGenerator` 可以读取已配置数据源的数据库元数据，生成带有 Jakarta Persistence 映射注解的实体类；配置 `repositoryPackage(...)` 后，还可以生成继承 `JdbcDslRepository` 的 Spring `@Repository` 类。

下面以 Spring 的 `DriverManagerDataSource` 为例。请根据实际数据库替换驱动类、JDBC URL、用户名和密码，并确保应用已添加对应的 JDBC 驱动依赖。

~~~java
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import io.github.jsbxyyx.jdbcdsl.codegen.JdbcEntityGenerator;

DataSource dataSource = new DriverManagerDataSource(
    "jdbc:postgresql://localhost:5432/app",
    "app_user",
    "your_password"
);

JdbcEntityGenerator.builder()
    .dataSource(dataSource)
    .entityPackage("com.example.entity")
    .repositoryPackage("com.example.repository")
    .outputDir("src/main/java")
    .useLombok(true)
    .trimPrefix("t_", "tb_")
    .generate("t_user", "t_order");
~~~

参数说明：

- `dataSource(...)`：连接目标数据库的数据源。
- `entityPackage(...)`：生成实体类的 Java 包名。
- `repositoryPackage(...)`：生成 Repository 的 Java 包名；不配置时跳过 Repository 生成。
- `outputDir(...)`：生成源码的根目录，默认是 `src/main/java`。
- `useLombok(true)`：生成 Lombok 注解（默认开启）；设为 `false` 则生成普通 getter 和链式 setter。
- `trimPrefix("t_", "tb_")`：生成类名时移除匹配的表名前缀。
- `generate("t_user", "t_order")`：只生成指定表；调用无参数的 `generate()` 会尝试处理数据库中的所有表。

例如，`t_user` 可以生成到 `com.example.entity` 下，Repository 则生成到 `com.example.repository` 下。生成的 Repository 绑定实体类型和主键类型，常用 CRUD 与 DSL builder 操作继承自 `JdbcDslRepository`。生成前请确认目标包和输出目录正确，并检查生成结果后再纳入版本控制。

## 属性与表列映射

通常使用 Jakarta Persistence 注解描述实体映射：

~~~java
@Entity
@Table(name = "app_user")
public class User {
    @Id
    private Long id;

    @Column(name = "user_name")
    private String name;

    private String status;

    // getters/setters
}
~~~

列名解析还会受到命名策略和数据库方言的影响。不要假设 Java 属性名必然与数据库列名相同。

## 测试

在仓库根目录执行：

~~~bash
./mvnw -pl jdbc-dsl test
~~~

只运行指定测试类：

~~~bash
./mvnw -pl jdbc-dsl -Dtest=JoinRendererTest test
~~~

## 相关源码入口

- SelectBuilder / SelectSpec：查询构建
- WhereBuilder：条件构建
- InsertBuilder、UpdateBuilder、DeleteBuilder：写操作
- JdbcDslExecutor：执行入口
- JPageable、JSort：分页与排序
- SqlRenderer、dialect/：SQL 渲染与数据库方言
