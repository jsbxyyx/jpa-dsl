import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("summarize-baseline.py")
WORKLOADS = {
    "query": ("rowCount", ("1", "100", "10000"), ("jdbcBean", "springBean", "dslRawBean", "dslSelectBean")),
    "batch": ("batchSize", ("1", "100", "1000"), ("jdbcBatch", "springBatch", "dslBatch", "dslChunkedBatch")),
}


class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for mode in ("sample", "thrpt"):
            for threads in (1, 8, 32):
                for workload, (parameter, sizes, methods) in WORKLOADS.items():
                    entries = []
                    for size in sizes:
                        for index, method in enumerate(methods):
                            entries.append({
                                "benchmark": f"io.github.jsbxyyx.jdbcdsl.bench.{workload.capitalize()}Benchmark.{method}",
                                "params": {parameter: size},
                                "mode": mode,
                                "threads": threads,
                                "primaryMetric": {
                                    "score": index + 1,
                                    "scoreUnit": "us/op" if mode == "sample" else "ops/us",
                                    "scoreError": 0.1,
                                    "scorePercentiles": {"95.0": 4, "99.0": 5},
                                },
                                "secondaryMetrics": {"gc.alloc.rate.norm": {"score": 32, "scoreUnit": "B/op"}},
                            })
                    self.path(workload, mode, threads).write_text(json.dumps(entries), encoding="utf-8")

    def path(self, workload, mode, threads):
        return self.directory / f"{workload}-{mode}-{threads}.json"

    def run_summary(self):
        return subprocess.run([sys.executable, str(SCRIPT), str(self.directory)], capture_output=True, text=True)

    def test_complete_matrix(self):
        result = self.run_summary()
        self.assertEqual(result.returncode, 0, result.stderr)
        rows = list(csv.DictReader(result.stdout.splitlines()))
        self.assertEqual(len(rows), 144)
        first = rows[0]
        self.assertEqual((first["method"], first["score_vs_jdbc"], first["p99"]), ("jdbcBean", "1.0", "5"))
        self.assertEqual(rows[1]["score_vs_jdbc"], "2.0")

    def test_missing_file_fails_without_partial_csv(self):
        self.path("query", "sample", 1).unlink()
        result = self.run_summary()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("Invalid or incomplete", result.stderr)

    def test_duplicate_entry_fails(self):
        path = self.path("batch", "thrpt", 32)
        entries = json.loads(path.read_text(encoding="utf-8"))
        entries.append(entries[0])
        path.write_text(json.dumps(entries), encoding="utf-8")
        result = self.run_summary()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("duplicate entry", result.stderr)

    def test_nonfinite_score_fails(self):
        path = self.path("query", "sample", 8)
        entries = json.loads(path.read_text(encoding="utf-8"))
        entries[0]["primaryMetric"]["score"] = float("nan")
        path.write_text(json.dumps(entries), encoding="utf-8")
        result = self.run_summary()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("not a finite number", result.stderr)


if __name__ == "__main__":
    unittest.main()
