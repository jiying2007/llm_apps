#!/usr/bin/env python3
"""Train Jingdu's tiny candidate-only Smart Clean linear model from local TSV corpus.

This is intentionally stdlib-only and deterministic. It emits 64 signed integer weights for
hashed character bigrams; product inference remains candidate-only and does not need an ML runtime.
"""
from __future__ import annotations

import argparse
import math
import re
from pathlib import Path

BUCKETS = 64
SCALE = 3.0
MIN_WEIGHT = -8
MAX_WEIGHT = 8


def hash_bigram(first: str, second: str) -> int:
    value = 0x811C9DC5
    for character in (first, second):
        code = ord(character)
        value = ((value ^ (code & 0xFF)) * 0x01000193) & 0xFFFFFFFF
        value = ((value ^ ((code >> 8) & 0xFF)) * 0x01000193) & 0xFFFFFFFF
    return value & (BUCKETS - 1)


def load(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.startswith("#"):
            continue
        try:
            label, text = raw.split("\t", 1)
        except ValueError as error:
            raise SystemExit(f"{path}:{number}: expected LABEL<TAB>text") from error
        if label not in {"AD", "BODY"} or not text.strip():
            raise SystemExit(f"{path}:{number}: invalid row")
        rows.append((label, text.strip()))
    return rows


def train(rows: list[tuple[str, str]]) -> list[int]:
    ad = [0] * BUCKETS
    body = [0] * BUCKETS
    for label, text in rows:
        chars = "".join(character for character in text if not character.isspace())
        buckets = {hash_bigram(chars[index], chars[index + 1]) for index in range(max(0, len(chars) - 1))}
        target = ad if label == "AD" else body
        for slot in buckets:
            target[slot] += 1
    output: list[int] = []
    for ad_count, body_count in zip(ad, body):
        raw = round(SCALE * math.log((ad_count + 1) / (body_count + 1)))
        output.append(max(MIN_WEIGHT, min(MAX_WEIGHT, raw)))
    return output


def source_weights(path: Path) -> list[int]:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"private val weights = intArrayOf\((.*?)\)\n", text, re.S)
    if not match:
        raise SystemExit(f"cannot locate runtime weights in {path}")
    values = [int(value) for value in re.findall(r"-?\d+", match.group(1))]
    if len(values) != BUCKETS:
        raise SystemExit(f"runtime model must contain {BUCKETS} weights, found {len(values)}")
    return values


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", default="quality/smartclean/train-v1.tsv")
    parser.add_argument("--verify-source")
    args = parser.parse_args()
    weights = train(load(Path(args.corpus)))
    if args.verify_source:
        current = source_weights(Path(args.verify_source))
        if current != weights:
            print("generated:", ", ".join(map(str, weights)))
            print("runtime:  ", ", ".join(map(str, current)))
            raise SystemExit("Smart Clean runtime weights drift from reproducible training corpus")
        print(f"Smart Clean tiny-model weights reproducible: {BUCKETS} buckets")
        return 0
    print(", ".join(map(str, weights)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
