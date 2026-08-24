#!/usr/bin/env python3
from pathlib import Path
import subprocess


def patch(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing marker: {label}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


main = "apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt"
settings = "apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt"
screen = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt"
panels = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt"
terminal = "scripts/verify-terminal-quality.sh"

patch(
    main,
    "success = { report -> uiState = uiState.copy(chaptersLoaded = true, chapters = report.chapters) },",
    "success = { report -> uiState = uiState.copy(chaptersLoaded = true, chapters = report.chapters.map { ChapterModel(it.offset, it.title, it.source, it.confidence) }) },",
    "chapter model bridge",
)

patch(
    settings,
    """    ReaderPalette.PAPER -> R.string.paper
    ReaderPalette.LIGHT -> R.string.light
    ReaderPalette.NIGHT -> R.string.night
    ReaderPalette.OLED -> R.string.reader_oled
""",
    """    ReaderPalette.PAPER -> R.string.paper
    ReaderPalette.LIGHT -> R.string.light
    ReaderPalette.SEPIA -> R.string.reader_theme_sepia
    ReaderPalette.NIGHT -> R.string.night
    ReaderPalette.OLED -> R.string.reader_oled
""",
    "sepia settings label",
)

patch(
    screen,
    """    snackbar: SnackbarHostState,
    canLocationBack: Boolean = false,
""",
    """    snackbar: SnackbarHostState,
    adaptiveLayout: ReaderAdaptiveLayout = ReaderAdaptiveLayout(ReaderAdaptiveWidth.COMPACT, hasHinge = false, tabletop = false),
    canLocationBack: Boolean = false,
""",
    "adaptive reader parameter",
)
patch(
    screen,
    """                PagedReaderPage(
                    text = targetText,
                    settings = settings,
""",
    """                PagedReaderPage(
                    text = targetText,
                    settings = settings,
                    adaptiveLayout = adaptiveLayout,
""",
    "adaptive page call",
)
patch(
    screen,
    """private fun PagedReaderPage(
    text: String,
    settings: ReaderSettings,
""",
    """private fun PagedReaderPage(
    text: String,
    settings: ReaderSettings,
    adaptiveLayout: ReaderAdaptiveLayout,
""",
    "adaptive page signature",
)
patch(
    screen,
    """        val useTwoColumns = when (settings.wideColumns) {
            ReaderWideColumns.SINGLE -> false
            ReaderWideColumns.DOUBLE -> maxWidth >= 600.dp
            ReaderWideColumns.AUTO -> maxWidth >= 840.dp
        }
""",
    """        val useTwoColumns = when (settings.wideColumns) {
            ReaderWideColumns.SINGLE -> false
            ReaderWideColumns.DOUBLE -> adaptiveLayout.width >= ReaderAdaptiveWidth.MEDIUM && !adaptiveLayout.tabletop
            ReaderWideColumns.AUTO -> adaptiveLayout.prefersTwoColumns
        }
""",
    "adaptive two-column policy",
)
patch(
    screen,
    """private fun readerBackground(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}
""",
    """private fun readerBackground(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.SEPIA -> Color(0xFFF3E5C8)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}
""",
    "sepia reader background",
)

patch(
    panels,
    "stringResource(R.string.add_bookmark)",
    "stringResource(R.string.reader_access_bookmark)",
    "bookmark accessibility string",
)

patch(
    terminal,
    """grep -Fq '\"bookmarks.${book.id}\"' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'mapOffset(oldPosition, oldLength, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'mapOffset(offset, oldLength, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'putStringSet(bookmarkKey(updated), mappedBookmarks)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
""",
    """grep -q 'ReaderAnnotationStore' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'annotationStore.remapBook(book.id, oldLength, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'dataStore' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
if grep -q 'getSharedPreferences(\"jingdu.reader.settings' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt; then
  echo 'Reader V2 settings must not retain the legacy SharedPreferences path' >&2
  exit 1
fi
""",
    "Reader V2 persistence contract",
)

subprocess.run(
    [
        "git", "add",
        main,
        settings,
        screen,
        panels,
        terminal,
    ],
    check=True,
)
