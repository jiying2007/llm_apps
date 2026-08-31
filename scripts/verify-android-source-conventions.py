#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/android/app/src/main/java"
ACTIVE = [ROOT / ".github", ROOT / "apps/android", ROOT / "scripts"]
FORBIDDEN = ("ReaderScreen" + "V3", "Reader" + "V3", "reader_" + "v3", "reader-" + "v3", "reader" + "V3", "smart_clean" + "3", "strings_reader_" + "v3", "strings_smart_clean" + "3")
REQUIRED = ("BookRepository.kt", "ChineseTextConverter.kt", "NativeCore.kt", "NativeIndexCache.kt", "ReaderController.kt", "ReaderTextPresentation.kt", "SmartCleanRefiner.kt", "TtsController.kt", "ReaderScreen.kt", "ReaderInsightsPanels.kt")
errors = []
java = sorted(MAIN.rglob("*.java"))
if java:
    errors.append("Android main source must be Kotlin-only: " + ", ".join(str(x.relative_to(ROOT)) for x in java))
for name in REQUIRED:
    if not (MAIN / "com/junchen/jingdu" / name).is_file():
        errors.append(f"missing canonical Kotlin source: {name}")
for root in ACTIVE:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for token in FORBIDDEN:
            if token in path.name or token in text:
                errors.append(f"forbidden generation residue {token!r}: {path.relative_to(ROOT)}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("android source conventions: OK")
