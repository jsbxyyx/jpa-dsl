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

假设 `User` 是数据库实体，`UserDto` 是可映射的 JavaBean：提供无参构造器，并为查询结果列提供可写属性（setter 或可访问字段）。

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

以下示例假设已经配置好 `JdbcDslExecutor executor`，并有映射实体 `User`。`User`、`Order`、`UserDto`、`StatusCountDto` 以及 `user1` 等均为示例占位类型或变量，需替换为应用中的实体、DTO 和数据。代码片段按主题展示，未重复列出所有公共 import；复制到独立类中时，请补齐对应包的 import。示例中的 JavaBean DTO 需要提供无参构造器和可写属性，也可使用匹配全部投影列的 `record`；如果结果是单列标量，可以使用 `mapTo(Long.class)`、`mapTo(Integer.class)` 等类型。

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

注意：如果在 LEFT JOIN 后的 `WHERE` 中限制右表字段（例如 `Order.status = 'PAID'`），没有匹配订单的用户通常会被过滤掉，效果可能接近 INNER JOIN。若想保留所有用户并只关联符合条件的订单，可将限制条件放入 `ON`。`OnBuilder` 的 `eq(...)` 用于两侧列的等值关联；其他条件可使用 `on.raw(sql, params)`，并通过参数绑定值：

~~~java
.join(Order.class, "o", JoinType.LEFT,
    on -> on.eq(User::getId, "u", Order::getUserId, "o")
            .raw("o.status = :status", Map.of("status", "PAID")))
~~~

Raw ON 片段必须由开发者控制，不能拼接用户输入。

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

**单条 INSERT 的行为边界：**

- `executor.save(entity)` 插入实体映射的全部列；`IDENTITY` 主键列会被排除，执行成功后 JDBC 生成的主键会回填到实体。
- `executor.save(spec, entity)` 在未指定列时行为相同；指定了 `.columns(...)` 后只插入指定列，不会自动从显式列集合中移除 IDENTITY 主键。执行时仍会尝试读取 JDBC 生成键并回填实体；若显式列集合包含主键，需自行确认其与数据库生成策略是否冲突。
- `executor.saveNonNull(entity)` 先自动填充审计时间，再过滤值为 `null` 的列；它始终排除 IDENTITY 主键列，成功插入后仍会回填生成主键。注意，自动填充的审计字段不再是 `null`，因此会参与 INSERT。
- 上述单条插入方法均返回 `void`，不是受影响行数。若需要 INSERT 后返回数据库列值，可评估 `saveReturning(...)`；该 API 使用数据库 `RETURNING` 语法，需确认方言支持。

### 自动填充审计时间

实体字段使用 Spring Data 的 `@CreatedDate` 和 `@LastModifiedDate` 注解后，单条 INSERT（包括 `saveNonNull`）、批量 INSERT 和 Upsert 会在执行前将其设置为当前时间；UPDATE 则会自动补充尚未显式赋值的 `@LastModifiedDate` 字段。若 UPDATE 已显式设置该字段，则保留调用方提供的值。自动填充值类型为 `LocalDateTime`。

默认时间源为 `LocalDateTime::now`。测试或需要统一时间源时，可通过 `JdbcDslExecutor#setTimeProvider(TimeProvider)` 注入自定义时间提供者，例如：

~~~java
executor.setTimeProvider(() -> LocalDateTime.of(2024, 1, 1, 0, 0, 0));
~~~

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
- InsertBuilder、BatchInsertBuilder、UpdateBuilder、DeleteBuilder、UpsertBuilder：写操作
- JdbcDslExecutor：执行入口
- JdbcDslRepository：通用 Repository
- JPageable、JSort：分页与排序
- SqlFunctions：SQL 函数和表达式
- JdbcEntityGenerator：从数据库元数据生成实体和 Repository
- SqlRenderer、dialect/：SQL 渲染与数据库方言

## Spring 配置

如果使用项目提供的 Spring Boot 自动配置，可在 `application.properties` 中配置：

```properties
# 缺省为 false：阻止没有 WHERE 条件的 UPDATE/DELETE
jdbcdsl.allow-empty-where=false

# 缺省为 true：DSL SELECT 自动过滤带有 @LogicalDelete 的根实体
# 不自动过滤 JOIN 关联实体；Raw SQL query/queryOne 也不应用此过滤
jdbcdsl.logical-delete-auto-filter=true

# default 保留 Java 属性名；snake_case 将 camelCase 转为 snake_case
jdbcdsl.naming-strategy=default
```

