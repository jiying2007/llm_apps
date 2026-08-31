#!/usr/bin/env python3
"""Validate Reader Macrobenchmark frame-tail evidence.

Release mode is the product SLO and remains P95 <= 40 ms / P99 <= 80 ms.
Hosted-regression mode is deliberately separate: GitHub's software-emulated Android guest is used
only to detect regressions against a checked-in hosted baseline, with an additional absolute safety
ceiling. Both modes require the same complete real-journey sample floors.

AndroidX writes FrameTimingMetric samples under sampledMetrics.frameDurationCpuMs.runs as one list
per benchmark iteration. Percentiles shown by Macrobenchmark are computed from the flattened sample
pool. This script mirrors AndroidX MetricResult.getPercentile() interpolation semantics.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import sys
from typing import Any, Iterable


REQUIRED_MIN_SAMPLES = {
    "pageTurn10MiB": 20,
    "continuousScroll10MiB": 500,
    "chaptersAndSettings10MiB": 50,
}
RELEASE_P95_MS = 40.0
RELEASE_P99_MS = 80.0
# Evidence-frozen hosted absolute ceilings. The source baseline's worst 15% relative limits are
# P95~=155.36 ms and P99~=215.88 ms, so 160/220 leaves the relative gate authoritative while also
# bounding future baseline drift. These values are never used by Release mode.
HOSTED_P95_MS = 160.0
HOSTED_P99_MS = 220.0
HOSTED_MAX_REGRESSION_RATIO = 0.15
HOSTED_BASELINE_SCHEMA_VERSION = 1


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


def required_sample_failures(sample_sets: list[tuple[str, list[float]]]) -> list[str]:
    failures: list[str] = []
    for suffix, minimum in REQUIRED_MIN_SAMPLES.items():
        counts = [len(samples) for label, samples in sample_sets if label.endswith(suffix)]
        if not counts:
            failures.append(f"missing required frame evidence: {suffix}")
            continue
        observed = max(counts)
        if observed < minimum:
            failures.append(
                f"insufficient frame evidence: {suffix} samples={observed} minimum={minimum}"
            )
    return failures


def collect_files(paths: list[str]) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for raw in paths:
        path = pathlib.Path(raw)
        if path.is_dir():
            files.extend(sorted(path.rglob("*-benchmarkData.json")))
        elif path.is_file():
            files.append(path)
    return list(dict.fromkeys(files))


def benchmark_suffix(label: str) -> str | None:
    for suffix in REQUIRED_MIN_SAMPLES:
        if label.endswith(suffix):
            return suffix
    return None


def load_hosted_baseline(path: str | None) -> dict[str, dict[str, float]]:
    if not path:
        raise ValueError("hosted-regression mode requires --baseline")
    baseline_path = pathlib.Path(path)
    try:
        payload = json.loads(baseline_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot parse hosted baseline {baseline_path}: {exc}") from exc
    if not isinstance(payload, dict) or payload.get("schemaVersion") != HOSTED_BASELINE_SCHEMA_VERSION:
        raise ValueError(
            f"hosted baseline schemaVersion must be {HOSTED_BASELINE_SCHEMA_VERSION}"
        )
    if payload.get("kind") != "reader-hosted-emulator-regression-baseline":
        raise ValueError("hosted baseline kind is invalid")
    raw_benchmarks = payload.get("benchmarks")
    if not isinstance(raw_benchmarks, dict):
        raise ValueError("hosted baseline benchmarks object is missing")
    result: dict[str, dict[str, float]] = {}
    for suffix in REQUIRED_MIN_SAMPLES:
        record = raw_benchmarks.get(suffix)
        if not isinstance(record, dict):
            raise ValueError(f"hosted baseline missing benchmark: {suffix}")
        try:
            p95 = float(record["p95Ms"])
            p99 = float(record["p99Ms"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError(f"hosted baseline has invalid percentiles for {suffix}") from exc
        if not math.isfinite(p95) or not math.isfinite(p99) or p95 <= 0 or p99 <= 0:
            raise ValueError(f"hosted baseline percentiles must be finite and positive for {suffix}")
        if p99 < p95:
            raise ValueError(f"hosted baseline P99 must be >= P95 for {suffix}")
        result[suffix] = {"p95Ms": p95, "p99Ms": p99}
    return result


def resolve_limits(mode: str, p95_ms: float | None, p99_ms: float | None) -> tuple[float, float]:
    if mode == "release":
        p95 = p95_ms if p95_ms is not None else float(os.environ.get("JINGDU_FRAME_P95_MS", str(RELEASE_P95_MS)))
        p99 = p99_ms if p99_ms is not None else float(os.environ.get("JINGDU_FRAME_P99_MS", str(RELEASE_P99_MS)))
    else:
        p95 = p95_ms if p95_ms is not None else float(os.environ.get("JINGDU_HOSTED_FRAME_P95_MS", str(HOSTED_P95_MS)))
        p99 = p99_ms if p99_ms is not None else float(os.environ.get("JINGDU_HOSTED_FRAME_P99_MS", str(HOSTED_P99_MS)))
    if not math.isfinite(p95) or not math.isfinite(p99) or p95 <= 0 or p99 <= 0 or p99 < p95:
        raise ValueError(f"invalid frame limits: P95={p95} P99={p99}")
    return p95, p99


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", help="benchmarkData.json files or directories")
    parser.add_argument(
        "--mode",
        choices=("release", "hosted-regression"),
        default=os.environ.get("JINGDU_PERFORMANCE_GATE_MODE", "release"),
    )
    parser.add_argument("--baseline", help="checked-in hosted regression baseline JSON")
    parser.add_argument("--max-regression-ratio", type=float, default=HOSTED_MAX_REGRESSION_RATIO)
    parser.add_argument("--p95-ms", type=float)
    parser.add_argument("--p99-ms", type=float)
    args = parser.parse_args()

    try:
        p95_limit, p99_limit = resolve_limits(args.mode, args.p95_ms, args.p99_ms)
        hosted_baseline = load_hosted_baseline(args.baseline) if args.mode == "hosted-regression" else None
    except ValueError as exc:
        print(f"performance gate: {exc}", file=sys.stderr)
        return 2
    if not math.isfinite(args.max_regression_ratio) or args.max_regression_ratio < 0:
        print("performance gate: max regression ratio must be finite and non-negative", file=sys.stderr)
        return 2

    files = collect_files(args.paths)
    if not files:
        print("performance gate: no benchmarkData.json found", file=sys.stderr)
        return 2

    rows: list[tuple[str, str, int, float, int]] = []
    all_sample_sets: list[tuple[str, list[float]]] = []
    for file in files:
        try:
            payload = json.loads(file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"performance gate: cannot parse {file}: {exc}", file=sys.stderr)
            return 2
        sample_sets = frame_sample_sets(payload)
        all_sample_sets.extend(sample_sets)
        for benchmark, samples in sample_sets:
            for percentile in (95, 99):
                rows.append(
                    (
                        str(file),
                        benchmark,
                        percentile,
                        androidx_percentile(samples, percentile),
                        len(samples),
                    )
                )

    if not rows:
        print("performance gate: no sampledMetrics.frameDurationCpuMs.runs evidence found", file=sys.stderr)
        return 2

    evidence_failures = required_sample_failures(all_sample_sets)
    if evidence_failures:
        for failure in evidence_failures:
            print(f"performance gate: {failure}", file=sys.stderr)
        return 2

    absolute_limits = {95: p95_limit, 99: p99_limit}
    failed = False
    seen = {95: 0, 99: 0}
    for file, benchmark, percentile, value, sample_count in rows:
        seen[percentile] += 1
        absolute_limit = absolute_limits[percentile]
        effective_limit = absolute_limit
        baseline_value: float | None = None
        relative_limit: float | None = None
        if hosted_baseline is not None:
            suffix = benchmark_suffix(benchmark)
            if suffix is None:
                print(
                    f"performance gate: unexpected hosted frame benchmark without baseline: {benchmark}",
                    file=sys.stderr,
                )
                return 2
            key = "p95Ms" if percentile == 95 else "p99Ms"
            baseline_value = hosted_baseline[suffix][key]
            relative_limit = baseline_value * (1.0 + args.max_regression_ratio)
            effective_limit = min(absolute_limit, relative_limit)

        status = "PASS" if value <= effective_limit else "FAIL"
        if hosted_baseline is None:
            detail = f"limit={effective_limit:.3f}ms"
        else:
            detail = (
                f"effective={effective_limit:.3f}ms absolute={absolute_limit:.3f}ms "
                f"baseline={baseline_value:.3f}ms relative={relative_limit:.3f}ms"
            )
        print(
            f"{benchmark} frameDurationCpuMs P{percentile}: {value:.3f}ms "
            f"{detail} samples={sample_count} {status} ({file})"
        )
        failed |= value > effective_limit

    if not all(seen[p] for p in (95, 99)):
        print(f"performance gate: incomplete percentile evidence: {seen}", file=sys.stderr)
        return 2
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
