#!/usr/bin/env python3
from pathlib import Path

body_path = Path(__file__).with_name("hardcut-body.py")
source = body_path.read_text(encoding="utf-8")
source = source.replace(
    'FORBIDDEN = ("ReaderScreenV3", "ReaderV3", "reader_v3", "reader-v3", "smart_clean3", "strings_reader_v3", "strings_smart_clean3")',
    'FORBIDDEN = ("ReaderScreen" + "V3", "Reader" + "V3", "reader_" + "v3", "reader-" + "v3", "reader" + "V3", "smart_clean" + "3", "strings_reader_" + "v3", "strings_smart_clean" + "3")',
)
source = source.replace(
    '.replace("ReaderScreenV3", "ReaderScreen")',
    '.replace("ReaderV3Panels.kt", "ReaderInsightsPanels.kt").replace("ReaderScreenV3", "ReaderScreen")',
)
source = source.replace(
    '.replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md")',
    '.replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md").replace("READER_V3_PRELAUNCH_FINAL.md", "PRODUCTION_READINESS.md").replace("READER_V2_PRELAUNCH.md", "PRODUCTION_READINESS.md")',
)
source = source.replace(
    'delete("scripts/hardcut-once.py")',
    'delete("scripts/hardcut-once.py")\ndelete("scripts/hardcut-body.py")',
)
needle = 'subprocess.run(["python3", "scripts/verify-android-source-conventions.py"], cwd=ROOT, check=True)'
patch = r'''
rel = "scripts/verify-terminal.sh"
text = read(rel)
text = text.replace("  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt \\\n", "")
text = text.replace("BookRepository.java", "BookRepository.kt")
text = text.replace("bash ./scripts/verify-reader-v3.sh", "bash ./scripts/verify-reader.sh")
write(rel, text)

rel = "scripts/verify-reader.sh"
text = read(rel)
for old, new in (
    ("docs/READER_PRELAUNCH_FINAL.md", "docs/PRODUCTION_READINESS.md"),
    ("BookRepository.java", "BookRepository.kt"),
    ("ReaderController.java", "ReaderController.kt"),
    ("ReaderPanels.kt", "ReaderInsightsPanels.kt"),
    ("prewarmChapterIndex(book);", "prewarmChapterIndex(book)"),
    ("prewarmChapterIndex(updated);", "prewarmChapterIndex(updated)"),
    ("source.chapters();", "source.chapters()"),
):
    text = text.replace(old, new)
text = text.replace("V3", "")
text = text.replace("  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt \\\n", "")
write(rel, text)

for root in (p("apps/android"), p("scripts"), p(".github")):
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file() or path == p("scripts/verify-android-source-conventions.py"):
            continue
        if path.suffix.lower() not in {".kt", ".xml", ".gradle", ".kts", ".sh", ".py", ".json", ".txt", ".md", ".proto", ".yml", ".yaml"}:
            continue
        value = path.read_text(encoding="utf-8")
        updated = value.replace("V3", "")
        if updated != value:
            path.write_text(updated, encoding="utf-8")

fix_path = p("scripts/hardcut-fixes.py")
exec(compile(fix_path.read_text(encoding="utf-8"), str(fix_path), "exec"), {"__file__": str(fix_path), "__name__": "__main__"})
delete("scripts/hardcut-fixes.py")
subprocess.run(["python3", "scripts/verify-android-source-conventions.py"], cwd=ROOT, check=True)
'''.strip()
if needle not in source:
    raise SystemExit("missing hard-cut verification insertion point")
source = source.replace(needle, patch)
exec(compile(source, str(body_path), "exec"), {"__file__": __file__, "__name__": "__main__"})
