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
