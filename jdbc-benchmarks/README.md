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

Default sample-time mode provides p95/p99 and other percentiles. Use `-bm thrpt`
in a separate run for throughput. `-prof gc` reports allocation and GC metrics,
not peak heap: measure peak heap separately with a profiler. Each operation is
one query or one complete batch, **not one row**; compare like `rowCount` or
`batchSize` values. Record the full JMH output and confidence intervals.

* `MappingBenchmark` uses a detached `CachedRowSet` and a precompiled mapper to
  isolate mapping and list construction from database round trips. The cold
  variant creates a mapper cache per operation; it is not JVM cold start.
* `QueryBenchmark` compares JDBC, JdbcTemplate with handwritten row mappers,
  raw executor mapping, and the typed DSL. Each performs a prepared, ordered
  query against the same indexed, non-null dataset. The typed spec is built
  outside timing except in `dslBuildAndSelectBean`; SQL rendering remains
  inside both typed variants. The handwritten implementations
  do not implement every strict conversion and NULL check, so they are useful
  lower-overhead baselines, not semantically identical for arbitrary data.
  `dslSelectListConsumeBean` and `dslSelectEachBean` consume every mapped row
  with the same JMH blackhole; the latter avoids a DSL result list. Driver-side
  buffering is unchanged by the callback. `dslPageBean` and `dslSliceBean`
  return the same content; Page executes a count query whereas Slice fetches
  one additional row to determine whether there is a next slice.
* `BatchBenchmark` compares prepared JDBC, JdbcTemplate and a prebuilt DSL batch.
  The chunked DSL variant uses at most 100 rows per batch. Each inserts the same
  rows. Deletion occurs outside the timed operation, but
  still affects database caches and transaction/log activity; `Level.Invocation`
  setup also adds harness overhead, especially for small batches. GC allocation
  metrics include the cleanup/setup allocations, so they are not insert-only.
* `BatchPreparationBenchmark` replaces JDBC execution with a recording template
  and measures DSL SQL rendering and parameter preparation without a database,
  driver or network. Its result is not end-to-end insert performance.

H2 is the default and is useful for local repeatability, not for estimating
production network or database behavior. To test MySQL or PostgreSQL, supply a
**dedicated empty benchmark database**:

```bash
export JDBC_BENCH_URL='jdbc:postgresql://localhost:5432/benchmark'
export JDBC_BENCH_USER='benchmark'
export JDBC_BENCH_PASSWORD='...'
export JDBC_BENCH_ALLOW_EXTERNAL=true
java -jar jdbc-benchmarks/target/benchmarks.jar -prof gc -foe true -rf json -rff results-postgres.json
```

For the optional PostgreSQL cursor, batch and slice integration tests, with the same
dedicated database environment variables set, run:

```bash
./mvnw -Pbenchmarks -pl jdbc-benchmarks -am -Dtest=PostgresCursorIT,PostgresBatchIT,PostgresSliceIT -Dsurefire.failIfNoSpecifiedTests=false test
```

They verify an actual server-side cursor with a positive fetch size inside a
transaction, compare persisted batch values with and without chunking, and
exercise Slice pagination. The performance workloads do not automatically
configure cursor fetching; configure and measure driver/transaction behavior separately.

The harness creates `jpa_dsl_bench_rows` and `jpa_dsl_bench_writes` and drops
only tables it successfully created. It fails if either table already exists;
do not use a production database. A crash may leave tables behind. Do not run
multiple benchmark processes concurrently against the same database. The normal
correctness tests use a separate in-memory database. The pool
allows up to 32 connections; transaction/autocommit, durability, driver batch
rewrite settings and database configuration affect results and must be recorded.
Never commit credentials or environment-specific result files.
