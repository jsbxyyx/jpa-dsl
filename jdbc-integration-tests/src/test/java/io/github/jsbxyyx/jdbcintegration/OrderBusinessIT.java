package io.github.jsbxyyx.jdbcintegration;

import io.github.jsbxyyx.jdbcdsl.BatchInsertBuilder;
import io.github.jsbxyyx.jdbcdsl.JPageable;
import io.github.jsbxyyx.jdbcdsl.JdbcDslExecutor;
import io.github.jsbxyyx.jdbcdsl.SelectBuilder;
import io.github.jsbxyyx.jdbcdsl.UpdateBuilder;
import io.github.jsbxyyx.jdbcdsl.UpsertBuilder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OrderBusinessIT {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    private JdbcDslExecutor executor;
    private Customer customer;
    private Inventory inventory;

    @BeforeAll
    static void createSchema() throws SQLException {
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(5), "MySQL connection should be valid");
            assertTrue(
                    connection
                            .getMetaData()
                            .getDatabaseProductName()
                            .toLowerCase(Locale.ROOT)
                            .contains("mysql"),
                    "Integration tests must run against MySQL, not H2 or another database");
            assertTrue(
                    connection.getMetaData().getDatabaseProductVersion().startsWith("8."),
                    "Integration tests require MySQL 8.0");
        }

        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS jpa_dsl_it_customer (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    customer_no VARCHAR(40) NOT NULL UNIQUE,
                    customer_name VARCHAR(120) NOT NULL,
                    email VARCHAR(160),
                    status VARCHAR(20) NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS jpa_dsl_it_inventory (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    sku VARCHAR(60) NOT NULL UNIQUE,
                    product_name VARCHAR(120) NOT NULL,
                    unit_price DECIMAL(12, 2) NOT NULL,
                    quantity BIGINT NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS jpa_dsl_it_order (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(60) NOT NULL UNIQUE,
                    customer_id BIGINT NOT NULL,
                    sku VARCHAR(60) NOT NULL,
                    quantity BIGINT NOT NULL,
                    total_amount DECIMAL(12, 2) NOT NULL,
                    status VARCHAR(24) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_jpa_dsl_it_order_customer_status (customer_id, status)
                ) ENGINE=InnoDB
                """);
        jdbc.execute(
                "ALTER TABLE jpa_dsl_it_order MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("order_date", "DATE NULL");
        ensureColumn("pickup_time", "TIME NULL");
    }

    private static void ensureColumn(String columnName, String columnDefinition) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                "jpa_dsl_it_order",
                columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE jpa_dsl_it_order ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    @BeforeEach
    void setUp() {
        executor = new JdbcDslExecutor(new NamedParameterJdbcTemplate(dataSource));
        jdbc.update("DELETE FROM jpa_dsl_it_order");
        jdbc.update("DELETE FROM jpa_dsl_it_inventory");
        jdbc.update("DELETE FROM jpa_dsl_it_customer");

        customer = new Customer("CUS-1001", "Alice Chen", "alice@example.test", "ACTIVE");
        executor.save(customer);
        inventory = new Inventory("SKU-KEYBOARD", "Mechanical Keyboard", new BigDecimal("89.90"), 10L);
        executor.save(inventory);
    }

    @Test
    void placeOrder_persistsOrderAndReturnsGeneratedId() {
        SalesOrder order = new SalesOrder(
                "ORD-1001", customer.getId(), inventory.getSku(), 2L, new BigDecimal("179.80"), "PENDING");

        executor.save(order);

        assertNotNull(order.getId(), "AUTO_INCREMENT id should be written back to the entity");
        SalesOrder stored = jdbc.queryForObject(
                "SELECT id, order_no, customer_id, sku, quantity, total_amount, status FROM jpa_dsl_it_order WHERE id = ?",
                (rs, rowNum) -> new SalesOrder(
                        rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getLong("customer_id"),
                        rs.getString("sku"),
                        rs.getLong("quantity"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("status")),
                order.getId());
        assertNotNull(stored);
        assertEquals(order.getId(), stored.getId());
        assertEquals("ORD-1001", stored.getOrderNo());
        assertEquals(customer.getId(), stored.getCustomerId());
        assertEquals(new BigDecimal("179.80"), stored.getTotalAmount());
        assertEquals("PENDING", stored.getStatus());
    }

    @Test
    void createdAt_roundTripsMySqlTimestampAsLocalDateTime() {
        LocalDateTime expected = LocalDateTime.of(2026, 10, 28, 9, 35, 42);
        SalesOrder order = new SalesOrder(
                "ORD-TIME-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING");
        order.setCreatedAt(expected);

        executor.save(order);

        SalesOrder result = executor.select(SelectBuilder.from(SalesOrder.class)
                        .where(w -> w.eq(SalesOrder::getOrderNo, "ORD-TIME-1"))
                        .mapToEntity())
                .get(0);
        assertEquals(expected, result.getCreatedAt());
        assertEquals(
                expected,
                jdbc.queryForObject(
                        "SELECT created_at FROM jpa_dsl_it_order WHERE order_no = ?",
                        (rs, rowNum) -> rs.getObject("created_at", LocalDateTime.class),
                        "ORD-TIME-1"));
    }

    @Test
    void javaTimeTypes_roundTripDateTimeAndTimeValues() {
        LocalDate expectedDate = LocalDate.of(2026, 10, 28);
        LocalTime expectedTime = LocalTime.of(14, 25, 36);
        LocalDateTime expectedDateTime = LocalDateTime.of(2026, 10, 28, 14, 25, 36);
        SalesOrder order = new SalesOrder(
                "ORD-JAVA-TIME-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING");
        order.setCreatedAt(expectedDateTime);
        order.setOrderDate(expectedDate);
        order.setPickupTime(expectedTime);

        executor.save(order);

        SalesOrder result = executor.select(SelectBuilder.from(SalesOrder.class)
                        .where(w -> w.eq(SalesOrder::getOrderNo, "ORD-JAVA-TIME-1"))
                        .mapToEntity())
                .get(0);
        assertEquals(expectedDate, result.getOrderDate());
        assertEquals(expectedTime, result.getPickupTime());
        assertEquals(expectedDateTime, result.getCreatedAt());

        assertEquals(
                expectedDate,
                jdbc.queryForObject(
                        "SELECT order_date FROM jpa_dsl_it_order WHERE order_no = ?",
                        (rs, rowNum) -> rs.getObject("order_date", LocalDate.class),
                        "ORD-JAVA-TIME-1"));
        assertEquals(
                expectedTime,
                jdbc.queryForObject(
                        "SELECT pickup_time FROM jpa_dsl_it_order WHERE order_no = ?",
                        (rs, rowNum) -> rs.getObject("pickup_time", LocalTime.class),
                        "ORD-JAVA-TIME-1"));
        assertEquals(
                expectedDateTime,
                jdbc.queryForObject(
                        "SELECT created_at FROM jpa_dsl_it_order WHERE order_no = ?",
                        (rs, rowNum) -> rs.getObject("created_at", LocalDateTime.class),
                        "ORD-JAVA-TIME-1"));
    }

    @Test
    void queryOrders_filtersByCustomerAndStatusAndMapsBigintDecimal() {
        executor.save(
                new SalesOrder("ORD-1002", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-1003", customer.getId(), inventory.getSku(), 3L, new BigDecimal("269.70"), "PENDING"));
        executor.save(new SalesOrder("ORD-1004", 9999999999L, inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));

        List<SalesOrder> result = executor.select(SelectBuilder.from(SalesOrder.class)
                .where(w -> w.eq(SalesOrder::getCustomerId, customer.getId()).eq(SalesOrder::getStatus, "PAID"))
                .orderBy(io.github.jsbxyyx.jdbcdsl.JSort.byAsc(SalesOrder::getOrderNo))
                .mapToEntity());

        assertEquals(1, result.size());
        assertEquals("ORD-1002", result.get(0).getOrderNo());
        assertEquals(customer.getId(), result.get(0).getCustomerId());
        assertEquals(new BigDecimal("89.90"), result.get(0).getTotalAmount());
    }

    @Test
    void customerOrderReport_joinsCustomersAndOrdersAndMapsDto() {
        executor.save(new SalesOrder(
                "ORD-REPORT-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-REPORT-2", customer.getId(), inventory.getSku(), 2L, new BigDecimal("179.80"), "PENDING"));

        List<OrderSummary> report = executor.select(SelectBuilder.from(Customer.class, "c")
                .select(
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(Customer::getCustomerName, "c"),
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(SalesOrder::getOrderNo, "o"),
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(SalesOrder::getTotalAmount, "o")
                                .as("totalAmount"))
                .join(
                        SalesOrder.class,
                        "o",
                        io.github.jsbxyyx.jdbcdsl.JoinType.INNER,
                        on -> on.eq(Customer::getId, "c", SalesOrder::getCustomerId, "o"))
                .where(w -> w.eq(SalesOrder::getStatus, "o", "PAID"))
                .mapTo(OrderSummary.class));

        assertEquals(1, report.size());
        assertEquals("Alice Chen", report.get(0).getCustomerName());
        assertEquals("ORD-REPORT-1", report.get(0).getOrderNo());
        assertEquals(new BigDecimal("89.90"), report.get(0).getTotalAmount());
    }

    @Test
    void inSubquery_filtersOrdersByCustomersMatchingSubquery() {
        Customer inactiveCustomer = new Customer("CUS-1003", "Inactive Customer", null, "INACTIVE");
        executor.save(inactiveCustomer);
        executor.save(new SalesOrder(
                "ORD-SUBQUERY-ACTIVE", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-SUBQUERY-INACTIVE",
                inactiveCustomer.getId(),
                inventory.getSku(),
                1L,
                new BigDecimal("89.90"),
                "PAID"));

        var activeCustomerIds = SelectBuilder.from(Customer.class, "c")
                .select(io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(Customer::getId, "c"))
                .where(w -> w.eq(Customer::getStatus, "c", "ACTIVE"))
                .mapTo(Long.class);

        List<SalesOrder> result = executor.select(SelectBuilder.from(SalesOrder.class)
                .where(w -> w.inSubquery(
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(SalesOrder::getCustomerId), activeCustomerIds))
                .mapToEntity());

        assertEquals(1, result.size());
        assertEquals("ORD-SUBQUERY-ACTIVE", result.get(0).getOrderNo());
    }

    @Test
    void aggregateReport_groupsByStatusAndAppliesHaving() {
        executor.save(
                new SalesOrder("ORD-AGG-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-AGG-2", customer.getId(), inventory.getSku(), 2L, new BigDecimal("179.80"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-AGG-3", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));

        List<OrderStatusSummary> report = executor.select(SelectBuilder.from(SalesOrder.class)
                .select(
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(SalesOrder::getStatus)
                                .as("status"),
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar().as("orderCount"),
                        io.github.jsbxyyx.jdbcdsl.SqlFunctions.sum(SalesOrder::getTotalAmount)
                                .as("totalAmount"))
                .groupBy(SalesOrder::getStatus)
                .having(h -> h.gt(io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar(), 1L))
                .mapTo(OrderStatusSummary.class));

        assertEquals(1, report.size());
        assertEquals("PAID", report.get(0).getStatus());
        assertEquals(2L, report.get(0).getOrderCount());
        assertEquals(new BigDecimal("269.70"), report.get(0).getTotalAmount());
    }

    @Test
    void nullableEmail_isNullFilterReturnsAndMapsNullValue() {
        Customer customerWithoutEmail = new Customer("CUS-1002", "No Email", null, "ACTIVE");
        executor.save(customerWithoutEmail);

        List<Customer> result = executor.select(SelectBuilder.from(Customer.class)
                .where(w -> w.isNull(Customer::getEmail).eq(Customer::getCustomerNo, "CUS-1002"))
                .mapToEntity());

        assertEquals(1, result.size());
        assertEquals("No Email", result.get(0).getCustomerName());
        assertNull(result.get(0).getEmail());
    }

    @Test
    void changeOrderStatus_updatesOnlyMatchingPendingOrder() {
        SalesOrder order = new SalesOrder(
                "ORD-1005", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING");
        executor.save(order);

        int affected = executor.executeUpdate(UpdateBuilder.from(SalesOrder.class)
                .set(SalesOrder::getStatus, "PAID")
                .where(w -> w.eq(SalesOrder::getId, order.getId()).eq(SalesOrder::getStatus, "PENDING"))
                .build());

        assertEquals(1, affected);
        assertEquals(
                "PAID",
                jdbc.queryForObject("SELECT status FROM jpa_dsl_it_order WHERE id = ?", String.class, order.getId()));

        int duplicateTransition = executor.executeUpdate(UpdateBuilder.from(SalesOrder.class)
                .set(SalesOrder::getStatus, "PAID")
                .where(w -> w.eq(SalesOrder::getId, order.getId()).eq(SalesOrder::getStatus, "PENDING"))
                .build());
        assertEquals(0, duplicateTransition, "A repeated transition must not match an already-paid order");
    }

    @Test
    void batchInsert_importsSeveralOrdersAndPreservesEachRowParameters() {
        List<SalesOrder> orders = List.of(
                new SalesOrder(
                        "ORD-BATCH-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"),
                new SalesOrder(
                        "ORD-BATCH-2", customer.getId(), inventory.getSku(), 2L, new BigDecimal("179.80"), "PAID"),
                new SalesOrder(
                        "ORD-BATCH-3", customer.getId(), inventory.getSku(), 3L, new BigDecimal("269.70"), "SHIPPED"));

        int[] counts = executor.executeBatchInsert(
                BatchInsertBuilder.of(SalesOrder.class, orders).build());

        assertEquals(3, counts.length);
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jpa_dsl_it_order WHERE order_no LIKE 'ORD-BATCH-%'", Integer.class));
        assertEquals(
                new BigDecimal("539.40"),
                jdbc.queryForObject(
                        "SELECT SUM(total_amount) FROM jpa_dsl_it_order WHERE order_no LIKE 'ORD-BATCH-%'",
                        BigDecimal.class));
    }

    @Test
    void inventoryUpsert_insertsThenUpdatesOnUniqueSku() {
        var upsert = UpsertBuilder.into(Inventory.class)
                .onConflict(Inventory::getSku)
                .doUpdate(Inventory::getProductName, Inventory::getUnitPrice, Inventory::getQuantity)
                .build();

        Inventory newItem = new Inventory("SKU-MOUSE", "Wireless Mouse", new BigDecimal("39.50"), 5L);
        executor.upsert(upsert, newItem);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jpa_dsl_it_inventory WHERE sku = 'SKU-MOUSE'", Integer.class));

        Inventory revisedItem = new Inventory("SKU-MOUSE", "Wireless Mouse Pro", new BigDecimal("49.50"), 8L);
        executor.upsert(upsert, revisedItem);

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jpa_dsl_it_inventory WHERE sku = 'SKU-MOUSE'", Integer.class));
        assertEquals(
                "Wireless Mouse Pro",
                jdbc.queryForObject(
                        "SELECT product_name FROM jpa_dsl_it_inventory WHERE sku = 'SKU-MOUSE'", String.class));
        assertEquals(
                new BigDecimal("49.50"),
                jdbc.queryForObject(
                        "SELECT unit_price FROM jpa_dsl_it_inventory WHERE sku = 'SKU-MOUSE'", BigDecimal.class));
        assertEquals(
                8L,
                jdbc.queryForObject("SELECT quantity FROM jpa_dsl_it_inventory WHERE sku = 'SKU-MOUSE'", Long.class));
    }

    @Test
    void inventoryReservation_decrementsAtomicallyAndRejectsInsufficientStock() {
        int reserved = executor.executeUpdate(UpdateBuilder.from(Inventory.class)
                .subtract(Inventory::getQuantity, 2L)
                .where(w -> w.eq(Inventory::getSku, inventory.getSku()).gte(Inventory::getQuantity, 2L))
                .build());

        assertEquals(1, reserved);
        assertEquals(
                8L,
                jdbc.queryForObject(
                        "SELECT quantity FROM jpa_dsl_it_inventory WHERE sku = ?", Long.class, inventory.getSku()));

        int rejected = executor.executeUpdate(UpdateBuilder.from(Inventory.class)
                .subtract(Inventory::getQuantity, 20L)
                .where(w -> w.eq(Inventory::getSku, inventory.getSku()).gte(Inventory::getQuantity, 20L))
                .build());

        assertEquals(0, rejected);
        assertEquals(
                8L,
                jdbc.queryForObject(
                        "SELECT quantity FROM jpa_dsl_it_inventory WHERE sku = ?", Long.class, inventory.getSku()));
    }

    @Test
    void concurrentReservations_doNotOversellInventory() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> reserveStock(start));
            Future<Integer> second = pool.submit(() -> reserveStock(start));
            start.countDown();

            int firstReserved = first.get(10, TimeUnit.SECONDS);
            int secondReserved = second.get(10, TimeUnit.SECONDS);

            assertEquals(1, firstReserved + secondReserved);
            assertEquals(
                    3L,
                    jdbc.queryForObject(
                            "SELECT quantity FROM jpa_dsl_it_inventory WHERE sku = ?", Long.class, inventory.getSku()));
        } finally {
            pool.shutdownNow();
        }
    }

    private int reserveStock(CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent reservation");
        }
        return executor.executeUpdate(UpdateBuilder.from(Inventory.class)
                .subtract(Inventory::getQuantity, 7L)
                .where(w -> w.eq(Inventory::getSku, inventory.getSku()).gte(Inventory::getQuantity, 7L))
                .build());
    }

    @Test
    void failedCheckout_rollsBackOrderAndInventoryChanges() {
        assertThrows(
                IllegalStateException.class,
                () -> transactions.executeWithoutResult(status -> {
                    int changed = executor.executeUpdate(UpdateBuilder.from(Inventory.class)
                            .subtract(Inventory::getQuantity, 2L)
                            .where(w ->
                                    w.eq(Inventory::getSku, inventory.getSku()).gte(Inventory::getQuantity, 2L))
                            .build());
                    if (changed != 1) {
                        throw new IllegalStateException("Insufficient stock");
                    }
                    executor.save(new SalesOrder(
                            "ORD-ROLLBACK",
                            customer.getId(),
                            inventory.getSku(),
                            2L,
                            new BigDecimal("179.80"),
                            "PENDING"));
                    throw new IllegalStateException("Simulated downstream payment failure");
                }));

        assertEquals(
                10L,
                jdbc.queryForObject(
                        "SELECT quantity FROM jpa_dsl_it_inventory WHERE sku = ?", Long.class, inventory.getSku()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jpa_dsl_it_order WHERE order_no = 'ORD-ROLLBACK'", Integer.class));
    }

    @Test
    void pageGroupedOrders_countsGroupsAfterHaving() {
        executor.save(new SalesOrder(
                "ORD-GPAGE-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-GPAGE-2", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-GPAGE-3", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));
        executor.save(new SalesOrder(
                "ORD-GPAGE-4", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));
        executor.save(new SalesOrder(
                "ORD-GPAGE-5", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "SHIPPED"));

        var page = executor.selectPage(
                SelectBuilder.from(SalesOrder.class)
                        .select(
                                io.github.jsbxyyx.jdbcdsl.SqlFunctions.col(SalesOrder::getStatus)
                                        .as("status"),
                                io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar()
                                        .as("orderCount"),
                                io.github.jsbxyyx.jdbcdsl.SqlFunctions.sum(SalesOrder::getTotalAmount)
                                        .as("totalAmount"))
                        .groupBy(SalesOrder::getStatus)
                        .having(h -> h.gt(io.github.jsbxyyx.jdbcdsl.SqlFunctions.countStar(), 1L))
                        .mapTo(OrderStatusSummary.class),
                JPageable.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertTrue(List.of("PAID", "PENDING").contains(page.getContent().get(0).getStatus()));
    }

    @Test
    void pageDistinctCustomersJoinedToOrders_countsUniqueCustomers() {
        Customer secondCustomer = new Customer("CUS-PAGE-2", "Bob Chen", "bob@example.test", "ACTIVE");
        executor.save(secondCustomer);
        executor.save(new SalesOrder(
                "ORD-JOIN-PAGE-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-JOIN-PAGE-2", customer.getId(), inventory.getSku(), 2L, new BigDecimal("179.80"), "PAID"));
        executor.save(new SalesOrder(
                "ORD-JOIN-PAGE-3", secondCustomer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PAID"));

        var page = executor.selectPage(
                SelectBuilder.from(Customer.class, "c")
                        .distinct()
                        .join(
                                SalesOrder.class,
                                "o",
                                io.github.jsbxyyx.jdbcdsl.JoinType.INNER,
                                on -> on.eq(Customer::getId, "c", SalesOrder::getCustomerId, "o"))
                        .orderBy(io.github.jsbxyyx.jdbcdsl.JSort.byAsc(Customer::getCustomerNo))
                        .mapToEntity(),
                JPageable.of(0, 1));

        assertEquals(2, page.getTotalElements(), "COUNT must count distinct customer rows, not joined orders");
        assertEquals(1, page.getContent().size());
        assertEquals("CUS-1001", page.getContent().get(0).getCustomerNo());

        var secondPage = executor.selectPage(
                SelectBuilder.from(Customer.class, "c")
                        .distinct()
                        .join(
                                SalesOrder.class,
                                "o",
                                io.github.jsbxyyx.jdbcdsl.JoinType.INNER,
                                on -> on.eq(Customer::getId, "c", SalesOrder::getCustomerId, "o"))
                        .orderBy(io.github.jsbxyyx.jdbcdsl.JSort.byAsc(Customer::getCustomerNo))
                        .mapToEntity(),
                JPageable.of(1, 1));
        assertEquals(2, secondPage.getTotalElements());
        assertEquals(1, secondPage.getContent().size());
        assertEquals("CUS-PAGE-2", secondPage.getContent().get(0).getCustomerNo());
    }

    @Test
    void pageOrders_returnsStablePageAndCorrectTotalCount() {
        executor.save(new SalesOrder(
                "ORD-PAGE-1", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));
        executor.save(new SalesOrder(
                "ORD-PAGE-2", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));
        executor.save(new SalesOrder(
                "ORD-PAGE-3", customer.getId(), inventory.getSku(), 1L, new BigDecimal("89.90"), "PENDING"));

        var page = executor.selectPage(
                SelectBuilder.from(SalesOrder.class)
                        .where(w -> w.eq(SalesOrder::getCustomerId, customer.getId()))
                        .orderBy(io.github.jsbxyyx.jdbcdsl.JSort.byAsc(SalesOrder::getOrderNo))
                        .mapToEntity(),
                JPageable.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("ORD-PAGE-1", page.getContent().get(0).getOrderNo());
        assertEquals("ORD-PAGE-2", page.getContent().get(1).getOrderNo());
    }

    private static DataSource createDataSource() {
        String url = env("MYSQL_IT_URL", null);
        if (url == null || url.isBlank()) {
            String host = env("MYSQL_IT_HOST", "mysql-svc");
            String port = env("MYSQL_IT_PORT", "3306");
            String database = env("MYSQL_IT_DATABASE", "test");
            url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        }
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(env("MYSQL_IT_USERNAME", "root"));
        source.setPassword(env("MYSQL_IT_PASSWORD", "root"));
        return source;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static class OrderStatusSummary {
        private String status;
        private Long orderCount;
        private BigDecimal totalAmount;

        public OrderStatusSummary() {}

        public String getStatus() {
            return status;
        }

        public Long getOrderCount() {
            return orderCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }
    }

    public static class OrderSummary {
        private String customerName;
        private String orderNo;
        private BigDecimal totalAmount;

        public OrderSummary() {}

        public String getCustomerName() {
            return customerName;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }
    }

    @Entity
    @Table(name = "jpa_dsl_it_customer")
    public static class Customer {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "customer_no")
        private String customerNo;

        @Column(name = "customer_name")
        private String customerName;

        @Column(name = "email")
        private String email;

        @Column(name = "status")
        private String status;

        public Customer() {}

        Customer(String customerNo, String customerName, String email, String status) {
            this.customerNo = customerNo;
            this.customerName = customerName;
            this.email = email;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public String getCustomerNo() {
            return customerNo;
        }

        public String getCustomerName() {
            return customerName;
        }

        public String getEmail() {
            return email;
        }

        public String getStatus() {
            return status;
        }

        public void setCustomerNo(String customerNo) {
            this.customerNo = customerNo;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @Entity
    @Table(name = "jpa_dsl_it_inventory")
    public static class Inventory {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "sku")
        private String sku;

        @Column(name = "product_name")
        private String productName;

        @Column(name = "unit_price")
        private BigDecimal unitPrice;

        @Column(name = "quantity")
        private Long quantity;

        public Inventory() {}

        Inventory(String sku, String productName, BigDecimal unitPrice, Long quantity) {
            this.sku = sku;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public Long getId() {
            return id;
        }

        public String getSku() {
            return sku;
        }

        public String getProductName() {
            return productName;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public Long getQuantity() {
            return quantity;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public void setQuantity(Long quantity) {
            this.quantity = quantity;
        }
    }

    @Entity
    @Table(name = "jpa_dsl_it_order")
    public static class SalesOrder {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "order_no")
        private String orderNo;

        @Column(name = "customer_id")
        private Long customerId;

        @Column(name = "sku")
        private String sku;

        @Column(name = "quantity")
        private Long quantity;

        @Column(name = "total_amount")
        private BigDecimal totalAmount;

        @Column(name = "status")
        private String status;

        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "order_date")
        private LocalDate orderDate;

        @Column(name = "pickup_time")
        private LocalTime pickupTime;

        public SalesOrder() {}

        SalesOrder(String orderNo, Long customerId, String sku, Long quantity, BigDecimal totalAmount, String status) {
            this.orderNo = orderNo;
            this.customerId = customerId;
            this.sku = sku;
            this.quantity = quantity;
            this.totalAmount = totalAmount;
            this.status = status;
            this.createdAt = LocalDateTime.now().withNano(0);
        }

        SalesOrder(
                Long id,
                String orderNo,
                Long customerId,
                String sku,
                Long quantity,
                BigDecimal totalAmount,
                String status) {
            this.id = id;
            this.orderNo = orderNo;
            this.customerId = customerId;
            this.sku = sku;
            this.quantity = quantity;
            this.totalAmount = totalAmount;
            this.status = status;
            this.createdAt = LocalDateTime.now().withNano(0);
        }

        public Long getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public String getSku() {
            return sku;
        }

        public Long getQuantity() {
            return quantity;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDate getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(LocalDate orderDate) {
            this.orderDate = orderDate;
        }

        public LocalTime getPickupTime() {
            return pickupTime;
        }

        public void setPickupTime(LocalTime pickupTime) {
            this.pickupTime = pickupTime;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public void setQuantity(Long quantity) {
            this.quantity = quantity;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
