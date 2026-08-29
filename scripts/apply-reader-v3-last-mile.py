#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)


screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()

old_controls = '''        if (controlsVisible) Box(Modifier.align(Alignment.TopCenter)) {
            ReaderTopBarV3(book.name, currentChapter, actions) { more = true }
        }
'''
new_controls = '''        // Keep hot controls composed for the whole reader session. Visibility changes are a
        // layout-phase placement only, so reopening controls never rebuilds the Material button tree.
        Box(
            Modifier.align(Alignment.TopCenter).offset {
                androidx.compose.ui.unit.IntOffset(0, if (controlsVisible) 0 else -READER_HIDDEN_LAYER_OFFSET_PX)
            },
        ) {
            ReaderTopBarV3(book.name, currentChapter, actions) { more = true }
        }
'''
screen = replace_once(screen, old_controls, new_controls, "top controls residency")
old_bottom = '''        if (controlsVisible) Box(Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBarV3(
'''
new_bottom = '''        Box(
            Modifier.align(Alignment.BottomCenter).offset {
                androidx.compose.ui.unit.IntOffset(0, if (controlsVisible) 0 else READER_HIDDEN_LAYER_OFFSET_PX)
            },
        ) {
            ReaderBottomBarV3(
'''
screen = replace_once(screen, old_bottom, new_bottom, "bottom controls residency")

old_flow = '''    LaunchedEffect(scrollableState, layoutResult, window, viewportHeight, state.autoScrolling) {
        snapshotFlow { scrollOffsetPx.roundToInt() to scrollableState.isScrollInProgress }.distinctUntilChanged().collect { (y, scrolling) ->
            val w = window ?: return@collect
            val layout = layoutResult ?: return@collect
            if (w.displayText.isEmpty() || layout.lineCount <= 0) return@collect
            val line = layout.getLineForVerticalPosition(y.toFloat()).coerceIn(0, layout.lineCount - 1)
            val utf = layout.getLineStart(line).coerceIn(0, w.displayText.length)
            val absolute = (w.start + w.map.sourceForDisplay(w.displayText.codePointCount(0, utf).toLong())).coerceIn(0L, (w.documentLength - 1).coerceAtLeast(0L))
            localPosition.set(absolute)
            val shouldCommit = if (state.autoScrolling) {
                abs(absolute - lastCommitted) >= AUTO_SCROLL_COMMIT_CHARS
            } else {
                !scrolling && absolute != lastCommitted
            }
            if (shouldCommit) {
                lastCommitted = absolute
                actions.onSyncTtsPosition(absolute)
            }
            val edge = (viewportHeight * 0.25f).roundToInt()
            val nearTop = y <= edge && w.start > 0
            val nearBottom = maxScrollPx > 0f && maxScrollPx - y.toFloat() <= edge.toFloat() && w.start + w.map.sourceCodePoints < w.documentLength - 1
            if (!loading && !scrolling && (nearTop || nearBottom)) loadAround(absolute)
        }
    }
'''
new_flow = '''    fun absoluteAtContinuousOffset(y: Int): Long? {
        val currentWindow = window ?: return null
        val layout = layoutResult ?: return null
        if (currentWindow.displayText.isEmpty() || layout.lineCount <= 0) return null
        val line = layout.getLineForVerticalPosition(y.toFloat()).coerceIn(0, layout.lineCount - 1)
        val utf = layout.getLineStart(line).coerceIn(0, currentWindow.displayText.length)
        val displayedCodePoints = currentWindow.displayText.codePointCount(0, utf).toLong()
        return (currentWindow.start + currentWindow.map.sourceForDisplay(displayedCodePoints))
            .coerceIn(0L, (currentWindow.documentLength - 1).coerceAtLeast(0L))
    }

    suspend fun settleContinuousPosition(y: Int, auto: Boolean) {
        val currentWindow = window ?: return
        val absolute = absoluteAtContinuousOffset(y) ?: return
        localPosition.set(absolute)
        val shouldCommit = if (auto) {
            abs(absolute - lastCommitted) >= AUTO_SCROLL_COMMIT_CHARS
        } else {
            absolute != lastCommitted
        }
        if (shouldCommit) {
            lastCommitted = absolute
            actions.onSyncTtsPosition(absolute)
        }
        val edge = (viewportHeight * 0.25f).roundToInt()
        val nearTop = y <= edge && currentWindow.start > 0
        val nearBottom = maxScrollPx > 0f &&
            maxScrollPx - y.toFloat() <= edge.toFloat() &&
            currentWindow.start + currentWindow.map.sourceCodePoints < currentWindow.documentLength - 1
        if (!loading && (nearTop || nearBottom)) loadAround(absolute)
    }

    // A manual swipe can emit dozens of scroll deltas. Position/source mapping is not visual work;
    // perform it once when the gesture settles instead of on every delta/frame.
    LaunchedEffect(scrollableState, layoutResult, window, viewportHeight) {
        snapshotFlow { scrollableState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling) settleContinuousPosition(scrollOffsetPx.roundToInt(), auto = false)
        }
    }
'''
screen = replace_once(screen, old_flow, new_flow, "continuous per-delta mapping")

