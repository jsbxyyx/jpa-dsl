# JDBC DSL performance benchmarks

This optional module measures the current implementation rather than asserting
that it meets an unspecified performance target. It is not part of the default
reactor or release.

## Build and smoke test

Use Java 17 and Maven. From the repository root:

```bash
./mvnw -Pbenchmarks -pl jdbc-benchmarks -am package
java -jar jdbc-benchmarks/target/benchmarks.jar -l
java -jar jdbc-benchmarks/target/benchmarks.jar '.*QueryBenchmark.*' -p rowCount=1 -wi 1 -i 1 -w 1s -r 1s -f 1 -foe true
```

The module's JUnit tests verify that the compared implementations return the
same values and insert the same rows. The short run above checks the harness;
**it is not a performance result**.

## Reproducible measurements

On an otherwise idle machine, use a fixed JDK, heap, CPU configuration, database,
driver, pool, schema, dataset and concurrency. Keep the commit, OS and hardware
details with the result. Run at least two forks and use the default warmup and
measurement settings, or document changes. Run all suites for one thread and
repeat with a representative `-t` value:

```bash
java -Xms2g -Xmx2g -jar jdbc-benchmarks/target/benchmarks.jar -prof gc -foe true -rf json -rff results.json
java -Xms2g -Xmx2g -jar jdbc-benchmarks/target/benchmarks.jar -t 8 -prof gc -foe true -rf json -rff results-8threads.json
```

For a repeatable query and batch matrix (1, 8 and 32 threads; sample-time
and throughput modes), set the dedicated database environment variables below
and run `bash jdbc-benchmarks/run-baseline.sh /path/to/new-results-directory`.
The output directory must not already exist. The script records the commit,
worktree status, host, JVM and JMH settings without recording database credentials
or the URL. Set `BENCH_DATABASE_LABEL` to a non-secret database/version label,
and record database configuration and driver settings separately. Defaults use
two forks, three warmup and five measurement iterations of two seconds each.
For explicitly labelled smoke runs only, the positive-integer environment
variables `JMH_FORKS`, `JMH_WARMUP_ITERATIONS`, `JMH_MEASUREMENT_ITERATIONS`
and `JMH_ITERATION_SECONDS` override these defaults.
Build from a clean, recorded commit for reproducible comparisons. The
environment file records the commit, worktree status and benchmark JAR hash,
but does not preserve uncommitted changes or database server configuration.
Record the database version and relevant settings separately.

After the matrix completes, validate all expected combinations and export a
CSV summary using Python 3:

```bash
python3 jdbc-benchmarks/summarize-baseline.py baseline-pg > baseline-pg.csv
```

`score_vs_jdbc` is the primary score divided by the corresponding JDBC score:
lower is better for sample time, higher is better for throughput. Compare only
like-for-like modes and units. The summary retains p95, p99, allocation and
score error; inspect the original JSON and logs for the complete measurements.

For a separate 30-minute stability run of a representative workload, use a
dedicated database and an unused output path, for example:

```bash
/usr/bin/time -v -o soak-resources.txt \
  java -Xms2g -Xmx2g -jar jdbc-benchmarks/target/benchmarks.jar \
  '.*QueryBenchmark.dslSelectBean' -p rowCount=100 -t 8 -bm thrpt \
  -f 1 -wi 3 -w 2s -i 30 -r 60s -prof gc -foe true \
  -rf json -rff soak-results.json
```

Inspect per-iteration throughput and GC metrics for degradation rather than
relying only on the aggregate. GNU time reports the maximum RSS of an
individual process (including waited-for children), not combined application
and database peak memory. Monitor the JVM and database/container separately
when aggregate or database memory is required. Do not run these commands against
the same database concurrently or on a machine shared with other workloads.

Default sample-time mode provides p95/p99 and other percentiles. Use `-bm thrpt`
in a separate run for throughput. `-prof gc` reports allocation and GC metrics,
not peak heap: measure peak heap separately with a profiler. Each operation is
one query or one complete batch, **not one row**; compare like `rowCount` or
`batchSize` values. Record the full JMH output and confidence intervals.

* `MappingBenchmark` uses a detached `CachedRowSet` and compares direct
  ResultSet mapping, Spring `BeanPropertyRowMapper`, cached JavaBeans setter
  reflection, and the DSL mapper. It isolates mapping and list construction
  from database round trips. The cold variant creates a mapper cache per
  operation; it is not JVM cold start.
* `QueryBenchmark` compares direct JDBC, JdbcTemplate with
  `BeanPropertyRowMapper`, JdbcTemplate with a handwritten `RowMapper`,
  MyBatis 3 (`SIMPLE` executor with annotation-based result mapping), raw
  executor mapping, and the typed DSL. Each performs a prepared, ordered query
  against the same indexed, non-null dataset and uses the same HikariDataSource.
  MyBatis configuration and mapper metadata are created during trial setup;
  each timed operation opens and closes its own SqlSession. The typed spec is built
  outside timing except in `dslBuildAndSelectBean`; SQL rendering remains
  inside both typed variants. The handwritten implementations
  do not implement every strict conversion and NULL check, so they are useful
  lower-overhead baselines, not semantically identical for arbitrary data.
  `dslSelectListConsumeBean` and `dslSelectEachBean` consume every mapped row
  with the same JMH blackhole; the latter avoids a DSL result list. Driver-side
  buffering is unchanged by the callback. `dslPageBean` and `dslSliceBean`
  return the same content; Page executes a count query whereas Slice fetches
  one additional row to determine whether there is a next slice.
