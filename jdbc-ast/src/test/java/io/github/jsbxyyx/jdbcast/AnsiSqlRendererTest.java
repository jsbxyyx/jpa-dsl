package io.github.jsbxyyx.jdbcast;

import io.github.jsbxyyx.jdbcast.builder.SQL;
import io.github.jsbxyyx.jdbcast.clause.CteDef;
import io.github.jsbxyyx.jdbcast.clause.FrameBound;
import io.github.jsbxyyx.jdbcast.clause.TableRef;
import io.github.jsbxyyx.jdbcast.condition.ExistsCondition;
import io.github.jsbxyyx.jdbcast.meta.JpaMetaResolver;
import io.github.jsbxyyx.jdbcast.renderer.AnsiSqlRenderer;
import io.github.jsbxyyx.jdbcast.renderer.RenderedSql;
import io.github.jsbxyyx.jdbcast.renderer.dialect.LimitOffsetDialect;
import io.github.jsbxyyx.jdbcast.renderer.dialect.OffsetFetchDialect;
import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jsbxyyx.jdbcast.builder.SQL.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnsiSqlRenderer} — pure renderer tests, no database.
 */
class AnsiSqlRendererTest {

    // ------------------------------------------------------------------ //
    //  Stub entities
    // ------------------------------------------------------------------ //

    @Table(name = "t_user")
    static class User {
        @Id @Column(name = "id")       private Long    id;
        @Column(name = "username")     private String  username;
        @Column(name = "status")       private String  status;
        @Column(name = "age")          private Integer age;
        @Column(name = "email")        private String  email;

        public Long    getId()       { return id; }
        public String  getUsername() { return username; }
        public String  getStatus()   { return status; }
        public Integer getAge()      { return age; }
        public String  getEmail()    { return email; }
    }

    @Table(name = "t_order")
    static class Order {
        @Id @Column(name = "id")       private Long   id;
        @Column(name = "user_id")      private Long   userId;
        @Column(name = "amount")       private Double amount;
        @Column(name = "order_no")     private String orderNo;

        public Long   getId()      { return id; }
        public Long   getUserId()  { return userId; }
        public Double getAmount()  { return amount; }
        public String getOrderNo() { return orderNo; }
    }

    // ------------------------------------------------------------------ //
    //  Setup
    // ------------------------------------------------------------------ //

    private AnsiSqlRenderer renderer;
    private TableRef<User>  u;
    private TableRef<Order> o;

    @BeforeEach
    void setUp() {
        renderer = new AnsiSqlRenderer(JpaMetaResolver.INSTANCE);
        u = TableRef.of(User.class, "u");
        o = TableRef.of(Order.class, "o");
    }

    // ================================================================== //
    //  SELECT — basic
    // ================================================================== //

