#!/usr/bin/env python3
"""Validate a completed baseline matrix and write its measurements as CSV."""

import csv
import json
import math
import sys
from pathlib import Path


WORKLOADS = {
    "query": ("rowCount", ("1", "100", "10000"), ("jdbcBean", "springBean", "dslRawBean", "dslSelectBean")),
    "batch": ("batchSize", ("1", "100", "1000"), ("jdbcBatch", "springBatch", "dslBatch", "dslChunkedBatch")),
}


def finite_number(value, description):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f"{description} is not a finite number: {value!r}")
    return value


def matrix_rows(directory):
    for mode in ("sample", "thrpt"):
        for threads in (1, 8, 32):
            for workload, (parameter, sizes, methods) in WORKLOADS.items():
                filename = directory / f"{workload}-{mode}-{threads}.json"
                with filename.open(encoding="utf-8") as file:
                    entries = json.load(file)
                expected = {(size, method) for size in sizes for method in methods}
                found = {}
                for entry in entries:
                    benchmark_class, method = entry["benchmark"].rsplit(".", 1)
                    key = (entry["params"][parameter], method)
                    expected_class = f"io.github.jsbxyyx.jdbcdsl.bench.{workload.capitalize()}Benchmark"
                    if (benchmark_class != expected_class or key not in expected or key in found
                            or entry["mode"] != mode or entry["threads"] != threads):
                        raise ValueError(f"{filename}: unexpected or duplicate entry {key}")
                    primary = entry["primaryMetric"]
                    score = finite_number(primary["score"], f"{filename} {key} score")
                    alloc_metric = entry["secondaryMetrics"]["gc.alloc.rate.norm"]
                    if alloc_metric["scoreUnit"] != "B/op":
                        raise ValueError(f"{filename} {key}: unexpected allocation unit")
                    allocation = finite_number(alloc_metric["score"], f"{filename} {key} allocation")
                    percentiles = primary.get("scorePercentiles", {})
                    p95 = finite_number(percentiles["95.0"], f"{filename} {key} p95") if mode == "sample" else ""
                    p99 = finite_number(percentiles["99.0"], f"{filename} {key} p99") if mode == "sample" else ""
                    found[key] = (score, primary["scoreUnit"], primary["scoreError"], p95, p99, allocation)
                if found.keys() != expected:
                    raise ValueError(f"{filename}: missing entries {sorted(expected - found.keys())}")
                for size in sizes:
                    reference = found[(size, methods[0])]
                    if reference[0] <= 0:
                        raise ValueError(f"{filename}: JDBC reference score must be positive")
                    for method in methods:
                        score, unit, error, p95, p99, allocation = found[(size, method)]
                        if unit != reference[1]:
                            raise ValueError(f"{filename}: incompatible score units for {size}")
                        yield (workload, mode, threads, size, method, score, unit, error, p95, p99,
                               allocation, score / reference[0])


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 jdbc-benchmarks/summarize-baseline.py OUTPUT_DIRECTORY")
    try:
        rows = list(matrix_rows(Path(sys.argv[1])))
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise SystemExit(f"Invalid or incomplete benchmark matrix: {error}") from error
    writer = csv.writer(sys.stdout, lineterminator="\n")
    writer.writerow(("workload", "mode", "threads", "size", "method", "score", "score_unit",
                     "score_error", "p95", "p99", "gc_alloc_bytes_per_op", "score_vs_jdbc"))
    writer.writerows(rows)


if __name__ == "__main__":
    main()