old_auto = '''    LaunchedEffect(state.autoScrolling, settings.autoScrollSpeedDpPerSecond, window) {
        if (!state.autoScrolling) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive && state.autoScrolling) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0
                    val deltaPx = with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() }
                    scrollOffsetPx = (scrollOffsetPx + deltaPx).coerceIn(0f, maxScrollPx)
                }
                lastFrame = now
            }
            val w = window ?: continue
            if (maxScrollPx > 0f && scrollOffsetPx >= maxScrollPx - 1f && w.start + w.map.sourceCodePoints >= w.documentLength - 1) {
                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)); break
            }
        }
    }
'''
new_auto = '''    LaunchedEffect(state.autoScrolling, settings.autoScrollSpeedDpPerSecond, window) {
        if (!state.autoScrolling) return@LaunchedEffect
        var lastFrame = 0L
        var lastPositionSample = 0L
        while (isActive && state.autoScrolling) {
            var samplePosition = false
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0
                    val deltaPx = with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() }
                    scrollOffsetPx = (scrollOffsetPx + deltaPx).coerceIn(0f, maxScrollPx)
                }
                if (lastPositionSample == 0L || now - lastPositionSample >= AUTO_SCROLL_POSITION_SAMPLE_NS) {
                    lastPositionSample = now
                    samplePosition = true
                }
                lastFrame = now
            }
            if (samplePosition) settleContinuousPosition(scrollOffsetPx.roundToInt(), auto = true)
            val currentWindow = window ?: continue
            if (maxScrollPx > 0f && scrollOffsetPx >= maxScrollPx - 1f &&
                currentWindow.start + currentWindow.map.sourceCodePoints >= currentWindow.documentLength - 1) {
                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)); break
            }
        }
    }
'''
screen = replace_once(screen, old_auto, new_auto, "continuous auto cadence")

screen = replace_once(
    screen,
    'private const val AUTO_SCROLL_COMMIT_CHARS = 512L\n',
    'private const val AUTO_SCROLL_COMMIT_CHARS = 512L\nprivate const val AUTO_SCROLL_POSITION_SAMPLE_NS = 250_000_000L\nprivate const val READER_HIDDEN_LAYER_OFFSET_PX = 16_384\n',
    "reader hot constants",
)
screen_path.write_text(screen)

app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = replace_once(
    app,
    'import androidx.compose.foundation.layout.padding\n',
    'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.offset\n',
    "JingduApp offset import",
)
app = replace_once(
    app,
    'import androidx.compose.ui.unit.dp\n',
    'import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.unit.dp\n',
    "JingduApp IntOffset import",
)

state_anchor = '''        val readerState = remember(
            state.currentBook,
            state.pageText,
            state.position,
            state.length,
            state.cleanMode,
            state.chapters,
            state.chaptersLoaded,
            state.annotations,
            state.motion,
            state.tts,
            state.settings,
        ) {
            AppUiState(
                screen = AppScreen.READER,
                currentBook = state.currentBook,
                pageText = state.pageText,
                position = state.position,
                length = state.length,
                cleanMode = state.cleanMode,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
                annotations = state.annotations,
                motion = state.motion,
                tts = state.tts,
                settings = state.settings,
            )
        }
'''
state_new = state_anchor + '''        val quickPanelState = remember(state.settings, state.motion) {
            AppUiState(settings = state.settings, motion = state.motion)
        }
        val chaptersPanelState = remember(state.currentBook, state.length, state.chapters, state.chaptersLoaded) {
            AppUiState(
                currentBook = state.currentBook,
                length = state.length,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
            )
        }
'''
app = replace_once(app, state_anchor, state_new, "persistent panel states")

