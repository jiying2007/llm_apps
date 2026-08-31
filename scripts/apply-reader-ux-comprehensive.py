#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
changed = []


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    old = path.read_text(encoding="utf-8")
    if old != text:
        path.write_text(text, encoding="utf-8")
        changed.append(rel)


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one match, got {count}: {old[:120]!r}")
    write(rel, text.replace(old, new, 1))


def replace_n(rel, old, new, count_expected):
    text = read(rel)
    count = text.count(old)
    if count != count_expected:
        raise SystemExit(f"{rel}: expected {count_expected} matches, got {count}: {old[:120]!r}")
    write(rel, text.replace(old, new))


def regex_once(rel, pattern, replacement):
    text = read(rel)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{rel}: regex expected one match, got {count}: {pattern[:120]!r}")
    write(rel, updated)


# --- Reader app shell: keep hot panels resident for fast reopen, but never cache interactive pixels.
app = "apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt"
for line in [
    "import androidx.compose.ui.draw.drawWithContent\n",
    "import androidx.compose.ui.graphics.layer.drawLayer\n",
    "import androidx.compose.ui.graphics.rememberGraphicsLayer\n",
    "import androidx.compose.ui.layout.onSizeChanged\n",
    "import androidx.compose.ui.platform.LocalDensity\n",
    "import androidx.compose.ui.platform.LocalLayoutDirection\n",
    "import androidx.compose.ui.unit.IntSize\n",
]:
    text = read(app)
    if line in text:
        write(app, text.replace(line, "", 1))

regex_once(
    app,
    r"/\*\*\n \* Hot panels stay measured for the Reader session\..*?\nprivate const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384",
    '''/**
 * Hot Quick/Chapters panels stay measured for the Reader session, but all visible content draws
 * live. Visibility is placement-phase only, so hidden panels cannot intercept pointer input and
 * local panel state can never replay stale pixels.
 */
@Composable
private fun PersistentReaderPanelLayer(
    panelState: State<ReaderPanel?>,
    target: ReaderPanel,
    @Suppress("UNUSED_PARAMETER") recordKey: Any?,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val visible = panelState.value == target
                    placeable.placeWithLayer(
                        x = 0,
                        y = if (visible) 0 else READER_PANEL_HIDDEN_OFFSET_PX,
                    ) { alpha = if (visible) 1f else 0f }
                }
            }
            .semantics { if (panelState.value != target) hideFromAccessibility() },
    ) { content() }
}

private const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384''',
)

# --- Reader surface: gesture arbitration, delayed single-tap, bookmarks entry and semantic status.
screen = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt"
replace_once(screen, "import kotlinx.coroutines.Dispatchers\n", "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.coroutineScope\nimport kotlinx.coroutines.launch\n")
replace_once(
    screen,
    "                onOpenQuick = { actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) },\n",
    "                onBookmarks = { actions.onOpenPanel(ReaderPanel.BOOKMARKS) },\n",
)
replace_once(screen, "    onOpenQuick: () -> Unit,\n", "    onBookmarks: () -> Unit,\n")
replace_once(
    screen,
    '                TextButton(onOpenQuick) { Text("Aa") }\n',
    '                IconButton(onBookmarks) { Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmarks)) }\n',
)
replace_once(
    screen,
    '        DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })\n',
    '        DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })\n'
    '        DropdownMenuItem({ Text(stringResource(R.string.bookmarks)) }, { close { actions.onOpenPanel(ReaderPanel.BOOKMARKS) } }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) })\n',
)
replace_once(
    screen,
    '        chapter?.title?.take(18)?.let(::add)\n',
    '        chapter?.title?.let { ReaderTextPresentation.chapterTitle(it, state.settings).take(18) }?.let(::add)\n',
)

