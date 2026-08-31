#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def edit(rel: str, pairs: list[tuple[str, str]]) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

edit("apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt", [
    ("it.text()", "it.text"),
    ("candidate.text()", "candidate.text"),
    ("candidate.reason()", "candidate.reason"),
    ("candidate.score()", "candidate.score"),
    ("candidate.count()", "candidate.count"),
])
edit("apps/android/app/src/main/java/com/junchen/jingdu/CompetitiveSheets.kt", [
    ("candidate.text()", "candidate.text"),
    ("candidate.reason()", "candidate.reason"),
    ("candidate.score()", "candidate.score"),
    ("candidate.count()", "candidate.count"),
    ("item.raw.text()", "item.raw.text"),
    ("item.raw.reason()", "item.raw.reason"),
    ("item.raw.score()", "item.raw.score"),
    ("item.raw.count()", "item.raw.count"),
])
edit("apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt", [("candidate.score()", "candidate.score")])
edit("apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt", [
    ("fun save(value: ReaderSettings) { val safe = sanitize(value); ChineseDisplayConverter.configure(safe); pending.tryEmit(safe) }", "fun save(value: ReaderSettings) { pending.tryEmit(sanitize(value)) }")
])
edit("apps/android/app/src/main/java/com/junchen/jingdu/ChineseTextConverter.kt", [
    ("return values.filter { seen.add(it.source.lowercase(Locale.ROOT)) }", "return values.filter { seen.add(it.source.lowercase(Locale.ROOT)) }.toList()")
])
edit("apps/android/app/src/main/java/com/junchen/jingdu/TtsController.kt", [
    ("HANS_MARKERS.indexOf(cp)", "HANS_MARKERS.indexOf(cp.toChar())"),
    ("HANT_MARKERS.indexOf(cp)", "HANT_MARKERS.indexOf(cp.toChar())"),
    ("HK_MARKERS.indexOf(cp)", "HK_MARKERS.indexOf(cp.toChar())"),
])
