#!/usr/bin/env python3
"""Fail CI when Reader V3 Macrobenchmark frame-tail SLOs regress.

AndroidX has emitted frame percentiles in more than one JSON shape across benchmark releases.
This parser intentionally accepts both flattened metric names and nested percentile objects, while
refusing to pass when P95/P99 frameDurationCpuMs evidence is absent.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import re
import sys
from typing import Any, Iterable

PERCENTILE_RE = re.compile(r"(?:^|[_ .-])P?(95|99)(?:$|[_ .-])", re.IGNORECASE)
FRAME_RE = re.compile(r"frameDurationCpuMs", re.IGNORECASE)


def numbers(value: Any) -> Iterable[float]:
    if isinstance(value, bool):
        return
    if isinstance(value, (int, float)) and math.isfinite(float(value)):
        yield float(value)
    elif isinstance(value, list):
        for item in value:
            yield from numbers(item)


def find_percentiles(node: Any, path: tuple[str, ...] = ()) -> list[tuple[str, int, float]]:
    found: list[tuple[str, int, float]] = []
    if isinstance(node, dict):
        for key, value in node.items():
            key_text = str(key)
            next_path = path + (key_text,)
            joined = ".".join(next_path)
            if FRAME_RE.search(joined):
                match = PERCENTILE_RE.search(key_text) or PERCENTILE_RE.search(joined)
                if match:
                    values = list(numbers(value))
                    if values:
                        # Percentile nodes should be scalar; max makes malformed duplicate data fail-safe.
                        found.append((joined, int(match.group(1)), max(values)))
            found.extend(find_percentiles(value, next_path))
    elif isinstance(node, list):
        for index, item in enumerate(node):
            found.extend(find_percentiles(item, path + (str(index),)))
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", help="benchmarkData.json files or directories")
    parser.add_argument("--p95-ms", type=float, default=float(os.environ.get("JINGDU_FRAME_P95_MS", "40")))
    parser.add_argument("--p99-ms", type=float, default=float(os.environ.get("JINGDU_FRAME_P99_MS", "80")))
    args = parser.parse_args()

    files: list[pathlib.Path] = []
    for raw in args.paths:
        path = pathlib.Path(raw)
        if path.is_dir():
            files.extend(sorted(path.rglob("*-benchmarkData.json")))
        elif path.is_file():
            files.append(path)
    files = list(dict.fromkeys(files))
    if not files:
        print("performance SLO: no benchmarkData.json found", file=sys.stderr)
        return 2

    worst = {95: -math.inf, 99: -math.inf}
    evidence: dict[int, list[tuple[str, str, float]]] = {95: [], 99: []}
    for file in files:
        try:
            payload = json.loads(file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"performance SLO: cannot parse {file}: {exc}", file=sys.stderr)
            return 2
        for metric_path, percentile, value in find_percentiles(payload):
            if percentile in worst:
                worst[percentile] = max(worst[percentile], value)
                evidence[percentile].append((str(file), metric_path, value))

    missing = [p for p in (95, 99) if not evidence[p]]
    if missing:
        print(f"performance SLO: missing frameDurationCpuMs percentile evidence: {missing}", file=sys.stderr)
        return 2

    limits = {95: args.p95_ms, 99: args.p99_ms}
    failed = False
    for percentile in (95, 99):
        value = worst[percentile]
        limit = limits[percentile]
        status = "PASS" if value <= limit else "FAIL"
        print(f"frameDurationCpuMs P{percentile}: worst={value:.3f}ms limit={limit:.3f}ms {status}")
        failed |= value > limit
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