# Paged selection owns the fast-render fallback lifecycle. After the real selection is cleared,
# restore the fast renderer instead of leaving the page permanently in the fallback path.
selection_block = '''    val selectionState = rememberSelectionState()
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        onSelection(range?.let { SelectionPayload(it) { selectionState.clear() } })
    }
'''
paged_selection = '''    val selectionState = rememberSelectionState()
    var fastSelectionMode by remember(sourceStart) { mutableStateOf(false) }
    var sawFastSelection by remember(sourceStart) { mutableStateOf(false) }
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        if (range != null) sawFastSelection = true
        else if (sawFastSelection) { fastSelectionMode = false; sawFastSelection = false }
        onSelection(range?.let { SelectionPayload(it) { selectionState.clear() } })
    }
'''
continuous_selection = '''    val selectionState = rememberSelectionState()
    var fastSelectionMode by remember(start) { mutableStateOf(false) }
    var sawFastSelection by remember(start) { mutableStateOf(false) }
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        if (range != null) sawFastSelection = true
        else if (sawFastSelection) { fastSelectionMode = false; sawFastSelection = false }
        onSelection(range?.let { SelectionPayload(it) { selectionState.clear() } })
    }
'''
text = read(screen)
if text.count(selection_block) != 2:
    raise SystemExit(f"{screen}: expected two selection blocks, got {text.count(selection_block)}")
text = text.replace(selection_block, paged_selection, 1)
text = text.replace(selection_block, continuous_selection, 1)
write(screen, text)

replace_once(
    screen,
    '                    Text(annotated.subSequence(0, firstEnd), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip)\n'
    '                    Text(annotated.subSequence(firstEnd, annotated.length), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip)\n',
    '''                    Text(
                        annotated.subSequence(0, firstEnd), Modifier.weight(1f).fillMaxHeight(), style = style,
                        overflow = TextOverflow.Clip, selectionMode = fastSelectionMode,
                        onRequestSelection = { fastSelectionMode = true },
                    )
                    Text(
                        annotated.subSequence(firstEnd, annotated.length), Modifier.weight(1f).fillMaxHeight(), style = style,
                        overflow = TextOverflow.Clip, selectionMode = fastSelectionMode,
                        onRequestSelection = { fastSelectionMode = true },
                    )
''',
)
replace_once(
    screen,
    '''                Text(
                    annotated,
                    Modifier.fillMaxHeight().widthIn(max = 760.dp).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp),
                    style = style,
                    overflow = TextOverflow.Clip,
                )
''',
    '''                Text(
                    annotated,
                    Modifier.fillMaxHeight().widthIn(max = 760.dp).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp),
                    style = style,
                    overflow = TextOverflow.Clip,
                    selectionMode = fastSelectionMode,
                    onRequestSelection = { fastSelectionMode = true },
                )
''',
)
replace_once(
    screen,
    '                onScrollSettled = { settleEvents.tryEmit(Unit) },\n                onTextLayout = { layoutResult = it },\n',
    '                onScrollSettled = { settleEvents.tryEmit(Unit) },\n                selectionMode = fastSelectionMode,\n                onRequestSelection = { fastSelectionMode = true },\n                onTextLayout = { layoutResult = it },\n',
)