对应的 YAML 写法：

```yaml
jdbcdsl:
  allow-empty-where: false
  logical-delete-auto-filter: true
  naming-strategy: snake_case
```

显式声明的 `@Table` / `@Column` 映射优先于命名策略。`logical-delete-auto-filter` 只为 DSL SELECT 的根实体追加正常态条件，不会自动过滤 JOIN 关联实体；`query` / `queryOne` 执行的 Raw SQL 也不会自动追加逻辑删除条件。自定义 `NamingStrategy` Spring Bean 优先于 `jdbcdsl.naming-strategy` 属性。若不使用 Spring Boot 自动配置，请自行创建 `NamedParameterJdbcTemplate` 和 `JdbcDslExecutor`；执行器可自动检测常见数据库方言，也可通过构造器显式传入 `Dialect`。

## 条件查询与参数绑定

条件值应通过 DSL 条件方法传入，不要将用户输入拼接进 SQL 字符串。`WhereBuilder` 支持组合条件；需要分组表达式时，可使用 lambda 组合 AND/OR 条件。对于列表过滤，应优先使用库提供的集合条件 API，并明确空集合在业务上的含义。

如果 DSL 无法表达某个数据库特有查询，可以使用 `JdbcDslExecutor.query(...)` / `queryOne(...)` 执行参数化 SQL，或者使用 `update(...)` 执行 DML：

```java
Map<String, Object> params = Map.of("status", "ACTIVE");
List<UserDto> rows = executor.query(
    "select id, name from app_user where status = :status",
    params,
    UserDto.class
);

UserDto first = executor.queryOne(
    "select id, name from app_user where id = :id",
    Map.of("id", 42L),
    UserDto.class
);

int changed = executor.update(
    "update app_user set status = :status where id = :id",
    Map.of("status", "DISABLED", "id", 42L)
);
```

`queryOne` 在没有结果时返回 `null`；`update` 返回受影响行数。所有动态数据都应放入参数 Map，通过 `:name` 占位符绑定。表名、列名、排序方向等 SQL 结构不能通过 JDBC 参数占位符绑定；若这些结构必须动态选择，应使用白名单映射。不要把不可信输入拼进 SQL 文本。

## 原生 SQL 与 Raw SQL 表达式

执行器的 `query` / `queryOne` 是完整 SQL 的入口。构建 DSL 条件时，也可以在确有必要时使用 Raw SQL 表达式来表达 DSL 暂不支持的片段。Raw SQL 是显式的安全边界：SQL 片段本身应由开发者控制，值应通过参数绑定传入，不能把用户输入直接拼接到片段中。

不同 Raw SQL 片段中的命名参数会被隔离重命名，以减少片段间同名参数冲突；这不意味着可以省略参数管理。请确保占位符和传入参数匹配，并为参数使用清晰、稳定的名称。涉及数据库专属语法时，应在目标数据库上执行集成测试。

## 分页与 COUNT 的语义

- `JPageable.of(page, size)` 的页码从 `0` 开始；`size` 必须为正数。
- `executor.select(spec, pageable)` 只返回分页列表，不额外执行 COUNT。
- `executor.selectPage(spec, pageable)` 返回 Spring Data `Page`，需要额外统计总数。
- 分页结果应使用确定性的排序；仅依赖数据库默认返回顺序，跨页时可能出现重复或遗漏。
- `executor.count(spec)` 遵循查询结果的 SQL 语义：普通 JOIN（未启用 `distinct()`）产生的多行会分别计数；带 `GROUP BY` 时统计应用 `HAVING` 后的分组数；启用 `distinct()` 时会包裹去重后的完整 SELECT 投影再计数。因此，`distinct()` 的去重范围取决于 SELECT 投影列，而不是自动按根实体主键去重。若只需统计某个键的去重数量，可显式使用 `COUNT(DISTINCT ...)`。
- 数据库方言会生成不同分页 SQL。SQL Server 在未指定排序时可能需要补充语法所需的排序表达式，但这不等同于稳定的业务排序。

## 结果映射约定

执行器根据结果集列标签（column label）匹配目标属性或 record 组件，匹配时不区分大小写。普通 JavaBean DTO/实体应提供可访问的无参构造器；属性优先通过 setter 写入，没有 setter 时回退到可访问字段。`record` 使用规范构造器创建，支持 DSL 和原生 SQL；普通类的任意参数构造器不支持。列标签应与目标属性对应。投影列建议显式使用 `.as("propertyName")`，特别是聚合表达式、同名列或 JOIN 多表投影，避免列名歧义。

