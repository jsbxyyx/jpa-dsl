# MySQL business integration tests

This module executes the public `jdbc-dsl` API against a real MySQL 8.0 service. It does not start Docker or use Testcontainers.

## Default connection

- Host: `mysql-svc`
- Port: `3306`
- Database: `test`
- Username: `root`
- Password: `root`

Override any setting with environment variables:

- `MYSQL_IT_URL` (if set, overrides host/port/database)
- `MYSQL_IT_HOST`
- `MYSQL_IT_PORT`
- `MYSQL_IT_DATABASE`
- `MYSQL_IT_USERNAME`
- `MYSQL_IT_PASSWORD`

## Run

From the repository root:

```bash
./mvnw -pl jdbc-integration-tests -am -Pmysql-it verify
```

The profile runs tests matching `*IT.java` through Maven Failsafe. Normal unit-test runs do not execute these external-database tests.

## Data safety

Tests create only the namespaced tables `jpa_dsl_it_customer`, `jpa_dsl_it_inventory`, and `jpa_dsl_it_order` in the configured database. Before each test they delete rows from those three tables only. They do not drop the database, truncate other tables, or remove these tables.

Use a dedicated test database. The configured account needs permission to create tables and read/write rows in that database.

## Current business scenarios

- Create an order and verify the generated ID and persisted BIGINT/DECIMAL values.
- Filter orders by customer and status.
- Generate a customer/order JOIN report and map results into a DTO.
- Change an order from pending to paid with a guarded update.
- Batch insert orders and verify aggregate results.
- MySQL UPSERT against a unique SKU.
- Roll back a checkout when a downstream step fails.
- Verify pagination results and total count.
