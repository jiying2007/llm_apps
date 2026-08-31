#!/usr/bin/env python3
from pathlib import Path

screen = Path('apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt')
text = screen.read_text(encoding='utf-8')
start_marker = '''    Box(
        Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures),
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionContainer(state = selectionState) {
'''
end_marker = '''    }
}

@Composable
private fun ContinuousReaderPage('''
start = text.index(start_marker)
end = text.index(end_marker, start)
old = text[start:end + len('    }\n')]
inner_start = old.index('            Box(\n')
inner_end = old.rfind('        }\n    }\n')
assert inner_start >= 0 and inner_end > inner_start
inner = old[inner_start:inner_end]
# SelectionContainer adds one indentation level around the page Box. Remove exactly four spaces
# from every non-empty line to make that page body a reusable composable lambda.
inner_lines = inner.splitlines()
inner = '\n'.join((line[4:] if line.startswith('    ') else line) for line in inner_lines) + '\n'
new = (
    '    val pageContent: @Composable () -> Unit = {\n'
    + inner
    + '    }\n\n'
    + '    Box(\n'
    + '        Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures),\n'
    + '        contentAlignment = Alignment.TopCenter,\n'
    + '    ) {\n'
    + '        if (fastSelectionMode) {\n'
    + '            SelectionContainer(state = selectionState) { pageContent() }\n'
    + '        } else {\n'
    + '            pageContent()\n'
    + '        }\n'
    + '    }\n'
)
text = text[:start] + new + text[end + len('    }\n'):]
screen.write_text(text, encoding='utf-8')

test = Path('apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt')
text = test.read_text(encoding='utf-8')
old_import = 'import androidx.compose.ui.test.assertIsDisplayed\n'
new_import = 'import androidx.compose.ui.test.assertDoesNotExist\nimport androidx.compose.ui.test.assertIsDisplayed\n'
assert text.count(old_import) == 1 and 'assertDoesNotExist' not in text
text = text.replace(old_import, new_import, 1)
old_import = 'import androidx.compose.ui.test.performClick\n'
new_import = 'import androidx.compose.ui.test.performClick\nimport androidx.compose.ui.test.performTouchInput\n'
assert text.count(old_import) == 1 and 'performTouchInput' not in text
text = text.replace(old_import, new_import, 1)
anchor = '    @Test fun quickReadingSettingsStayTouchableAcrossRepeatedStateChanges() {\n'
addition = '''    @Test fun centerTapRestoresReaderChromeAfterAutoHide() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = "Chapter 1\\nA stable body used for center-tap restoration verification.",
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true, controlsAutoHideMs = 80L),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
        Thread.sleep(240L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface)).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
    }

'''
assert text.count(anchor) == 1 and 'centerTapRestoresReaderChromeAfterAutoHide' not in text
text = text.replace(anchor, addition + anchor, 1)
test.write_text(text, encoding='utf-8')

contract = Path('scripts/verify-reader.sh')
text = contract.read_text(encoding='utf-8')
anchor = "require_literal \"$screen\" 'SelectionContainer(state = selectionState)' 'paged selection container'\n"
addition = "require_literal \"$screen\" 'if (fastSelectionMode) {' 'selection container activation gate'\n"
assert text.count(anchor) == 1 and 'selection container activation gate' not in text
contract.write_text(text.replace(anchor, anchor + addition, 1), encoding='utf-8')