没有对应属性的结果列会被忽略；匹配到属性的列若转换或赋值失败，将抛出 `IllegalStateException` 并终止查询，而不会返回不完整的对象。顶层异常信息包含列位置、属性名、目标类型和值的类型；原始异常可能包含实际数据。部分基本数值类型属性会将 SQL NULL 转换为零；需要区分 NULL 时请使用包装类型。

映射到 `record` 时，每个组件必须恰好匹配一个结果列，列顺序可以不同；缺少组件列或多个列匹配同一组件都会报错。基本类型组件不接受 SQL NULL，请使用包装类型表示可空值。规范构造器抛出的异常会终止查询。

```java
record UserView(Long id, String username) {}

List<UserView> users = executor.query(
        "SELECT username, id FROM t_user", Map.of(), UserView.class);
```

例如：

```java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.upper;

SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
    .select(
        User::getId,
        User::getName,
        upper(User::getStatus).as("statusUpper"))
    .mapTo(UserDto.class);
```

JavaBean DTO 中需要存在与 `id`、`name`、`statusUpper` 对应的可写属性，或使用包含这些组件的 `record`。普通 JavaBean 需要可访问的无参构造器；属性优先通过 setter 写入，没有 setter 时会回退到可访问字段。普通类不会自动匹配任意参数构造器。

## 条件构建进阶

### AND / OR 分组

多个连续的条件默认按 AND 组合。需要改变优先级时，使用 `and(...)` 和 `or(...)` 显式嵌套分组：

```java
SelectSpec<User, User> spec = SelectBuilder.from(User.class)
    .where(w -> w.eq(User::getStatus, "ACTIVE")
        .and(group -> group.or(names -> names.eq(User::getName, "Alice")
            .eq(User::getName, "Bob"))))
    .mapToEntity();
```

> 注意：请按项目当前版本 `WhereBuilder` 实际提供的分组 API 编写条件；若只需要简单条件，优先使用多个 `eq` / `gt` 等方法，不要把整段 SQL 手动拼接到 `raw(...)`。

### IN、NULL 与范围条件

```java
List<Long> ids = List.of(10L, 20L, 30L);

SelectSpec<User, User> spec = SelectBuilder.from(User.class)
    .where(w -> w.in(User::getId, ids)
        .isNotNull(User::getEmail)
        .between(User::getAge, 18, 65))
    .mapToEntity();

List<User> users = executor.select(spec);
```

`in(...)` / `notIn(...)` 接受集合。构建动态查询时，可以使用带 `boolean condition` 的重载跳过不适用的条件；不要让空集合的含义由偶然的业务路径决定，应在调用前明确“不过滤”还是“返回空结果”的业务语义。

### EXISTS 与子查询

子查询可以作为 `IN`、`EXISTS` 条件使用。内层查询通过 `mapTo(...)` 或 `mapToEntity()` 形成 `SelectSpec`：

```java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;

SelectSpec<Order, Long> paidCustomerIds = SelectBuilder.from(Order.class)
    .select(Order::getUserId)
    .where(w -> w.eq(Order::getStatus, "PAID"))
    .mapTo(Long.class);

SelectSpec<User, User> spec = SelectBuilder.from(User.class)
    .where(w -> w.inSubquery(col(User::getId), paidCustomerIds))
    .mapToEntity();
```

上例中的 `col(...)` 来自 `SqlFunctions`。外层列与子查询投影应保持类型一致。`notInSubquery(...)` 遵循 SQL 的 `NOT IN` NULL 语义：子查询结果包含 NULL 时，比较结果可能变为 UNKNOWN，业务上需要排除 NULL 时应在子查询中显式过滤。若使用 `EXISTS`，请确保子查询条件确实关联到外层记录；非相关子查询只判断整个子查询是否至少返回一行。子查询渲染会与外层共享参数上下文，自动分配不冲突的参数名；内层 `ORDER BY` 和锁定子句不会带入内联子查询。

标量子查询可通过 `Scalar.scalar(...)` 包装，再用于 `eqScalar`、`gtScalar` 等比较方法：

```java
import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.max;

SelectSpec<User, Integer> maxAge = SelectBuilder.from(User.class)
    .select(max(User::getAge))
    .mapTo(Integer.class);

SelectSpec<User, User> olderUsers = SelectBuilder.from(User.class)
    .where(w -> w.gtScalar(col(User::getAge), scalar(maxAge)))
    .mapToEntity();
```