regex_once(
    screen,
    r"private fun Modifier\.readerGestures\(.*?\n\}\n\nprivate fun Modifier\.readerAccessibilityActions",
    '''private fun Modifier.readerGestures(
    settings: ReaderSettings,
    widthPx: Int,
    heightPx: Int,
    systemLeftInsetPx: Int,
    systemRightInsetPx: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onBookmark: () -> Unit,
    onAnyTouch: () -> Unit = {},
): Modifier = pointerInput(settings, widthPx, heightPx, systemLeftInsetPx, systemRightInsetPx) {
    coroutineScope {
        val swipe = 52.dp.toPx()
        val tapSlop = 14.dp.toPx()
        var lastCenterTapAt = 0L
        var pendingCenterTap: Job? = null

        fun dispatch(action: ReaderGestureAction) {
            when (action) {
                ReaderGestureAction.CONTROLS -> onToggleControls()
                ReaderGestureAction.BOOKMARK -> onBookmark()
                ReaderGestureAction.NEXT -> onNext()
                ReaderGestureAction.PREVIOUS -> onPrevious()
                ReaderGestureAction.NONE -> Unit
            }
        }
        fun cancelPendingCenterTap() {
            pendingCenterTap?.cancel()
            pendingCenterTap = null
            lastCenterTapAt = 0L
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onAnyTouch()
            var last = down
            var consumedByChild = down.isConsumed
            var maxPointers = 1
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                maxPointers = maxOf(maxPointers, event.changes.size)
                if (event.changes.any { it.isConsumed }) consumedByChild = true
                event.changes.firstOrNull { it.id == down.id }?.let { last = it }
            } while (last.pressed)
            if (maxPointers > 1) return@awaitEachGesture

            val delta = last.position - down.position
            val duration = last.uptimeMillis - down.uptimeMillis
            val edgeGuard = 8.dp.toPx()
            if (!consumedByChild && settings.brightnessGestureEnabled && widthPx > 0 &&
                down.position.x >= systemLeftInsetPx + edgeGuard &&
                down.position.x <= systemLeftInsetPx + widthPx * 0.14f &&
                abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe
            ) {
                cancelPendingCenterTap()
                onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f)
                return@awaitEachGesture
            }

            if (settings.swipePagingEnabled && widthPx > 0 &&
                down.position.x > systemLeftInsetPx + edgeGuard &&
                down.position.x < widthPx - systemRightInsetPx - edgeGuard &&
                ReaderGesturePolicy.allowsPageSwipe(consumedByChild, duration, delta.x, delta.y, swipe)
            ) {
                cancelPendingCenterTap()
                var forward = delta.x < 0
                if (settings.reversePagingGestures) forward = !forward
                if (forward) onNext() else onPrevious()
                return@awaitEachGesture
            }

            if (duration <= 360 && delta.getDistance() <= tapSlop && widthPx > 0) {
                if (down.position.x <= systemLeftInsetPx + edgeGuard ||
                    down.position.x >= widthPx - systemRightInsetPx - edgeGuard
                ) return@awaitEachGesture
                val edge = when (settings.tapZonePreset) {
                    ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> widthPx * settings.tapZoneEdgeFraction
                    ReaderTapZonePreset.RIGHT_HANDED -> widthPx * 0.22f
                    ReaderTapZonePreset.LEFT_HANDED -> widthPx * 0.32f
                }
                when {
                    down.position.x < edge && settings.tapPagingEnabled -> {
                        cancelPendingCenterTap()
                        if (settings.reversePagingGestures) onNext() else onPrevious()
                    }
                    down.position.x > widthPx - edge && settings.tapPagingEnabled -> {
                        cancelPendingCenterTap()
                        if (settings.reversePagingGestures) onPrevious() else onNext()
                    }
                    else -> {
                        val centerAction = if (settings.advancedGestureCustomizationEnabled) settings.centerTapAction else ReaderGestureAction.CONTROLS
                        val doubleAction = if (settings.advancedGestureCustomizationEnabled) settings.doubleTapAction
                        else if (settings.doubleTapBookmarkEnabled) ReaderGestureAction.BOOKMARK else ReaderGestureAction.NONE
                        val tapAt = last.uptimeMillis
                        if (doubleAction == ReaderGestureAction.NONE) {
                            cancelPendingCenterTap()
                            dispatch(centerAction)
                        } else if (ReaderGesturePolicy.isDoubleTap(lastCenterTapAt, tapAt)) {
                            pendingCenterTap?.cancel()
                            pendingCenterTap = null
                            lastCenterTapAt = 0L
                            dispatch(doubleAction)
                        } else {
                            pendingCenterTap?.cancel()
                            lastCenterTapAt = tapAt
                            pendingCenterTap = launch {
                                delay(330L)
                                if (lastCenterTapAt == tapAt) {
                                    lastCenterTapAt = 0L
                                    pendingCenterTap = null
                                    dispatch(centerAction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.readerAccessibilityActions''',
)

