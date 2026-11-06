#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: bash jdbc-benchmarks/run-baseline.sh OUTPUT_DIRECTORY" >&2
    exit 2
fi
if [[ -z "${JDBC_BENCH_URL:-}" || "${JDBC_BENCH_ALLOW_EXTERNAL:-}" != true ]]; then
    echo "Set JDBC_BENCH_URL and JDBC_BENCH_ALLOW_EXTERNAL=true for a dedicated empty database." >&2
    exit 2
fi
if [[ ! -f jdbc-benchmarks/target/benchmarks.jar ]]; then
    echo "Build jdbc-benchmarks/target/benchmarks.jar first; run from the repository root." >&2
    exit 2
fi
if [[ -e "$1" ]]; then
    echo "Output path already exists; refusing to overwrite: $1" >&2
    exit 2
fi

warmup_iterations="${JMH_WARMUP_ITERATIONS:-3}"
measurement_iterations="${JMH_MEASUREMENT_ITERATIONS:-5}"
iteration_seconds="${JMH_ITERATION_SECONDS:-2}"
forks="${JMH_FORKS:-2}"
for value in "$warmup_iterations" "$measurement_iterations" "$iteration_seconds" "$forks"; do
    if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
        echo "JMH iteration, duration and fork settings must be positive integers." >&2
        exit 2
    fi
done

mkdir -- "$1"
output=$(cd -- "$1" && pwd)
{
    echo "UTC: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "Commit: $(git rev-parse HEAD)"
    echo "Worktree status:"
    git status --short
    echo "OS: $(uname -a)"
    echo "Database label: ${BENCH_DATABASE_LABEL:-unspecified}"
    echo "JMH: warmup=$warmup_iterations measurement=$measurement_iterations seconds=$iteration_seconds forks=$forks"
    echo "JVM heap: -Xms2g -Xmx2g"
    echo "Benchmark artifact SHA-256:"
    sha256sum jdbc-benchmarks/target/benchmarks.jar
    echo "JVM:"
    java -version 2>&1
    echo "CPU:"
    lscpu
} > "$output/environment.txt"

for mode in sample thrpt; do
    for threads in 1 8 32; do
        for workload in query batch; do
            if [[ "$workload" == query ]]; then
                selector='.*QueryBenchmark\.(jdbcBean|springBean|dslRawBean|dslSelectBean)'
                parameter='rowCount=1,100,10000'
            else
                selector='.*BatchBenchmark\.(jdbcBatch|springBatch|dslBatch|dslChunkedBatch)'
                parameter='batchSize=1,100,1000'
            fi
            name="${workload}-${mode}-${threads}"
            echo "Running $name"
            java -Xms2g -Xmx2g -jar jdbc-benchmarks/target/benchmarks.jar "$selector" \
                -p "$parameter" -t "$threads" -bm "$mode" \
                -wi "$warmup_iterations" -i "$measurement_iterations" \
                -w "${iteration_seconds}s" -r "${iteration_seconds}s" -f "$forks" \
                -prof gc -foe true -rf json -rff "$output/$name.json" > "$output/$name.log" 2>&1
        done
    done
done