标量子查询必须由 SQL 保证最多返回一行；返回多行时由数据库报错，返回零行时其值为 NULL。若要把 SELECT 当作派生表放入 `FROM`，可使用 `SelectBuilder.fromSubquery(innerSpec, "sub", Entity.class)`；传入的实体类负责提供派生表列映射，必须与内层 SELECT 投影列的结构相符。

## GROUP BY、HAVING 与 CTE

### 分组统计

```java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar;

SelectSpec<User, StatusCountDto> spec = SelectBuilder.from(User.class)
    .select(col(User::getStatus).as("status"), countStar().as("userCount"))
    .groupBy(User::getStatus)
    .having(h -> h.gt(countStar(), 5))
    .mapTo(StatusCountDto.class);

List<StatusCountDto> rows = executor.select(spec);
```

`StatusCountDto` 应提供可访问的无参构造器，以及与投影列标签对应的可写属性。聚合结果列如需映射到特定属性，建议为表达式设置明确别名，并确保 DTO 属性名一致。

### CTE（WITH）

```java
SelectSpec<User, User> activeUsers = SelectBuilder.from(User.class)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapToEntity();

SelectSpec<User, UserDto> spec = SelectBuilder.fromCte("active_users", User.class)
    .withCte("active_users", activeUsers)
    .select(User::getId, User::getName)
    .mapTo(UserDto.class);

List<UserDto> users = executor.select(spec);
```

CTE 名称应由应用代码固定提供，不要使用未经校验的外部输入拼接标识符。可以多次调用 `.withCte(...)` 声明多个 CTE，按声明顺序渲染；CTE 主体中的 `ORDER BY` 不会保留。当前 API 提供普通 `WITH`，没有递归 CTE 专用构建能力。若要在外层查询的 FROM 中读取 CTE，应使用 `fromCte(name, Entity.class)`；CTE 在 WHERE / IN / EXISTS 中的引用需要使用受控的原生 SQL 片段。CTE 的可用语法和优化行为取决于目标数据库；在生产数据库上验证具体 SQL。

### 窗口函数（OVER）

窗口函数在保留明细行的同时，计算当前行所在窗口内的排名或聚合值：

```java
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;

SelectSpec<User, UserRnDto> spec = SelectBuilder.from(User.class)
    .select(
        col(User::getName),
        rowNumber()
            .over(w -> w.partitionBy(User::getStatus)
                .orderBy(JOrder.asc(User::getAge)))
            .as("rn"),
        sum(User::getAge)
            .over(w -> w.partitionBy(User::getStatus)
                .orderBy(JOrder.asc(User::getAge))
                .rowsBetween(
                    WindowExpression.FrameBound.unboundedPreceding(),
                    WindowExpression.FrameBound.currentRow()))
            .as("runningTotal"))
    .mapTo(UserRnDto.class);
```

`over(...)` 支持 `partitionBy(...)`、`orderBy(...)`，以及 `rowsBetween`、`rangeBetween`、`groupsBetween` 窗口帧。帧边界可用 `FrameBound.unboundedPreceding()`、`preceding(n)`、`currentRow()`、`following(n)` 和 `unboundedFollowing()` 表示。窗口帧的合法组合及 `GROUPS` 等语法支持依赖数据库版本；例如源码文档注明 GROUPS 需要 PostgreSQL 11+、H2 2.x 或 SQLite 3.28+。窗口函数的 SQL 渲染测试不等于所有方言均可执行，部署前应在目标数据库验证。投影别名应与 DTO 可写属性匹配。

### 集合查询（UNION / INTERSECT / EXCEPT）

各分支先构造为相同结果类型的 `SelectSpec`，再组合：

```java
SelectSpec<User, UserDto> active = SelectBuilder.from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(User::getStatus, "ACTIVE"))
    .mapTo(UserDto.class);

SelectSpec<User, UserDto> inactive = SelectBuilder.from(User.class)
    .select(User::getId, User::getName)
    .where(w -> w.eq(User::getStatus, "INACTIVE"))
    .mapTo(UserDto.class);

UnionSpec<UserDto> union = UnionSpec.of(active)
    .unionAll(inactive)
    .orderBy(JSort.byAsc(User::getName))
    .build();

List<UserDto> rows = executor.union(union);
```