* `BatchBenchmark` compares prepared JDBC, JdbcTemplate, MyBatis 3 (`BATCH`
  executor with one mapped INSERT per row), and a prebuilt DSL batch. MyBatis
  opens and closes a SqlSession per timed operation and flushes the queued batch
  once. The chunked DSL variant uses at most 100 rows per batch. Each inserts
  the same rows. MyBatis uses JDBC batch execution, not multi-row INSERT SQL.
  Each thread's ID range has a permanent boundary row to separate InnoDB index
  gaps during concurrent cleanup. Deletion occurs outside the timed operation, but
  still affects database caches and transaction/log activity; `Level.Invocation`
  setup also adds harness overhead, especially for small batches. GC allocation
  metrics include the cleanup/setup allocations, so they are not insert-only.
  Methods with the `Transactional` suffix run the complete batch in one explicit
  transaction and commit once: JDBC manages its connection directly, MyBatis
  uses a non-autocommit BATCH SqlSession, and Spring/DSL use the same
  DataSourceTransactionManager-backed TransactionTemplate. Unsuffixed methods
  retain their original autocommit behavior. Compare only methods with the same
  transaction mode. Chunked DSL still splits the batch into chunks inside the
  same outer transaction in its transactional variant; it does not commit once
  per chunk. The two modes help separate transaction-boundary costs from
  statement preparation/execution, but database latency and Level.Invocation
  cleanup can still dominate. Use multiple forks and inspect per-iteration
  distributions; do not infer a ranking from one short run.
* `BatchPreparationBenchmark` replaces JDBC execution with a recording template
  and compares DSL SQL rendering and parameter preparation with an explicit
  Spring named-parameter baseline for the same SQL and values, without a database,
  driver or network. The baseline uses fixed SQL and direct getters; it does not
  include DSL metadata lookup or rendering. Its result is not end-to-end insert
  performance.
* `PaginationRenderingBenchmark` measures dialect pagination rendering in
  isolation, including ordinary SQL, quoted parameter-like text and actual
  parameter-name collisions. It does not measure query execution.
* `NumericConversionBenchmark` isolates exact, integral Float/Double-to-Long
  conversion on preboxed inputs; it excludes JDBC reads and result mapping.

H2 is the default and is useful for local repeatability, not for estimating
production network or database behavior. To test MySQL or PostgreSQL, supply a
**dedicated empty benchmark database**. When `JDBC_BENCH_URL` is unset, the
harness can reuse the integration-test connection settings (`MYSQL_IT_URL`, or
`MYSQL_IT_HOST`/`MYSQL_IT_PORT`/`MYSQL_IT_DATABASE`, plus `MYSQL_IT_USERNAME` and
`MYSQL_IT_PASSWORD). The defaults match `jdbc-integration-tests` (`mysql-svc:3306`,
`test`, `root`/`root`); external database access still requires the explicit
`JDBC_BENCH_ALLOW_EXTERNAL=true` opt-in. Each benchmark JVM creates uniquely
suffixed tables (for example, `jpa_dsl_bench_rows_<run-id>` and
`jpa_dsl_bench_writes_<run-id>`), and drops only the exact tables it created
when the trial shuts down. `JDBC_BENCH_TABLE_PREFIX` can override the prefix; it
must be a simple SQL identifier prefix of at most 31 characters:

```bash
export JDBC_BENCH_URL='jdbc:postgresql://localhost:5432/benchmark'
export JDBC_BENCH_USER='benchmark'
export JDBC_BENCH_PASSWORD='...'
export JDBC_BENCH_ALLOW_EXTERNAL=true
java -jar jdbc-benchmarks/target/benchmarks.jar -prof gc -foe true -rf json -rff results-postgres.json
```

For the cross-database correctness suite, run with a dedicated PostgreSQL or
MySQL database configured:

```bash
./mvnw -Pbenchmarks -pl jdbc-benchmarks -am -Dtest=ExternalDatabaseCorrectnessIT,ExternalBatchConcurrencyIT,ExternalLockingIT -Dsurefire.failIfNoSpecifiedTests=false test
```

It exercises NULL and exact numeric mapping, logical-delete pagination,
transaction rollback across chunks, callback cleanup, concurrent record
mapping, 32-thread batch fixture isolation, and transactional row locking across
pagination APIs. For additional PostgreSQL
cursor, batch, slice and numeric tests:

```bash
./mvnw -Pbenchmarks -pl jdbc-benchmarks -am -Dtest=PostgresCursorIT,PostgresBatchIT,PostgresSliceIT,PostgresNumericMappingIT -Dsurefire.failIfNoSpecifiedTests=false test
```

They verify an actual server-side cursor with a positive fetch size inside a
transaction, compare persisted batch values with and without chunking, and
exercise Slice pagination and exact numeric mapping. The performance workloads
do not automatically configure cursor fetching; configure and measure
driver/transaction behavior separately.

The harness creates per-run tables using a random 12-character suffix and drops
only those exact tables it successfully created. A forcibly terminated process
may leave orphaned tables; inspect and remove only tables with the configured
benchmark prefix and a confirmed stale run ID. The harness never automatically
drops pre-existing tables. Separate suffixes allow independent benchmark JVMs
to coexist, although they still share database CPU, I/O and connection limits.
Do not use a production database. The normal correctness tests use a separate
in-memory database. The pool
allows up to 32 connections; transaction/autocommit, durability, driver batch
rewrite settings and database configuration affect results and must be recorded.
Never commit credentials or environment-specific result files.
