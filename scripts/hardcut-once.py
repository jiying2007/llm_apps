#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRANCH = "release/source-v2.3.1"

def p(rel: str) -> Path:
    return ROOT / rel

def read(rel: str) -> str:
    return p(rel).read_text(encoding="utf-8")

def write(rel: str, text: str) -> None:
    path = p(rel)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

def require_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing expected source for {label}: {old[:100]!r}")
    return text.replace(old, new)

def move(old: str, new: str, transform=lambda s: s) -> None:
    src, dst = p(old), p(new)
    if not src.is_file():
        raise SystemExit(f"missing move source: {old}")
    if dst.exists():
        raise SystemExit(f"move target already exists: {new}")
    text = transform(src.read_text(encoding="utf-8"))
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text, encoding="utf-8")
    src.unlink()

def delete(rel: str) -> None:
    path = p(rel)
    if path.exists():
        path.unlink()

move("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt", "apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt", lambda s: s.replace("V3", "").replace("reader-v3", "reader"))
move("apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt", "apps/android/app/src/main/java/com/junchen/jingdu/ReaderInsightsPanels.kt", lambda s: s.replace("V3", ""))
move("apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt", "apps/android/app/src/test/java/com/junchen/jingdu/ReaderFoundationsTest.kt", lambda s: s.replace("V3", ""))

for locale in ("values", "values-b+zh+Hans", "values-b+zh+Hant"):
    move(f"apps/android/app/src/main/res/{locale}/strings_reader_v3.xml", f"apps/android/app/src/main/res/{locale}/strings_reader.xml", lambda s: s.replace("reader_v3_", "reader_").replace("Reader V3", "Reader"))
    move(f"apps/android/app/src/main/res/{locale}/strings_smart_clean3.xml", f"apps/android/app/src/main/res/{locale}/strings_smart_clean.xml", lambda s: s.replace("smart_clean3_", "smart_clean_").replace("Smart Clean 3", "Smart Clean"))

move("scripts/verify-reader-v3.sh", "scripts/verify-reader.sh", lambda s: s.replace("Reader V3", "Reader").replace("reader-v3", "reader").replace("READER_V3", "READER").replace("ReaderV3", "Reader").replace("reader_v3", "reader"))
move("scripts/reader-v3-hosted-emulator-baseline.json", "scripts/reader-hosted-emulator-baseline.json", lambda s: s.replace("Reader V3", "Reader").replace("reader-v3", "reader").replace("reader_v3", "reader"))

delete("docs/READER_V2_PRELAUNCH.md")
delete("docs/READER_V3_PRELAUNCH_FINAL.md")
if p("docs/READER_V3_PROFILE_PROVENANCE.md").is_file():
    move("docs/READER_V3_PROFILE_PROVENANCE.md", "docs/READER_PROFILE_PROVENANCE.md", lambda s: s.replace("Reader V3", "Reader").replace("READER_V3", "READER").replace("reader-v3", "reader").replace("reader_v3", "reader"))

for rel in [
    "apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/ChineseScript.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/NativeCore.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/NativeIndexCache.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java",
    "apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java",
]:
    delete(rel)

