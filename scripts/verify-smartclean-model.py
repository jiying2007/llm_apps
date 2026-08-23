#!/usr/bin/env python3
"""Evaluate the shipped Smart Clean tiny candidate model against held-out hard negatives."""
from __future__ import annotations

import re
from pathlib import Path
from train_smartclean_model_compat import hash_bigram, source_weights

AD_THRESHOLD = 20
BODY_THRESHOLD = -12
STRONG_MARKERS = (
    "http://", "https://", "www.", ".com", ".net", ".cn", ".tw", ".hk",
    "最新网址", "备用网址", "请收藏本站", "请记住本站", "手机用户请访问",
    "关注公众号", "微信公众号", "本书来自", "更多精彩", "搜索书名", "请牢记域名",
    "最新網址", "備用網址", "請收藏本站", "請記住本站", "手機用戶請訪問",
    "關注公眾號", "本書來自", "更多精彩", "搜尋書名", "請牢記網域",
)
SPECIAL_HEADINGS = {"序章", "楔子", "前言", "序言", "后记", "後記", "尾声", "尾聲", "大结局", "大結局", "终章", "終章"}


def load_rows(path: Path) -> list[tuple[str, str]]:
    output: list[tuple[str, str]] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.startswith("#"):
            continue
        try:
            label, text = raw.split("\t", 1)
        except ValueError as error:
            raise SystemExit(f"{path}:{number}: expected LABEL<TAB>text") from error
        if label not in {"AD", "BODY"} or not text.strip():
            raise SystemExit(f"{path}:{number}: invalid row")
        output.append((label, text.strip()))
    return output


def looks_like_heading(value: str) -> bool:
    if value.lower().startswith("chapter"):
        return True
    if value.startswith("第") and any(marker in value[:24] for marker in ("章", "回", "节", "節", "卷")):
        return True
    return value in SPECIAL_HEADINGS


def classify(text: str, weights: list[int]) -> tuple[str, int]:
    value = text.strip()[:512]
    if len(value) < 4:
        return "UNCERTAIN", 0
    score = -4
    lower = value.lower()
    for marker in STRONG_MARKERS:
        if marker.lower() in lower:
            score += 12
    seen: set[int] = set()
    chars = "".join(character for character in value if not character.isspace())
    for index in range(max(0, len(chars) - 1)):
        slot = hash_bigram(chars[index], chars[index + 1])
        if slot not in seen:
            seen.add(slot)
            score += weights[slot]
    if len(value) > 180:
        score -= 4
    if "。" in value and not any(marker.lower() in lower for marker in STRONG_MARKERS):
        score -= 4
    if looks_like_heading(value):
        score -= 20
    if score >= AD_THRESHOLD:
        return "AD", score
    if score <= BODY_THRESHOLD:
        return "BODY", score
    return "UNCERTAIN", score


def main() -> int:
    source = Path("apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt")
    weights = source_weights(source)
    if len(weights) != 64 or any(weight < -8 or weight > 8 for weight in weights):
        raise SystemExit("runtime weights must be 64 bounded signed-int8-style values")

    rows = load_rows(Path("quality/smartclean/eval-v1.tsv"))
    true_ad = sum(label == "AD" for label, _ in rows)
    predicted_ad = [(label, text, classify(text, weights)) for label, text in rows if classify(text, weights)[0] == "AD"]
    true_positive = sum(label == "AD" for label, _, _ in predicted_ad)
    false_positive = [item for item in predicted_ad if item[0] != "AD"]
    precision = 1.0 if not predicted_ad else true_positive / len(predicted_ad)
    recall = 0.0 if true_ad == 0 else true_positive / true_ad

    if false_positive:
        for label, text, decision in false_positive:
            print(f"FALSE POSITIVE {decision}: {text}")
        raise SystemExit("Smart Clean auto-AD hard-negative false positive detected")
    if precision < 0.995:
        raise SystemExit(f"Smart Clean auto-AD precision too low: {precision:.4f}")
    if recall < 0.20:
        raise SystemExit(f"Smart Clean model became trivial/inert: AD recall {recall:.4f} < 0.20")

    headings = [text for label, text in rows if label == "BODY" and looks_like_heading(text)]
    unsafe_headings = [(text, classify(text, weights)) for text in headings if classify(text, weights)[0] == "AD"]
    if unsafe_headings:
        raise SystemExit(f"chapter heading classified as AD: {unsafe_headings}")

    runtime = source.read_text(encoding="utf-8")
    if "classifyCandidate(text: String)" not in runtime or "take(512)" not in runtime:
        raise SystemExit("candidate-only bounded semantic contract missing")
    if re.search(r"File\(|ReaderController|normalizedFile|documentFile", runtime):
        raise SystemExit("semantic classifier must never open or receive a whole document")

    print(f"Smart Clean model quality OK: precision={precision:.3f} recall={recall:.3f} false_positive=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