# --- Fast text: external selection lifecycle restores native fast rendering after selection clear.
fast = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt"
replace_once(
    fast,
    "internal fun Text(text: AnnotatedString, modifier: Modifier, style: TextStyle, overflow: TextOverflow) {\n",
    "internal fun Text(\n    text: AnnotatedString, modifier: Modifier, style: TextStyle, overflow: TextOverflow,\n    selectionMode: Boolean? = null, onRequestSelection: (() -> Unit)? = null,\n) {\n",
)
replace_once(
    fast,
    '    var selectionMode by remember(text.text) { mutableStateOf(false) }\n    if (selectionMode || accessibility.isTouchExplorationEnabled) {\n',
    '    var internalSelectionMode by remember(text.text) { mutableStateOf(false) }\n    val selecting = selectionMode ?: internalSelectionMode\n    if (selecting || accessibility.isTouchExplorationEnabled) {\n',
)
replace_once(
    fast,
    '    BoxWithConstraints(modifier.fillMaxWidth().armSelectionOnLongPress(text.text) { selectionMode = true }) {\n',
    '    BoxWithConstraints(modifier.fillMaxWidth().armSelectionOnLongPress(text.text) {\n        if (onRequestSelection != null) onRequestSelection() else internalSelectionMode = true\n    }) {\n',
)
replace_once(
    fast,
    '    onScrollSettled: () -> Unit,\n    onTextLayout: (ReaderContinuousLayout) -> Unit,\n) {\n',
    '    onScrollSettled: () -> Unit,\n    selectionMode: Boolean? = null,\n    onRequestSelection: (() -> Unit)? = null,\n    onTextLayout: (ReaderContinuousLayout) -> Unit,\n) {\n',
)
replace_once(
    fast,
    '    var selectionMode by remember(text.text) { mutableStateOf(false) }\n    val fallback = selectionMode || accessibility.isTouchExplorationEnabled\n',
    '    var internalSelectionMode by remember(text.text) { mutableStateOf(false) }\n    val fallback = (selectionMode ?: internalSelectionMode) || accessibility.isTouchExplorationEnabled\n',
)
replace_once(
    fast,
    '                    ) { selectionMode = true }\n',
    '                    ) { if (onRequestSelection != null) onRequestSelection() else internalSelectionMode = true }\n',
)

