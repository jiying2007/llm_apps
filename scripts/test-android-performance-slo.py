#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("check-android-performance-slo.py")
spec = importlib.util.spec_from_file_location("jingdu_android_performance_slo", MODULE_PATH)
assert spec and spec.loader
slo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(slo)


class AndroidPerformanceSloTest(unittest.TestCase):
    def test_androidx_percentile_matches_linear_interpolation(self) -> None:
        values = [float(value) for value in range(1, 101)]
        self.assertAlmostEqual(95.05, slo.androidx_percentile(values, 95))
        self.assertAlmostEqual(99.01, slo.androidx_percentile(values, 99))

    def test_sampled_metrics_are_flattened_per_benchmark(self) -> None:
        payload = {
            "benchmarks": [
                {
                    "name": "pageTurn10MiB",
                    "className": "ReaderJourneyBenchmark",
                    "sampledMetrics": {"frameDurationCpuMs": {"runs": [[5.0, 10.0], [15.0, 20.0]]}},
                }
            ]
        }
        rows = slo.frame_sample_sets(payload)
        self.assertEqual([("ReaderJourneyBenchmark.pageTurn10MiB", [5.0, 10.0, 15.0, 20.0])], rows)

    def test_required_interaction_sample_counts_reject_truncated_evidence(self) -> None:
        valid = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 500),
            ("ReaderJourneyBenchmark.chaptersAndSettings10MiB", [1.0] * 50),
        ]
        self.assertEqual([], slo.required_sample_failures(valid))
        truncated = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 2),
        ]
        failures = slo.required_sample_failures(truncated)
        self.assertTrue(any("continuousScroll10MiB samples=2" in failure for failure in failures))
        self.assertTrue(any("missing required frame evidence: chaptersAndSettings10MiB" in failure for failure in failures))

    def test_release_defaults_remain_product_slo(self) -> None:
        self.assertEqual((40.0, 80.0), slo.resolve_limits("release", None, None))
        self.assertEqual(40.0, slo.RELEASE_P95_MS)
        self.assertEqual(80.0, slo.RELEASE_P99_MS)

    def test_hosted_defaults_are_separate_regression_ceiling(self) -> None:
        self.assertEqual((120.0, 160.0), slo.resolve_limits("hosted-regression", None, None))
        self.assertEqual(0.15, slo.HOSTED_MAX_REGRESSION_RATIO)

    def test_hosted_baseline_requires_complete_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "baseline.json"
            payload = {
                "schemaVersion": 1,
                "kind": "reader-v3-hosted-emulator-regression-baseline",
                "benchmarks": {
                    "pageTurn10MiB": {"p95Ms": 80.0, "p99Ms": 110.0},
                    "continuousScroll10MiB": {"p95Ms": 95.0, "p99Ms": 130.0},
                    "chaptersAndSettings10MiB": {"p95Ms": 100.0, "p99Ms": 140.0},
                },
            }
            path.write_text(json.dumps(payload), encoding="utf-8")
            loaded = slo.load_hosted_baseline(str(path))
            self.assertEqual(95.0, loaded["continuousScroll10MiB"]["p95Ms"])
            del payload["benchmarks"]["chaptersAndSettings10MiB"]
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing benchmark"):
                slo.load_hosted_baseline(str(path))

    def test_hosted_relative_limit_and_absolute_ceiling_both_apply(self) -> None:
        baseline = 100.0
        relative = baseline * (1.0 + slo.HOSTED_MAX_REGRESSION_RATIO)
        self.assertAlmostEqual(115.0, relative)
        self.assertEqual(115.0, min(slo.HOSTED_P95_MS, relative))
        high_baseline_relative = 119.0 * (1.0 + slo.HOSTED_MAX_REGRESSION_RATIO)
        self.assertEqual(120.0, min(slo.HOSTED_P95_MS, high_baseline_relative))

    def test_real_shape_file_discovery(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            report = root / "ReaderJourneyBenchmark-benchmarkData.json"
            report.write_text(json.dumps({"benchmarks": []}), encoding="utf-8")
            self.assertEqual([report], slo.collect_files([directory]))

    def test_reader_profile_product_contract(self) -> None:
        contract = pathlib.Path(__file__).with_name("verify-reader-profile-contract.py")
        result = subprocess.run(
            [sys.executable, str(contract)],
            cwd=contract.parent.parent,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("Reader V3 Baseline/Startup Profile contract OK", result.stdout)


if __name__ == "__main__":
    unittest.main()