    @Test
    void simpleSelect_generatesCorrectSql() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId), u.col(User::getUsername))
                .where(u.col(User::getStatus).eq("ACTIVE"))
                .build();

        RenderedSql r = renderer.render(stmt);

        assertThat(r.sql()).isEqualTo(
                "SELECT u.id, u.username FROM t_user u WHERE u.status = :p1");
        assertThat(r.params()).hasSize(1).containsEntry("p1", "ACTIVE");
    }

    @Test
    void selectStar_noExplicitColumns() {
        SelectStatement stmt = SQL.from(u).build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("SELECT * FROM t_user u");
    }

    @Test
    void selectDistinct_prefixesDistinct() {
        SelectStatement stmt = SQL.from(u)
                .distinct()
                .select(u.col(User::getStatus))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).startsWith("SELECT DISTINCT");
        assertThat(r.sql()).contains("u.status");
    }

    @Test
    void selectWithAlias_addsAsClause() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getUsername).as("name"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.username AS name");
    }

    // ================================================================== //
    //  SELECT — JOINs
    // ================================================================== //

    @Test
    void innerJoin_generatesCorrectOnClause() {
        SelectStatement stmt = SQL.from(u)
                .join(o).on(u.col(User::getId).eq(o.col(Order::getUserId)))
                .select(u.col(User::getUsername), o.col(Order::getAmount))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("INNER JOIN t_order o ON u.id = o.user_id");
        // The right side is a ColExpr — rendered as expression, not a param
        assertThat(r.params()).isEmpty();
    }

    @Test
    void leftJoin_usesLeftKeyword() {
        SelectStatement stmt = SQL.from(u)
                .leftJoin(o).on(u.col(User::getId).eq(o.col(Order::getUserId)))
                .select(u.col(User::getUsername))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("LEFT JOIN t_order o");
    }

    // ================================================================== //
    //  SELECT — WHERE conditions
    // ================================================================== //

    @Test
    void whereWithAnd_wrapsEachChildInParens() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getStatus).eq("ACTIVE")
                        .and(u.col(User::getAge).gte(18)))
                .build();

        RenderedSql r = renderer.render(stmt);
        // AND wraps each child in parens
        assertThat(r.sql()).contains("(u.status = :p1) AND (u.age >= :p2)");
        assertThat(r.params()).containsEntry("p1", "ACTIVE").containsEntry("p2", 18);
    }

    @Test
    void whereWithOr_wrapsEachChildInParens() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getStatus).eq("ACTIVE")
                        .or(u.col(User::getStatus).eq("PENDING")))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("(u.status = :p1) OR (u.status = :p2)");
    }

    @Test
    void notCondition_prefixesNot() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getStatus).eq("INACTIVE").not())
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("NOT (u.status = :p1)");
    }

    @Test
    void inCondition_generatesInClause() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getStatus).in("ACTIVE", "PENDING"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.status IN (");
        assertThat(r.params()).hasSize(2);
    }

    @Test
    void notInCondition_generatesNotIn() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getStatus).notIn("DELETED"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.status NOT IN (");
    }

    @Test
    void isNullCondition_generatesIsNull() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getEmail).isNull())
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.email IS NULL");
    }

    @Test
    void isNotNullCondition_generatesIsNotNull() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getEmail).isNotNull())
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.email IS NOT NULL");
    }

    @Test
    void betweenCondition_generatesBetween() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getAge).between(18, 65))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.age BETWEEN :p1 AND :p2");
        assertThat(r.params()).containsEntry("p1", 18).containsEntry("p2", 65);
    }

    @Test
    void notBetween_generatesNotBetween() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getAge).notBetween(18, 65))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.age NOT BETWEEN");
    }

    @Test
    void likeCondition_generatesLike() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getUsername).like("alice%"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.username LIKE :p1");
        assertThat(r.params()).containsEntry("p1", "alice%");
    }

    @Test
    void notLike_generatesNotLike() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(u.col(User::getUsername).notLike("bob%"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.username NOT LIKE");
    }

    // ================================================================== //
    //  SELECT — GROUP BY / HAVING
    // ================================================================== //

    @Test
    void groupByAndHaving() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getStatus), countStar().as("cnt"))
                .groupBy(u.col(User::getStatus))
                .having(countStar().gt(5L))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("GROUP BY u.status");
        assertThat(r.sql()).contains("HAVING COUNT(*) > :p1");
        assertThat(r.params()).containsEntry("p1", 5L);
    }

    // ================================================================== //
    //  SELECT — ORDER BY / LIMIT / OFFSET
    // ================================================================== //

    @Test
    void orderByMultiple() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .orderBy(u.col(User::getAge).desc(), u.col(User::getUsername).asc())
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("ORDER BY u.age DESC, u.username ASC");
    }

    @Test
    void limitAndOffset() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .limit(10)
                .offset(20)
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("LIMIT :p1");
        assertThat(r.sql()).contains("OFFSET :p2");
        assertThat(r.params()).containsEntry("p1", 10L).containsEntry("p2", 20L);
    }

    // ================================================================== //
    //  SELECT — locking
    // ================================================================== //

    @Test
    void forUpdate() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .forUpdate()
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("FOR UPDATE");
    }

    @Test
    void forUpdateNowait() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .forUpdate(LockMode.UPDATE_NOWAIT)
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("FOR UPDATE NOWAIT");
    }

    @Test
    void forUpdateSkipLocked() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .forUpdate(LockMode.UPDATE_SKIP_LOCKED)
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("FOR UPDATE SKIP LOCKED");
    }

    @Test
    void forShare() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .forUpdate(LockMode.SHARE)
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("FOR SHARE");
    }

    // ================================================================== //
    //  SET OPERATIONS
    // ================================================================== //

    @Test
    void union() {
        SelectStatement active   = SQL.from(u).select(u.col(User::getId)).where(u.col(User::getStatus).eq("ACTIVE")).build();
        SelectStatement inactive = SQL.from(u).select(u.col(User::getId)).where(u.col(User::getStatus).eq("INACTIVE")).build();

        RenderedSql r = renderer.render(active.union(inactive));
        assertThat(r.sql()).contains(" UNION ");
        assertThat(r.params()).hasSize(2);
    }

    @Test
    void unionAll() {
        SelectStatement a = SQL.from(u).select(u.col(User::getId)).build();
        SelectStatement b = SQL.from(u).select(u.col(User::getId)).build();

        RenderedSql r = renderer.render(a.unionAll(b));
        assertThat(r.sql()).contains(" UNION ALL ");
    }

    @Test
    void intersect() {
        SelectStatement a = SQL.from(u).select(u.col(User::getId)).where(u.col(User::getStatus).eq("ACTIVE")).build();
        SelectStatement b = SQL.from(u).select(u.col(User::getId)).where(u.col(User::getAge).gte(30)).build();

        RenderedSql r = renderer.render(a.intersect(b));
        assertThat(r.sql()).contains(" INTERSECT ");
    }

    @Test
    void except() {
        SelectStatement a = SQL.from(u).select(u.col(User::getId)).build();
        SelectStatement b = SQL.from(u).select(u.col(User::getId)).where(u.col(User::getStatus).eq("INACTIVE")).build();

        RenderedSql r = renderer.render(a.except(b));
        assertThat(r.sql()).contains(" EXCEPT ");
    }

    // ================================================================== //
    //  EXISTS subquery
    // ================================================================== //

    @Test
    void existsSubquery() {
        SelectStatement sub = SQL.from(o)
                .select(val(1))
                .where(o.col(Order::getUserId).eq(u.col(User::getId)))
                .build();

        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getUsername))
                .where(ExistsCondition.exists(sub))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("EXISTS (SELECT");
    }

    @Test
    void notExistsSubquery() {
        SelectStatement sub = SQL.from(o).select(val(1))
                .where(o.col(Order::getUserId).eq(u.col(User::getId))).build();

        RenderedSql r = renderer.render(
                SQL.from(u).select(u.col(User::getId))
                        .where(ExistsCondition.notExists(sub)).build());

        assertThat(r.sql()).contains("NOT EXISTS (SELECT");
    }

    // ================================================================== //
    //  Window functions
    // ================================================================== //

    @Test
    void windowFunction_rowNumber() {
        SelectStatement stmt = SQL.from(u)
                .select(
                        u.col(User::getUsername),
                        rowNumber().over(w -> w
                                .partitionBy(u.col(User::getStatus))   // explicit col with alias
                                .orderBy(u.col(User::getAge).desc())
                        ).as("rn")
                )
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("ROW_NUMBER() OVER (PARTITION BY u.status ORDER BY u.age DESC) AS rn");
    }

    @Test
    void windowFunction_sumWithFrame() {
        SelectStatement stmt = SQL.from(o)
                .select(
                        o.col(Order::getOrderNo),
                        sum(o.col(Order::getAmount)).over(w -> w
                                .partitionBy(o.col(Order::getUserId))
                                .orderBy(o.col(Order::getId).asc())
                                .rowsBetween(FrameBound.unboundedPreceding(), FrameBound.currentRow())
                        ).as("running_total")
                )
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("SUM(o.amount) OVER (PARTITION BY o.user_id ORDER BY o.id ASC "
                + "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total");
    }

    @Test
    void aggregateCountDistinct() {
        SelectStatement stmt = SQL.from(u)
                .select(countDistinct(u.col(User::getStatus)).as("statuses"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("COUNT(DISTINCT u.status) AS statuses");
    }

    @Test
    void aggregateCountStar() {
        SelectStatement stmt = SQL.from(u)
                .select(countStar().as("total"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("COUNT(*) AS total");
    }

    // ================================================================== //
    //  UPDATE
    // ================================================================== //

    @Test
    void updateStatement() {
        UpdateStatement stmt = SQL.update(User.class)
                .set(User::getStatus, "INACTIVE")
                .set(User::getAge, 99)
                .where(u.col(User::getId).eq(1L))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).startsWith("UPDATE t_user SET");
        assertThat(r.sql()).contains("status = :p1");
        assertThat(r.sql()).contains("age = :p2");
        assertThat(r.sql()).contains("WHERE u.id = :p3");
        assertThat(r.params()).containsEntry("p1", "INACTIVE")
                              .containsEntry("p2", 99)
                              .containsEntry("p3", 1L);
    }

    @Test
    void updateWithReturning() {
        UpdateStatement stmt = SQL.update(User.class)
                .set(User::getStatus, "INACTIVE")
                .where(u.col(User::getId).eq(1L))
                .returning("id", "status")
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("RETURNING id, status");
    }

    // ================================================================== //
    //  INSERT
    // ================================================================== //

    @Test
    void insertStatement() {
        InsertStatement stmt = SQL.insertInto(User.class)
                .set(User::getUsername, "alice")
                .set(User::getEmail, "alice@example.com")
                .set(User::getAge, 30)
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).startsWith("INSERT INTO t_user (");
        assertThat(r.sql()).contains(") VALUES (");
        assertThat(r.params()).containsValue("alice")
                              .containsValue("alice@example.com")
                              .containsValue(30);
    }

    // ================================================================== //
    //  DELETE
    // ================================================================== //

    @Test
    void deleteStatement() {
        DeleteStatement stmt = SQL.deleteFrom(User.class)
                .where(u.col(User::getStatus).eq("INACTIVE"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("DELETE FROM t_user WHERE u.status = :p1");
        assertThat(r.params()).containsEntry("p1", "INACTIVE");
    }

    @Test
    void deleteWithReturning() {
        DeleteStatement stmt = SQL.deleteFrom(User.class)
                .where(u.col(User::getStatus).eq("INACTIVE"))
                .returning("id", "username")
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("RETURNING id, username");
    }

    // ================================================================== //
    //  CTE
    // ================================================================== //

    @Test
    void cteWith() {
        SelectStatement cteQuery = SQL.from(u)
                .select(u.col(User::getId), u.col(User::getUsername))
                .where(u.col(User::getStatus).eq("ACTIVE"))
                .build();

        TableRef<User> active = TableRef.of(User.class, "au");
        SelectStatement stmt = SQL.from(active)
                .with(CteDef.of("active_users", cteQuery))
                .select(active.star())
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).startsWith("WITH active_users AS (SELECT");
        assertThat(r.sql()).contains("SELECT au.*");
        assertThat(r.params()).containsValue("ACTIVE");
    }

    // ================================================================== //
    //  Raw expressions / conditions
    // ================================================================== //

    @Test
    void rawExpression_passthroughSql() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId), SQL.<String>raw("UPPER(u.username)"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("UPPER(u.username)");
    }

    @Test
    void rawCondition_passthroughSql() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .where(SQL.rawCond("u.age BETWEEN 18 AND 65"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("u.age BETWEEN 18 AND 65");
    }

    // ================================================================== //
    //  andWhere accumulation
    // ================================================================== //

    @Test
    void andWhere_accumulatesConditions() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getId))
                .andWhere(u.col(User::getStatus).eq("ACTIVE"))
                .andWhere(u.col(User::getAge).gte(18))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).contains("AND");
        assertThat(r.params()).hasSize(2);
    }

    // ================================================================== //
    //  ConditionBuilder (lambda WHERE)
    // ================================================================== //

    @Test
    void conditionBuilder_simpleEq() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w.eq(u.col(User::getStatus), "ACTIVE"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("SELECT u.* FROM t_user u WHERE u.status = :p1");
        assertThat(r.params()).containsEntry("p1", "ACTIVE");
    }

    @Test
    void conditionBuilder_multiplePredicatesAnd() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w
                        .eq(u.col(User::getStatus), "ACTIVE")
                        .gte(u.col(User::getAge), 18))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE (u.status = :p1) AND (u.age >= :p2)");
        assertThat(r.params()).containsEntry("p1", "ACTIVE").containsEntry("p2", 18);
    }

    @Test
    void conditionBuilder_whenFalse_skipsCondition() {
        String status = null;  // null → skip
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w
                        .eq(u.col(User::getStatus), status, status != null)
                        .gte(u.col(User::getAge), 18))
                .build();

        RenderedSql r = renderer.render(stmt);
        // Only age condition should appear
        assertThat(r.sql()).isEqualTo("SELECT u.* FROM t_user u WHERE u.age >= :p1");
        assertThat(r.params()).hasSize(1).containsEntry("p1", 18);
    }

    @Test
    void conditionBuilder_whenAllFalse_noWhereClause() {
        String status = null;
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w.eq(u.col(User::getStatus), status, status != null))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("SELECT u.* FROM t_user u");
    }

    @Test
    void conditionBuilder_nestedOrGroup() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w
                        .eq(u.col(User::getStatus), "ACTIVE")
                        .or(sub -> sub
                                .eq(u.col(User::getAge), 18)
                                .eq(u.col(User::getAge), 21)))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE (u.status = :p1) AND ((u.age = :p2) OR (u.age = :p3))");
        assertThat(r.params()).hasSize(3);
    }

    @Test
    void conditionBuilder_nestedAndGroup() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w
                        .eq(u.col(User::getStatus), "ACTIVE")
                        .and(sub -> sub
                                .gte(u.col(User::getAge), 18)
                                .lte(u.col(User::getAge), 65)))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE (u.status = :p1) AND ((u.age >= :p2) AND (u.age <= :p3))");
        assertThat(r.params()).hasSize(3);
    }

    @Test
    void conditionBuilder_nestedNotGroup() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w
                        .not(sub -> sub.eq(u.col(User::getStatus), "DELETED")))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE NOT (u.status = :p1)");
        assertThat(r.params()).containsEntry("p1", "DELETED");
    }

    @Test
    void conditionBuilder_inCollection() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w.in(u.col(User::getStatus), java.util.List.of("ACTIVE", "PENDING")))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE u.status IN (:p1, :p2)");
        assertThat(r.params()).hasSize(2);
    }

    @Test
    void conditionBuilder_isNull() {
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w.isNull(u.col(User::getEmail)))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("SELECT u.* FROM t_user u WHERE u.email IS NULL");
    }

    @Test
    void conditionBuilder_between_withWhen() {
        int minAge = 18;
        SelectStatement stmt = SQL.from(u)
                .select(u.star())
                .where(w -> w.between(u.col(User::getAge), minAge, 65, minAge > 0))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo("SELECT u.* FROM t_user u WHERE u.age BETWEEN :p1 AND :p2");
    }

    @Test
    void conditionBuilder_updateWhere() {
        UpdateStatement stmt = SQL.update(User.class)
                .set(User::getStatus, "INACTIVE")
                .where(w -> w
                        .eq(u.col(User::getId), 1L)
                        .ne(u.col(User::getStatus), "DELETED", true))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "UPDATE t_user SET status = :p1 WHERE (u.id = :p2) AND (u.status <> :p3)");
        assertThat(r.params()).hasSize(3);
    }

    @Test
    void conditionBuilder_deleteWhere() {
        DeleteStatement stmt = SQL.deleteFrom(User.class)
                .where(w -> w.eq(u.col(User::getStatus), "DELETED"))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "DELETE FROM t_user WHERE u.status = :p1");
        assertThat(r.params()).containsEntry("p1", "DELETED");
    }

    @Test
    void conditionBuilder_having_lambda() {
        SelectStatement stmt = SQL.from(u)
                .select(u.col(User::getStatus), SQL.count(u.col(User::getId)).as("cnt"))
                .groupBy(u.col(User::getStatus))
                .having(h -> h.gt(SQL.count(u.col(User::getId)), 5L))
                .build();

        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.status, COUNT(u.id) AS cnt FROM t_user u GROUP BY u.status HAVING COUNT(u.id) > :p1");
        assertThat(r.params()).containsEntry("p1", 5L);
    }

    // ================================================================== //
    //  PaginationDialect — LimitOffsetDialect (default)
    // ================================================================== //

    @Test
    void limitOffset_defaultDialect_limitAndOffset() {
        SelectStatement stmt = SQL.from(u).select(u.star())
                .where(w -> w.eq(u.col(User::getStatus), "ACTIVE"))
                .orderBy(u.col(User::getId).asc())
                .limit(20).offset(40)
                .build();

        RenderedSql r = renderer.render(stmt);  // renderer uses LimitOffsetDialect by default
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE u.status = :p1 ORDER BY u.id ASC LIMIT :p2 OFFSET :p3");
        assertThat(r.params()).containsEntry("p2", 20L).containsEntry("p3", 40L);
    }

    @Test
    void limitOffset_limitOnly() {
        SelectStatement stmt = SQL.from(u).select(u.star()).limit(10).build();
        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("LIMIT :p1");
        assertThat(r.sql()).doesNotContain("OFFSET");
    }

    @Test
    void limitOffset_offsetOnly() {
        SelectStatement stmt = SQL.from(u).select(u.star()).offset(5).build();
        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("OFFSET :p1");
        assertThat(r.sql()).doesNotContain("LIMIT");
    }

    @Test
    void limitOffset_page_convenience() {
        // .page(2, 10) → LIMIT 10 OFFSET 20
        SelectStatement stmt = SQL.from(u).select(u.star()).page(2, 10).build();
        RenderedSql r = renderer.render(stmt);
        assertThat(r.sql()).endsWith("LIMIT :p1 OFFSET :p2");
        assertThat(r.params()).containsEntry("p1", 10L).containsEntry("p2", 20L);
    }

    // ================================================================== //
    //  PaginationDialect — OffsetFetchDialect (SQL Server / Oracle / DB2)
    // ================================================================== //

    @Test
    void offsetFetch_limitAndOffset() {
        AnsiSqlRenderer sqlServerRenderer =
                new AnsiSqlRenderer(JpaMetaResolver.INSTANCE, OffsetFetchDialect.INSTANCE);

        SelectStatement stmt = SQL.from(u).select(u.star())
                .where(w -> w.eq(u.col(User::getStatus), "ACTIVE"))
                .orderBy(u.col(User::getId).asc())
                .limit(20).offset(40)
                .build();

        RenderedSql r = sqlServerRenderer.render(stmt);
        assertThat(r.sql()).isEqualTo(
                "SELECT u.* FROM t_user u WHERE u.status = :p1 ORDER BY u.id ASC"
                + " OFFSET :p2 ROWS FETCH NEXT :p3 ROWS ONLY");
        assertThat(r.params()).containsEntry("p2", 40L).containsEntry("p3", 20L);
    }

    @Test
    void offsetFetch_limitOnly_prepends_offset0() {
        AnsiSqlRenderer sqlServerRenderer =
                new AnsiSqlRenderer(JpaMetaResolver.INSTANCE, OffsetFetchDialect.INSTANCE);

        SelectStatement stmt = SQL.from(u).select(u.star()).limit(10).build();
        RenderedSql r = sqlServerRenderer.render(stmt);
        assertThat(r.sql()).endsWith("OFFSET 0 ROWS FETCH NEXT :p1 ROWS ONLY");
    }

    @Test
    void offsetFetch_offsetOnly() {
        AnsiSqlRenderer sqlServerRenderer =
                new AnsiSqlRenderer(JpaMetaResolver.INSTANCE, OffsetFetchDialect.INSTANCE);

        SelectStatement stmt = SQL.from(u).select(u.star()).offset(5).build();
        RenderedSql r = sqlServerRenderer.render(stmt);
        assertThat(r.sql()).endsWith("OFFSET :p1 ROWS");
        assertThat(r.sql()).doesNotContain("FETCH NEXT");
    }

    @Test
    void customDialect_lambda() {
        // Custom dialect: SAP HANA / Sybase IQ style
        AnsiSqlRenderer customRenderer = new AnsiSqlRenderer(JpaMetaResolver.INSTANCE,
                (sb, limit, offset, ctx) -> {
                    if (offset != null) sb.append(" START AT ").append(ctx.addParam(offset + 1));
                    if (limit  != null) sb.append(" LIMIT ").append(ctx.addParam(limit));
                });

        SelectStatement stmt = SQL.from(u).select(u.star()).limit(10).offset(20).build();
        RenderedSql r = customRenderer.render(stmt);
        assertThat(r.sql()).endsWith("START AT :p1 LIMIT :p2");
        assertThat(r.params()).containsEntry("p1", 21L).containsEntry("p2", 10L);
    }
}