# --- Settings: one giant LazyColumn item is not a useful virtual list; use natural scrolling content.
settings = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt"
text = read(settings)
if "import androidx.compose.foundation.rememberScrollState\n" not in text:
    text = text.replace("import androidx.compose.foundation.layout.*\n", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n", 1)
    write(settings, text)
replace_once(
    settings,
    '''private fun SettingsList(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
    }
}
''',
    '''private fun SettingsList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}
''',
)

# --- Library hierarchy: keep import/folder sync primary; move management/batch tools into one menu.
library = "apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt"
replace_once(
    library,
    "    var sortMenu by remember { mutableStateOf(false) }\n",
    "    var sortMenu by remember { mutableStateOf(false) }\n    var libraryToolsMenu by remember { mutableStateOf(false) }\n",
)
replace_once(
    library,
    '''                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    if (state.books.isNotEmpty()) TextButton(onClick = actions.onBatchImport) { Text(stringResource(R.string.batch_import)) }
                }
''',
    '''                Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
''',
)
replace_once(
    library,
    '''                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { folderLauncher.launch(null) }, label = { Text(stringResource(R.string.add_folder_library)) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) })
                    AssistChip(onClick = ::startFolderSync, enabled = !syncBusy, label = { Text(stringResource(R.string.sync_folders, folderRoots.size)) }, leadingIcon = { if (syncBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null) })
                    if (folderRoots.isNotEmpty()) AssistChip(onClick = { manageFolders = true }, label = { Text(stringResource(R.string.manage_folder_library, folderRoots.size)) }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                    AssistChip(onClick = { runBatch(false) }, enabled = !batchBusy && state.books.isNotEmpty(), label = { Text(stringResource(R.string.batch_optimize)) }, leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) })
                }
''',
    '''                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { folderLauncher.launch(null) }, label = { Text(stringResource(R.string.add_folder_library)) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) })
                    AssistChip(onClick = ::startFolderSync, enabled = !syncBusy, label = { Text(stringResource(R.string.sync_folders, folderRoots.size)) }, leadingIcon = { if (syncBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null) })
                    Box {
                        AssistChip(onClick = { libraryToolsMenu = true }, label = { Text(stringResource(R.string.library_more_actions)) }, leadingIcon = { Icon(Icons.Default.MoreVert, null) })
                        DropdownMenu(expanded = libraryToolsMenu, onDismissRequest = { libraryToolsMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.batch_import)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, onClick = { libraryToolsMenu = false; actions.onBatchImport() })
                            if (folderRoots.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.manage_folder_library, folderRoots.size)) }, leadingIcon = { Icon(Icons.Default.Folder, null) }, onClick = { libraryToolsMenu = false; manageFolders = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.batch_optimize)) }, enabled = !batchBusy && state.books.isNotEmpty(), leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) }, onClick = { libraryToolsMenu = false; runBatch(false) })
                        }
                    }
                }
''',
)

# --- Localized label for the consolidated library tools menu.
for rel, value in [
    ("apps/android/app/src/main/res/values/strings.xml", "More library actions"),
    ("apps/android/app/src/main/res/values-b+zh+Hans/strings.xml", "更多书库操作"),
    ("apps/android/app/src/main/res/values-b+zh+Hant/strings.xml", "更多書庫操作"),
]:
    text = read(rel)
    if 'name="library_more_actions"' not in text:
        marker = "</resources>"
        if marker not in text:
            raise SystemExit(f"{rel}: missing resources end")
        text = text.replace(marker, f'    <string name="library_more_actions">{value}</string>\n{marker}', 1)
        write(rel, text)

# --- UI tests: real controls, scrollable chapters and bookmark discoverability.
uitest = "apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt"
text = read(uitest)
if "import androidx.compose.ui.test.performTouchInput\n" not in text:
    text = text.replace("import androidx.compose.ui.test.performClick\n", "import androidx.compose.ui.test.performClick\nimport androidx.compose.ui.test.performTouchInput\nimport androidx.compose.ui.test.swipeUp\n", 1)
write(uitest, text)
replace_once(
    uitest,
    '''        composeRule.onNodeWithContentDescription("+").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("+").performClick()
''',
    '''        composeRule.onNodeWithText("+").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("+").performClick()
''',
)
replace_once(
    uitest,
    '        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_mode_continuous)).performClick()\n',
    '        composeRule.onNodeWithText(context.getString(R.string.reader_mode_continuous)).performClick()\n',
)
regex_once(
    uitest,
    r"    @Test fun chaptersPagingAndRowsRemainTouchableAfterLocalPanelStateChanges\(\) \{.*?\n    \}\n\n    @Test fun annotationsAreFirstClassLocalReaderAssets",
    '''    @Test fun chaptersScrollAndRowsRemainTouchableAfterLocalPanelStateChanges() {
        var jumped = -1L
        val chapters = (0 until 30).map { index -> ChapterModel(index * 1000L, "Chapter ${index + 1}") }
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", position = 0, length = 30_000,
                    panel = ReaderPanel.CHAPTERS, chapters = chapters, chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions().copy(onJump = { jumped = it }),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chapter 1").assertExists()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chapter 9").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(8_000L, jumped)
    }

    @Test fun annotationsAreFirstClassLocalReaderAssets''',
)
replace_once(
    uitest,
    '        composeRule.onNodeWithContentDescription(context.getString(R.string.start_read_aloud)).assertIsDisplayed()\n',
    '        composeRule.onNodeWithContentDescription(context.getString(R.string.start_read_aloud)).assertIsDisplayed()\n        composeRule.onNodeWithContentDescription(context.getString(R.string.bookmarks)).assertIsDisplayed()\n',
)

# --- Macrobenchmark: chapter panel must handle real scrolling, not just open/close.
journey = "apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt"
replace_once(
    journey,
    '''            requireChaptersClick()
            device.waitForIdle()
            // BACK closes the hot panel. Reader controls may legitimately have auto-hidden while the
''',
    '''            requireChaptersClick()
            device.waitForIdle()
            check(device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.82).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.46).toInt(),
                18,
            )) { "Reader chapters list swipe was not injected" }
            device.waitForIdle()
            // BACK closes the hot panel. Reader controls may legitimately have auto-hidden while the
''',
)

# --- Reader contract: remove superseded Canvas-panel requirements and lock the new interaction rules.
verify = "scripts/verify-reader.sh"
text = read(verify)
for line in [
    'hot_panel_canvas=apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanelCanvas.kt\n',
    'require_literal "$hot_controls" \'Exact overload used by ReaderReadingStatus\' \'Canvas reading status text\'\n',
    'require_literal "$hot_controls" \'Canvas(modifier.fillMaxWidth().height(22.dp))\' \'fixed-cost status drawing\'\n',
    'require_literal "$app" \'rememberGraphicsLayer()\' \'full hot-panel graphics layer cache\'\n',
    'require_literal "$app" \'layer.record(density, layoutDirection, size)\' \'full hot-panel pre-record\'\n',
    'require_literal "$app" \'drawLayer(layer)\' \'full hot-panel replay\'\n',
    'require_literal "$hot_panel_canvas" \'rememberGraphicsLayer()\' \'hot panel graphics layer\'\n',
    'require_literal "$hot_panel_canvas" \'layer.record(density, layoutDirection, layerSize)\' \'hot panel pre-record\'\n',
    'require_literal "$hot_panel_canvas" \'drawLayer(layer)\' \'hot panel display-list replay\'\n',
    'require_literal "$quick_panel" \'recordKey = listOf(colors\' \'quick panel record invalidation key\'\n',
    'require_literal "$smart_panel" \'recordKey = listOf(colors\' \'chapters panel record invalidation key\'\n',
]:
    if line in text:
        text = text.replace(line, "", 1)
write(verify, text)
replace_once(
    verify,
    "# Explicit display-list pre-recording keeps first visible panel draw and continuous text recording outside interaction frames.\n",
    "# Interactive hot panels use real controls/lists; only the reader text raster path may use display caching.\n",
)
insert_marker = "require_literal \"$hot_controls\" 'CenterAlignedTopAppBar' 'flattened reader top bar'\n"
text = read(verify)
if "semantic Material reader chrome" not in text:
    if insert_marker not in text:
        raise SystemExit("verify-reader.sh: hot controls marker missing")
    text = text.replace(
        insert_marker,
        insert_marker +
        "forbid_literal \"$hot_controls\" 'ReaderHotLine' 'Canvas-only reader chrome text'\n" +
        "require_literal \"$quick_panel\" 'real Compose controls' 'native quick settings controls'\n" +
        "forbid_literal \"$quick_panel\" 'ReaderCanvasPanel(' 'quick settings Canvas hit map'\n" +
        "require_literal \"$smart_panel\" 'LazyColumn(' 'scrolling chapters list'\n" +
        "require_literal \"$smart_panel\" 'rememberLazyListState()' 'chapter list state'\n" +
        "forbid_literal \"$smart_panel\" 'CHAPTER_WINDOW_ROWS' 'manual chapter pagination'\n" +
        "forbid_literal \"$smart_panel\" 'ReaderCanvasPanel(' 'chapter Canvas hit map'\n" +
        "require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderGesturePolicy.kt 'allowsPageSwipe' 'selection-aware paging policy'\n" +
        "require_literal apps/android/app/src/test/java/com/junchen/jingdu/ReaderGesturePolicyTest.kt 'fastHorizontalSwipeCanPassSelectionConsumption' 'gesture arbitration regression test'\n",
        1,
    )
    write(verify, text)

# Old full-panel Canvas infrastructure is intentionally removed once no active caller remains.
hot_canvas = ROOT / "apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanelCanvas.kt"
if hot_canvas.exists():
    hot_canvas.unlink()
    changed.append(str(hot_canvas.relative_to(ROOT)))

print("Reader UX migration changed:")
for item in changed:
    print(f" - {item}")