for name in ["BookRepository.kt", "ChineseTextConverter.kt", "NativeCore.kt", "NativeIndexCache.kt", "ReaderController.kt", "ReaderTextPresentation.kt", "SmartCleanRefiner.kt", "TtsController.kt"]:
    rel = f"apps/android/app/src/main/java/com/junchen/jingdu/{name}"
    if not p(rel).is_file():
        raise SystemExit(f"missing Kotlin hard-cut implementation: {rel}")

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt"
text = read(rel)
text = require_replace(text, "        val display = ChineseDisplayConverter.convert(intermediate, settings.chineseMode, settings.chineseOverrides)\n        val intermediateToDisplay = TextProjection.between(intermediate, display)\n", "        val presented = ReaderTextPresentation.present(intermediate, settings.chineseMode, settings.chineseOverrides)\n        val display = presented.displayText\n        val intermediateToDisplay = presented.projection\n", rel)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt"
text = read(rel).replace("ChineseDisplayConverter.convert(hit.context, state.settings.chineseMode, state.settings.chineseOverrides)", "ReaderTextPresentation.display(hit.context, state.settings)")
text, count = re.subn(r"\n@Composable internal fun ChaptersSheet\(.*?\n@Composable internal fun BookmarksSheet", "\n@Composable internal fun BookmarksSheet", text, flags=re.S)
if count != 1:
    raise SystemExit(f"expected exactly one dead ChaptersSheet, got {count}")
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt"
text = read(rel).replace("ReaderAnnotationsV3Panel", "ReaderAnnotationsPanel").replace("ReaderReadingMapV3Panel", "ReaderReadingMapPanel")
old = "        val chaptersPanelState = remember(state.currentBook, state.length, state.chapters, state.chaptersLoaded) {\n            AppUiState(\n                currentBook = state.currentBook,\n                length = state.length,\n                chapters = state.chapters,\n                chaptersLoaded = state.chaptersLoaded,\n            )\n        }\n"
new = "        val chaptersPanelState = remember(\n            state.currentBook,\n            state.length,\n            state.chapters,\n            state.chaptersLoaded,\n            state.settings.chineseMode,\n            state.settings.chineseOverrides,\n        ) {\n            AppUiState(\n                currentBook = state.currentBook,\n                length = state.length,\n                chapters = state.chapters,\n                chaptersLoaded = state.chaptersLoaded,\n                settings = state.settings,\n            )\n        }\n"
text = require_replace(text, old, new, rel)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt"
text = read(rel)
old = "    val current = report\n    val chapters = current?.chapters.orEmpty()\n    val end = minOf(chapters.size, windowStart + CHAPTER_WINDOW_ROWS)\n"
new = "    val current = report\n    val chapters = current?.chapters.orEmpty()\n    val displayTitles = remember(chapters, state.settings.chineseMode, state.settings.chineseOverrides) {\n        chapters.map { chapter -> ReaderTextPresentation.chapterTitle(chapter.title, state.settings) }\n    }\n    val end = minOf(chapters.size, windowStart + CHAPTER_WINDOW_ROWS)\n"
text = require_replace(text, old, new, rel)
text = text.replace("CustomAccessibilityAction(chapter.title)", "CustomAccessibilityAction(displayTitles[index])")
text = text.replace('CustomAccessibilityAction("$hideLabel: ${chapter.title}")', 'CustomAccessibilityAction("$hideLabel: ${displayTitles[index]}")')
text = text.replace("drawReaderText(chapter.title, paints.normal", "drawReaderText(displayTitles[index], paints.normal")
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderInsightsPanels.kt"
text = read(rel).replace("Text(chapter.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)", "Text(ReaderTextPresentation.chapterTitle(chapter.title, state.settings), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)")
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt"
text = read(rel)
text = require_replace(text, "    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title\n", "    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title?.let { ReaderTextPresentation.chapterTitle(it, settings) }\n", rel)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt"
write(rel, read(rel).replace("ReaderScreenV3(", "ReaderScreen("))

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderSkimController.kt"
text = read(rel)
text = require_replace(text, "            chapter = chapter?.title,\n", "            chapter = chapter?.title?.let { ReaderTextPresentation.chapterTitle(it, settings) },\n", rel)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt"
text = read(rel)
for old, new in (("chapter.offset()", "chapter.offset"), ("chapter.title()", "chapter.title"), ("hit.offset()", "hit.offset")):
    text = text.replace(old, new)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt"
text = read(rel).replace("TtsVoiceModel(it.name(), it.label())", "TtsVoiceModel(it.name, it.label)").replace("SearchResultModel(it.offset(), it.context())", "SearchResultModel(it.offset, it.context)")
for prop in ("text", "reason", "score", "count"):
    text = re.sub(rf"\bcandidate\.{prop}\(\)", f"candidate.{prop}", text)