box_old = '''            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, trackedActions, snackbar)
                AppScreen.READER -> ReaderRoute(readerState, trackedActions, snackbar, location.canBack, location.canForward, stableLocationBack, stableLocationForward)
            }
            state.busyLabel?.let { BusyOverlay(it) }
            when (state.panel) {
                ReaderPanel.QUICK_SETTINGS -> ReaderQuickSettingsSheet(AppUiState(settings = state.settings, motion = state.motion), trackedActions)
                ReaderPanel.CHAPTERS -> ReaderSmartChaptersPanel(
                    AppUiState(currentBook = state.currentBook, length = state.length, chapters = state.chapters, chaptersLoaded = state.chaptersLoaded),
                    trackedActions,
                    currentReaderPosition,
                )
'''
box_new = '''            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, trackedActions, snackbar)
                AppScreen.READER -> ReaderRoute(readerState, trackedActions, snackbar, location.canBack, location.canForward, stableLocationBack, stableLocationForward)
            }
            if (state.screen == AppScreen.READER) {
                PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS) {
                    ReaderQuickSettingsSheet(quickPanelState, trackedActions)
                }
                PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS) {
                    ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition)
                }
            }
            state.busyLabel?.let { BusyOverlay(it) }
            when (state.panel) {
                ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit
'''
app = replace_once(app, box_old, box_new, "persistent panel routing")

helper_anchor = '''@Composable private fun BusyOverlay(label: String) {
'''
helper = '''/**
 * Quick/Chapters are high-frequency reader overlays. Keep them composed after Reader opens and
 * move the complete layer outside the viewport while hidden. Modifier.offset reads visibility in
 * layout, so open/close does not destroy and recreate the panel composition or its Canvas display list.
 */
@Composable
private fun PersistentReaderPanelLayer(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().offset {
            IntOffset(0, if (visible) 0 else READER_PANEL_HIDDEN_OFFSET_PX)
        },
    ) { content() }
}

private const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384

@Composable private fun BusyOverlay(label: String) {
'''
app = replace_once(app, helper_anchor, helper, "persistent panel helper")
app_path.write_text(app)

verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = replace_once(
    verify,
    "require_literal \"$screen\" '!scrolling && absolute != lastCommitted' 'manual continuous commit'\n",
    "require_literal \"$screen\" 'snapshotFlow { scrollableState.isScrollInProgress }' 'scroll-end continuous mapping'\nforbid_literal \"$screen\" 'snapshotFlow { scrollOffsetPx.roundToInt()' 'per-delta continuous source mapping'\nrequire_literal \"$screen\" 'settleContinuousPosition(scrollOffsetPx.roundToInt(), auto = false)' 'manual continuous settle'\nrequire_literal \"$screen\" 'AUTO_SCROLL_POSITION_SAMPLE_NS = 250_000_000L' 'coarse auto-scroll position cadence'\n",
    "continuous contract",
)
verify = replace_once(
    verify,
    "require_literal \"$app\" 'ReaderPanel.QUICK_SETTINGS -> ReaderQuickSettingsSheet' 'quick settings route'\nrequire_literal \"$app\" 'ReaderPanel.CHAPTERS -> ReaderSmartChaptersPanel' 'chapters route'\n",
    "require_literal \"$app\" 'PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS)' 'resident quick settings layer'\nrequire_literal \"$app\" 'PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS)' 'resident chapters layer'\nrequire_literal \"$app\" 'ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit' 'persistent panel route ownership'\nrequire_literal \"$screen\" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'\n",
    "panel route contract",
)
verify_path.write_text(verify)

publisher_path = Path("scripts/publish-source-release.py")
publisher = publisher_path.read_text()
publisher = publisher.replace(
    'TEMP_PREFIXES = ("feat/", "fix/", "chore/", "ci/", "refactor/", "docs/", "test/", "perf/")',
    'TEMP_PREFIXES = ("feat/", "fix/", "chore/", "ci/", "refactor/", "docs/", "test/", "perf/", "tmp/")',
    1,
)
publisher_path.write_text(publisher)
