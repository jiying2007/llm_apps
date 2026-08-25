#!/usr/bin/env python3
from pathlib import Path


def replace(path: str, old: str, new: str, label: str):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing marker {label}: {path}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

reader = 'apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt'
replace(reader,
        'import androidx.compose.ui.platform.LocalHapticFeedback\n',
        'import androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.platform.LocalConfiguration\n',
        'LocalConfiguration import')
replace(reader,
        'import kotlinx.coroutines.Dispatchers\n',
        'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\n',
        'Job import')
replace(reader,
        'import kotlinx.coroutines.isActive\n',
        'import kotlinx.coroutines.isActive\nimport kotlinx.coroutines.launch\n',
        'launch import')
replace(reader, 'import java.util.Locale\n', '', 'remove Locale import')
replace(reader,
        '    val clock = if (state.settings.showClock) SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) else null\n',
        '    val locale = LocalConfiguration.current.locales[0]\n    val clock = if (state.settings.showClock) SimpleDateFormat("HH:mm", locale).format(now) else null\n',
        'observable locale')
replace(reader,
        '            if (trimmed.length in 2..48 && (trimmed.startsWith("第") || trimmed.startsWith("Chapter", true))) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), cursor, end)\n',
        '            if (ReaderHeadingClassifier.isHeading(trimmed)) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), cursor, end)\n',
        'heading classifier')
replace(reader,
        '    val swipe = 52.dp.toPx(); val tapSlop = 14.dp.toPx()\n    awaitEachGesture {\n',
        '    val swipe = 52.dp.toPx(); val tapSlop = 14.dp.toPx()\n    var lastCenterTapAt = 0L\n    var pendingCenterTap: Job? = null\n    awaitEachGesture {\n',
        'double tap state')
replace(reader,
        '                else -> if (settings.doubleTapBookmarkEnabled && duration < 180) onToggleControls() else onToggleControls()\n',
        '''                else -> {
                    if (!settings.doubleTapBookmarkEnabled) {
                        onToggleControls()
                    } else {
                        val tapAt = last.uptimeMillis
                        if (lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {
                            pendingCenterTap?.cancel(); pendingCenterTap = null; lastCenterTapAt = 0L; onBookmark()
                        } else {
                            lastCenterTapAt = tapAt
                            pendingCenterTap?.cancel()
                            pendingCenterTap = launch { delay(280L); onToggleControls() }
                        }
                    }
                }
''',
        'double tap behavior')

old_access = '''private fun Modifier.readerAccessibilityActions(previous: () -> Unit, next: () -> Unit, controls: () -> Unit, bookmark: () -> Unit): Modifier = semantics {
    contentDescription = "Reader surface"
    customActions = listOf(
        CustomAccessibilityAction("Previous page") { previous(); true }, CustomAccessibilityAction("Next page") { next(); true },
        CustomAccessibilityAction("Show or hide reading controls") { controls(); true }, CustomAccessibilityAction("Add bookmark") { bookmark(); true },
    )
}
'''
new_access = '''private fun Modifier.readerAccessibilityActions(
    previous: () -> Unit, next: () -> Unit, controls: () -> Unit, bookmark: () -> Unit,
    surfaceLabel: String, previousLabel: String, nextLabel: String, controlsLabel: String, bookmarkLabel: String,
): Modifier = semantics {
    contentDescription = surfaceLabel
    customActions = listOf(
        CustomAccessibilityAction(previousLabel) { previous(); true }, CustomAccessibilityAction(nextLabel) { next(); true },
        CustomAccessibilityAction(controlsLabel) { controls(); true }, CustomAccessibilityAction(bookmarkLabel) { bookmark(); true },
    )
}
'''
replace(reader, old_access, new_access, 'localized accessibility function')

access_call = '    val semantics = Modifier.readerAccessibilityActions(onPrevious, onNext, onToggleControls, onBookmark)\n'
localized_call = '''    val semantics = Modifier.readerAccessibilityActions(
        onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous),
        stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls),
        stringResource(R.string.reader_access_bookmark),
    )
'''
p = Path(reader); text = p.read_text(encoding='utf-8')
if text.count(access_call) != 2:
    raise SystemExit(f'expected two accessibility calls, found {text.count(access_call)}')
p.write_text(text.replace(access_call, localized_call), encoding='utf-8')

replace(reader,
        '    var value by remember(fraction) { mutableFloatStateOf(fraction) }\n',
        '    var value by remember(fraction) { mutableFloatStateOf(fraction) }\n    val progressDescription = stringResource(R.string.reading_progress)\n',
        'progress label')
replace(reader,
        'modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Reading progress" })',
        'modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription })',
        'localized progress semantics')
replace(reader,
        '            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ReaderHighlightStyle.entries.forEach { style -> TextButton({ onHighlight(style) }) { Text(style.name.lowercase().replaceFirstChar(Char::uppercase)) } } }\n',
        '''            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderHighlightStyle.entries.forEach { style ->
                    val label = stringResource(when (style) {
                        ReaderHighlightStyle.YELLOW -> R.string.reader_highlight_yellow
                        ReaderHighlightStyle.GREEN -> R.string.reader_highlight_green
                        ReaderHighlightStyle.BLUE -> R.string.reader_highlight_blue
                        ReaderHighlightStyle.PINK -> R.string.reader_highlight_pink
                    })
                    TextButton({ onHighlight(style) }) { Text(label) }
                }
            }
''',
        'localized highlight styles')

# Keep heading classification/document-language constants outside the presentation layer.
heading = Path('apps/android/app/src/main/java/com/junchen/jingdu/ReaderHeadingClassifier.kt')
heading.write_text('''package com.junchen.jingdu

/** Document-content heading heuristic; never UI copy. */
internal object ReaderHeadingClassifier {
    private const val CJK_ORDINAL_PREFIX = "\\u7b2c"
    fun isHeading(value: String): Boolean {
        val text = value.trim()
        return text.length in 2..48 && (text.startsWith(CJK_ORDINAL_PREFIX) || text.startsWith("Chapter", ignoreCase = true))
    }
}
''', encoding='utf-8')

# Remove new deprecation warnings introduced/touched by this reader release.
library = Path('apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt')
lib = library.read_text(encoding='utf-8')
if 'Icons.Default.MenuBook' in lib:
    if 'import androidx.compose.material.icons.automirrored.filled.MenuBook\n' not in lib:
        lib = lib.replace('import androidx.compose.material.icons.Icons\n', 'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.automirrored.filled.MenuBook\n', 1)
    lib = lib.replace('Icons.Default.MenuBook', 'Icons.AutoMirrored.Filled.MenuBook')
    library.write_text(lib, encoding='utf-8')

ui_test = Path('apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt')
t = ui_test.read_text(encoding='utf-8')
t = t.replace('import androidx.compose.ui.test.junit4.createComposeRule', 'import androidx.compose.ui.test.junit4.v2.createComposeRule')
ui_test.write_text(t, encoding='utf-8')

# V2 presentation/i18n contract: no deleted ProductSettingsSheet compatibility path.
i18n = Path('scripts/verify-android-i18n.py')
t = i18n.read_text(encoding='utf-8')
t = t.replace('    "ProductSettingsSheet.kt",\n', '    "ReaderV2Panels.kt",\n    "ReaderAdvancedSettingsSheet.kt",\n')
i18n.write_text(t, encoding='utf-8')

play = Path('scripts/verify-play-store.sh')
t = play.read_text(encoding='utf-8')
t = t.replace('apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt', 'apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt')
play.write_text(t, encoding='utf-8')

print('Reader V2 final patch applied')