`union(...)` 会去重，`unionAll(...)` 保留重复行；构建器还提供 `intersect`、`intersectAll`、`except` 和 `exceptAll`。所有分支应投影相同数量、顺序兼容且类型可由数据库合并的列，并映射到同一 DTO 类型；Java 泛型一致并不能替代数据库对列数和列类型的检查。组合结果的 `orderBy(...)` 位于所有分支之后，排序列按输出列名/别名解析，不应使用分支表别名前缀。各集合操作及其 ALL 变体的支持存在数据库差异，尤其 INTERSECT/EXCEPT ALL；请在目标方言验证。分页重载 `executor.union(spec, offset, size)` 可用于限制组合结果集。

## 批量 INSERT 与 Upsert

### 批量 INSERT

```java
List<User> batch = List.of(user1, user2, user3);

BatchInsertSpec<User> spec = BatchInsertBuilder.of(User.class, batch)
    .columns(User::getName, User::getStatus)
    .build();

int[] affected = executor.executeBatchInsert(spec);
```

`.columns(...)` 指定本次插入的属性；未指定时会插入实体映射中的全部列，但排除使用 `IDENTITY` 策略生成的主键。批量插入会在执行前为每行自动填充 `@CreatedDate` 和 `@LastModifiedDate` 字段。空集合会直接返回空的 `int[]`，不会执行 SQL。批量中的每个实体应使用相同的列集合；显式指定列时，确保每行都提供这些列所需的值。还应根据数据库和驱动限制控制批次大小。`affected` 是 JDBC 返回的每条批处理结果，不应假设所有驱动都返回完全相同的行数标记。

### Upsert（冲突时更新）

```java
UpsertSpec<User> spec = UpsertBuilder.into(User.class)
    .onConflict(User::getEmail)
    .doUpdate(User::getName, User::getStatus)
    .build();

executor.upsert(spec, user);
```

`onConflict(...)` 应对应数据库中实际存在的唯一键或唯一索引。`doUpdate(...)` 指定冲突时更新的列；不指定更新列时，默认更新所有非冲突列。`doNothing()` 可用于冲突时跳过更新。执行 Upsert 时，`@CreatedDate` 和 `@LastModifiedDate` 字段会在渲染前自动填充；默认更新列策略会排除 `@CreatedDate`，避免冲突更新时覆盖创建时间。各数据库的 Upsert 语法和唯一键推断方式不同：MySQL 根据表上的唯一索引判断冲突，PostgreSQL/H2 等需要明确冲突目标；当前默认 `Sql2008Dialect` 不支持 Upsert。请确认配置的方言支持该操作，并在目标数据库验证行为。

## 事务边界

`JdbcDslExecutor` 使用应用提供的 Spring JDBC `NamedParameterJdbcTemplate` 执行 SQL。需要将多次写操作作为一个原子业务步骤时，应在 Spring 管理的 Service 层使用 `@Transactional`，并确保 `DataSource`、事务管理器和 JDBC 模板使用同一数据源：

```java
import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;
import io.github.jsbxyyx.jdbcdsl.UpdateSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserService {
    private final JdbcDslExecutor executor;

    public UserService(JdbcDslExecutor executor) {
        this.executor = executor;
    }

    @Transactional
    public void updateUserAndAudit(UpdateSpec<User> update, String auditSql,
                                   Map<String, Object> auditParams) {
        executor.executeUpdate(update);
        executor.update(auditSql, auditParams);
    }
}
```

以上仅展示事务边界的组织方式；`UpdateSpec` 的构建、审计 SQL 和参数应替换为应用自己的业务逻辑。不要捕获异常后静默吞掉，否则事务可能无法按预期回滚。

## 真实数据库集成测试

普通单元测试和 SQL 渲染测试不要求连接外部数据库：

```bash
./mvnw -pl jdbc-dsl test
```

MySQL 真实数据库集成测试单独通过 `mysql-it` profile 启动，连接参数和测试表范围见 [`jdbc-integration-tests/README.md`](../jdbc-integration-tests/README.md)：

```bash
./mvnw -pl jdbc-integration-tests -am -Pmysql-it verify
```

集成测试不会自动启动数据库服务。请使用专用测试库，并确认连接用户只拥有测试所需权限。渲染测试通过不代表所有数据库版本都已完成真实运行验证；对 Oracle、SQL Server、PostgreSQL、达梦或 Kingbase 等目标环境发布前，应在对应数据库版本上验证查询、分页、COUNT 和写操作。

## 文档与版本

本 README 以仓库当前源码为准。发布版本的依赖版本请使用 Maven Central 中实际发布的版本号；`-SNAPSHOT` 或 `-rc` 版本是否可用取决于对应仓库是否已发布该构件。