old = "            .putExtra(TtsPlaybackService.EXTRA_RATE, uiState.settings.ttsRate)\n            .putExtra(TtsPlaybackService.EXTRA_PITCH, uiState.settings.ttsPitch)\n            .putExtra(TtsPlaybackService.EXTRA_VOICE, uiState.settings.ttsVoiceName)\n"
new = "            .putExtra(TtsPlaybackService.EXTRA_RATE, uiState.settings.ttsRate)\n            .putExtra(TtsPlaybackService.EXTRA_PITCH, uiState.settings.ttsPitch)\n            .putExtra(TtsPlaybackService.EXTRA_VOICE, uiState.settings.ttsVoiceName)\n            .putExtra(TtsPlaybackService.EXTRA_CHINESE_MODE, uiState.settings.chineseMode.name)\n            .putExtra(TtsPlaybackService.EXTRA_CHINESE_OVERRIDES, uiState.settings.chineseOverrides)\n"
text = require_replace(text, old, new, rel)
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt"
text = read(rel)
text = require_replace(text, "    private var title = \"Jingdu\"\n    private var bookId = \"\"\n    private var startRetries = 0\n", "    private var title = \"Jingdu\"\n    private var bookId = \"\"\n    private var chineseMode = ChineseDisplayMode.ORIGINAL\n    private var chineseOverrides = \"\"\n    private var startRetries = 0\n", rel)
text = require_replace(text, "        pitch: Float,\n        voiceName: String,\n    ) {\n", "        pitch: Float,\n        voiceName: String,\n        chineseMode: ChineseDisplayMode,\n        chineseOverrides: String,\n    ) {\n", rel)
text = require_replace(text, "        engine.setVoiceName(voiceName)\n        active = true\n", "        engine.setVoiceName(voiceName)\n        this.chineseMode = chineseMode\n        this.chineseOverrides = chineseOverrides\n        active = true\n", rel)
text = text.replace("reader.speech(offset).nextOffset()", "reader.speech(offset, chineseMode, chineseOverrides).nextOffset")
text = text.replace("engine.start(reader, offset, object : TtsController.Listener {", "engine.start(reader, offset, chineseMode, chineseOverrides, object : TtsController.Listener {")
write(rel, text)

rel = "apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt"
text = read(rel)
text = require_replace(text, "                pitch = intent.getFloatExtra(EXTRA_PITCH, 1f),\n                voiceName = intent.getStringExtra(EXTRA_VOICE).orEmpty(),\n            )\n", "                pitch = intent.getFloatExtra(EXTRA_PITCH, 1f),\n                voiceName = intent.getStringExtra(EXTRA_VOICE).orEmpty(),\n                chineseMode = runCatching {\n                    ChineseDisplayMode.valueOf(intent.getStringExtra(EXTRA_CHINESE_MODE).orEmpty())\n                }.getOrDefault(ChineseDisplayMode.ORIGINAL),\n                chineseOverrides = intent.getStringExtra(EXTRA_CHINESE_OVERRIDES).orEmpty(),\n            )\n", rel)
text = require_replace(text, "        const val EXTRA_VOICE = \"voice\"\n        const val EXTRA_MINUTES = \"minutes\"\n", "        const val EXTRA_VOICE = \"voice\"\n        const val EXTRA_CHINESE_MODE = \"chineseMode\"\n        const val EXTRA_CHINESE_OVERRIDES = \"chineseOverrides\"\n        const val EXTRA_MINUTES = \"minutes\"\n", rel)
write(rel, text)

for root in [p(".github"), p("apps/android"), p("scripts")]:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".kt", ".xml", ".gradle", ".kts", ".sh", ".py", ".json", ".txt", ".md", ".proto", ".yml", ".yaml"}:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text.replace("reader_v3_", "reader_").replace("smart_clean3_", "smart_clean_").replace("ReaderScreenV3", "ReaderScreen").replace("ReaderAnnotationsV3Panel", "ReaderAnnotationsPanel").replace("ReaderReadingMapV3Panel", "ReaderReadingMapPanel").replace("verify-reader-v3.sh", "verify-reader.sh").replace("reader-v3-hosted-emulator-baseline.json", "reader-hosted-emulator-baseline.json").replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md").replace("Reader V3", "Reader").replace("reader-v3", "reader").replace("READER_V3", "READER").replace("ReaderV3", "Reader")
        if updated != text:
            path.write_text(updated, encoding="utf-8")

