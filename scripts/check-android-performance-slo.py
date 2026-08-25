#!/usr/bin/env python3
"""Fail CI when Reader V3 Macrobenchmark frame-tail SLOs regress.

AndroidX writes FrameTimingMetric samples under sampledMetrics.frameDurationCpuMs.runs as one
list per benchmark iteration. Percentiles shown by Macrobenchmark are computed from the flattened
sample pool. This script mirrors AndroidX MetricResult.getPercentile() so CI thresholds and the
benchmark's own P95/P99 reporting use the same interpolation semantics.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import sys
from typing import Any, Iterable


def finite_numbers(value: Any) -> Iterable[float]:
    if isinstance(value, bool):
        return
    if isinstance(value, (int, float)) and math.isfinite(float(value)):
        yield float(value)
    elif isinstance(value, list):
        for item in value:
            yield from finite_numbers(item)


def androidx_percentile(values: list[float], percentile: int) -> float:
    """Mirror androidx.benchmark.MetricResult.getPercentile()."""
    if not values:
        raise ValueError("percentile requires at least one sample")
    ordered = sorted(values)
    ideal_index = min(100, max(0, percentile)) / 100.0 * (len(ordered) - 1)
    first_index = int(ideal_index)
    second_index = min(first_index + 1, len(ordered) - 1)
    ratio = ideal_index - first_index
    return ordered[first_index] * (1.0 - ratio) + ordered[second_index] * ratio


def frame_sample_sets(payload: Any) -> list[tuple[str, list[float]]]:
    """Return one flattened frameDurationCpuMs sample pool per benchmark record."""
    if not isinstance(payload, dict):
        return []
    benchmarks = payload.get("benchmarks")
    if not isinstance(benchmarks, list):
        return []
    found: list[tuple[str, list[float]]] = []
    for index, benchmark in enumerate(benchmarks):
        if not isinstance(benchmark, dict):
            continue
        sampled = benchmark.get("sampledMetrics")
        if not isinstance(sampled, dict):
            continue
        metric = sampled.get("frameDurationCpuMs")
        if not isinstance(metric, dict):
            continue
        samples = list(finite_numbers(metric.get("runs")))
        if samples:
            name = str(benchmark.get("name") or f"benchmark[{index}]")
            class_name = str(benchmark.get("className") or "")
            label = f"{class_name}.{name}".strip(".")
            found.append((label, samples))
    return found


def collect_files(paths: list[str]) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for raw in paths:
        path = pathlib.Path(raw)
        if path.is_dir():
            files.extend(sorted(path.rglob("*-benchmarkData.json")))
        elif path.is_file():
            files.append(path)
    return list(dict.fromkeys(files))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", help="benchmarkData.json files or directories")
    parser.add_argument("--p95-ms", type=float, default=float(os.environ.get("JINGDU_FRAME_P95_MS", "40")))
    parser.add_argument("--p99-ms", type=float, default=float(os.environ.get("JINGDU_FRAME_P99_MS", "80")))
    args = parser.parse_args()

    files = collect_files(args.paths)
    if not files:
        print("performance SLO: no benchmarkData.json found", file=sys.stderr)
        return 2

    rows: list[tuple[str, str, int, float, float, int]] = []
    limits = {95: args.p95_ms, 99: args.p99_ms}
    for file in files:
        try:
            payload = json.loads(file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"performance SLO: cannot parse {file}: {exc}", file=sys.stderr)
            return 2
        for benchmark, samples in frame_sample_sets(payload):
            for percentile in (95, 99):
                rows.append(
                    (
                        str(file),
                        benchmark,
                        percentile,
                        androidx_percentile(samples, percentile),
                        limits[percentile],
                        len(samples),
                    )
                )

    if not rows:
        print("performance SLO: no sampledMetrics.frameDurationCpuMs.runs evidence found", file=sys.stderr)
        return 2

    # Every frame-producing benchmark is independently gated. This prevents a fast journey from
    # masking a slow one when result files contain multiple tests.
    failed = False
    seen = {95: 0, 99: 0}
    for file, benchmark, percentile, value, limit, sample_count in rows:
        seen[percentile] += 1
        status = "PASS" if value <= limit else "FAIL"
        print(
            f"{benchmark} frameDurationCpuMs P{percentile}: {value:.3f}ms "
            f"limit={limit:.3f}ms samples={sample_count} {status} ({file})"
        )
        failed |= value > limit

    if not all(seen[p] for p in (95, 99)):
        print(f"performance SLO: incomplete percentile evidence: {seen}", file=sys.stderr)
        return 2
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