for path in [p("README.md"), *p("docs").glob("*.md")]:
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8")
    updated = text.replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md").replace("READER_V3_PRELAUNCH_FINAL.md", "PRODUCTION_READINESS.md").replace("READER_V2_PRELAUNCH.md", "PRODUCTION_READINESS.md").replace("Reader V3", "Reader").replace("Reader V2", "Reader").replace("reader-v3", "reader").replace("reader-v2", "reader").replace("READER_V3", "READER").replace("READER_V2", "READER").replace("ReaderV3", "Reader").replace("ReaderV2", "Reader")
    if updated != text:
        path.write_text(updated, encoding="utf-8")

write("scripts/verify-android-source-conventions.py", """#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/android/app/src/main/java"
ACTIVE = [ROOT / ".github", ROOT / "apps/android", ROOT / "scripts"]
FORBIDDEN = ("ReaderScreenV3", "ReaderV3", "reader_v3", "reader-v3", "smart_clean3", "strings_reader_v3", "strings_smart_clean3")
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
    print("\\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("android source conventions: OK")
""")

rel = "scripts/verify-terminal.sh"
text = read(rel)
if "verify-android-source-conventions.py" not in text:
    text += "\npython3 scripts/verify-android-source-conventions.py\n"
write(rel, text)

write("apps/android/app/src/test/java/com/junchen/jingdu/ReaderPresentationTest.kt", """package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPresentationTest {
    @Test fun chapterTitleUsesSameChinesePresentationAsReaderText() {
        val settings = ReaderSettings(chineseMode = ChineseDisplayMode.TRADITIONAL)
        val source = "这本书的第一章"
        assertEquals(ReaderTextPresentation.display(source, settings), ReaderTextPresentation.chapterTitle(source, settings))
        assertTrue(ReaderTextPresentation.chapterTitle(source, settings).contains("這"))
    }

    @Test fun lengthChangingOverrideMapsSpokenRangeBackToSource() {
        val source = "重庆欢迎你"
        val presented = ReaderTextPresentation.present(source, ChineseDisplayMode.TRADITIONAL, "重庆=>重慶市")
        val mapped = ReaderTextPresentation.sourceRangeForDisplayUtf16(presented.displayText, presented.projection, 0, presented.displayText.length)
        assertEquals(0L, mapped.first)
        assertTrue(mapped.last < source.codePointCount(0, source.length))
    }
}
""")

delete(".github/workflows/hardcut-once.yml")
delete("scripts/hardcut-once.py")

if list(p("apps/android/app/src/main/java").rglob("*.java")):
    raise SystemExit("Java product sources remain after hard cut")
for rel in ("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt", "apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt", "apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt", "scripts/verify-reader-v3.sh", "scripts/reader-v3-hosted-emulator-baseline.json"):
    if p(rel).exists():
        raise SystemExit(f"stale path remains: {rel}")

subprocess.run(["python3", "scripts/verify-android-source-conventions.py"], cwd=ROOT, check=True)
subprocess.run(["bash", "scripts/verify-terminal.sh"], cwd=ROOT, check=True)
subprocess.run(["./gradlew", ":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug", ":app:assembleRelease", ":app:assembleAndroidTest", "--stacktrace"], cwd=ROOT / "apps/android", check=True)

subprocess.run(["git", "config", "user.name", "github-actions[bot]"], cwd=ROOT, check=True)
subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], cwd=ROOT, check=True)
subprocess.run(["git", "add", "-A"], cwd=ROOT, check=True)
if subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=ROOT).returncode == 0:
    raise SystemExit("hard cut produced no changes")
subprocess.run(["git", "commit", "-m", "refactor: hard-cut Android Reader source conventions"], cwd=ROOT, check=True)
subprocess.run(["git", "push", "origin", f"HEAD:{BRANCH}"], cwd=ROOT, check=True)
