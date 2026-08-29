from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)


# 1) Continuous: parent viewport owns scrollY; the tall text node is a persistent HW layer.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = replace_once(
    fast,
    '''    fun consumeDelta(delta: Float): Float {
        val previous = offsetPx
        setOffset(previous - delta)
        return previous - offsetPx
    }
''',
    '',
    "obsolete Compose delta adapter",
)
fast = replace_once(
    fast,
    '''    init {
        clipChildren = true
        isClickable = true
        addView(content)
    }''',
    '''    init {
        clipChildren = true
        clipToPadding = true
        setWillNotDraw(true)
        isClickable = true
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        addView(content)
    }''',
    "continuous viewport init",
)
fast = replace_once(
    fast,
    '''    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        content.translationY = -value
    }''',
    '''    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        val y = value.roundToInt()
        if (scrollY != y) scrollTo(0, y)
    }''',
    "continuous viewport scroll property",
)
fast = replace_once(
    fast,
    '''            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val handledScroll = scrolling
                if (pinching && settings.pinchFontEnabled && abs(pinchScale - 1f) >= 0.04f) onResizeFont(pinchScale)
                else if (!longPressTriggered && !handledScroll) dispatchCompletedGesture(event)
                if (handledScroll) finishScrollWithFling()
                recycleTouch()
                performClick()
                return true
            }''',
    '''            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val handledScroll = scrolling
                val handledPinch = pinching
                val handledLongPress = longPressTriggered
                if (handledPinch && settings.pinchFontEnabled && abs(pinchScale - 1f) >= 0.04f) {
                    onResizeFont(pinchScale)
                } else if (!handledLongPress && !handledScroll && !handledPinch) {
                    dispatchCompletedGesture(event)
                    performClick()
                }
                if (handledScroll) finishScrollWithFling()
                recycleTouch()
                return true
            }''',
    "scroll ACTION_UP click semantics",
)
fast = replace_once(
    fast,
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        content.translationY = -offsetPx
    }''',
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        scrollTo(0, offsetPx.roundToInt())
        if (changed && content.isHardwareAccelerated) {
            content.post {
                if (content.isHardwareAccelerated && content.layerType == View.LAYER_TYPE_HARDWARE) content.buildLayer()
            }
        }
    }''',
    "continuous viewport layout",
)
fast_path.write_text(fast)

# 2) Reader chrome: reading status subscribes to page state only while it is actually visible.
screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
screen = replace_once(
    screen,
    '    var controlsVisible by rememberSaveable(book.id) { mutableStateOf(true) }\n',
    '    val controlsVisibility = rememberSaveable(book.id) { mutableStateOf(true) }\n    var controlsVisible by controlsVisibility\n',
    "controls state object",
)
screen = replace_once(
    screen,
    '''    val currentChapterIndex = remember(state.chapters, state.position) { state.chapters.indexOfLast { it.offset <= state.position } }
    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title
''',
    '''    val currentChapterIndex = remember(state.chapters, state.position) { state.chapters.indexOfLast { it.offset <= state.position } }
    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title
    val latestStatusState = rememberUpdatedState(state)
    val latestStatusChapterIndex = rememberUpdatedState(currentChapterIndex)
    val statusStateProvider = remember { { latestStatusState.value } }
    val statusChapterIndexProvider = remember { { latestStatusChapterIndex.value } }
''',
    "reading status stable providers",
)
screen = replace_once(
    screen,
    '''        if (settings.showReadingStatus) ReaderReadingStatusV3(
            state, currentChapterIndex, textColor, background, stats,
            Modifier.align(Alignment.BottomCenter).graphicsLayer {
                translationY = if (controlsVisible) READER_HIDDEN_LAYER_OFFSET_PX.toFloat() else 0f
                alpha = if (controlsVisible) 0f else 1f
            },
        )''',
    '''        if (settings.showReadingStatus) ReaderReadingStatusHost(
            controlsVisibility = controlsVisibility,
            stateProvider = statusStateProvider,
            chapterIndexProvider = statusChapterIndexProvider,
            color = textColor,
            background = background,
            stats = stats,
            modifier = Modifier.align(Alignment.BottomCenter),
        )''',
    "reading status root subscription",
)
screen = replace_once(
    screen,
    '''@Composable
private fun ReaderReadingStatusV3(state: AppUiState, chapterIndex: Int, color: Color, background: Color, stats: ReaderStatsStore, modifier: Modifier = Modifier) {''',
    '''@Composable
private fun ReaderReadingStatusHost(
    controlsVisibility: State<Boolean>,
    stateProvider: () -> AppUiState,
    chapterIndexProvider: () -> Int,
    color: Color,
    background: Color,
    stats: ReaderStatsStore,
    modifier: Modifier = Modifier,
) {
    if (!controlsVisibility.value) {
        ReaderReadingStatusV3(stateProvider(), chapterIndexProvider(), color, background, stats, modifier)
    }
}

@Composable
private fun ReaderReadingStatusV3(state: AppUiState, chapterIndex: Int, color: Color, background: Color, stats: ReaderStatsStore, modifier: Modifier = Modifier) {''',
    "reading status host insertion",
)
screen_path.write_text(screen)

# 3) Quick/Chapters visibility is observed only by BackHandler/semantics/layer invalidation scopes.
app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = replace_once(
    app,
    '''import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState''',
    '''import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState''',
    "panel State import",
)
app = replace_once(
    app,
    '''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle''',
    '''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle''',
    "panel semantics imports",
)
app = replace_once(
    app,
    '''        val chaptersPanelState = remember(state.currentBook, state.length, state.chapters, state.chaptersLoaded) {
            AppUiState(
                currentBook = state.currentBook,
                length = state.length,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
            )
        }

        BackHandler''',
    '''        val chaptersPanelState = remember(state.currentBook, state.length, state.chapters, state.chaptersLoaded) {
            AppUiState(
                currentBook = state.currentBook,
                length = state.length,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
            )
        }
        val fallbackPanelState = rememberUpdatedState(state.panel)
        val hotPanelState: State<ReaderPanel?> = if (hotPanel != null) hotPanel.collectAsStateWithLifecycle() else fallbackPanelState

        BackHandler''',
    "stable hot panel State",
)
app = replace_once(
    app,
    '''            if (state.screen == AppScreen.READER) {
                if (hotPanel != null) ReaderHotPanelHost(hotPanel, quickPanelState, chaptersPanelState, trackedActions, currentReaderPosition)
                else {
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS) { ReaderQuickSettingsSheet(quickPanelState, trackedActions) }
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS) { ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition) }
                }
            }''',
    '''            if (state.screen == AppScreen.READER) {
                ReaderHotPanelBackHandler(hotPanelState, trackedActions)
                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS) {
                    ReaderQuickSettingsSheet(quickPanelState, trackedActions)
                }
                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS) {
                    ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition)
                }
            }''',
    "hot panel stable child layers",
)
old_host = '''/** Hot overlay state is collected in this restart group only; ReaderRoute never subscribes to it. */
@Composable
private fun ReaderHotPanelHost(
    panelFlow: StateFlow<ReaderPanel?>,
    quickState: AppUiState,
    chaptersState: AppUiState,
    actions: JingduActions,
    currentPosition: () -> Long,
) {
    val panel = panelFlow.collectAsStateWithLifecycle().value
    BackHandler(enabled = panel != null) { actions.onClosePanel() }
    PersistentReaderPanelLayer(panel == ReaderPanel.QUICK_SETTINGS) { ReaderQuickSettingsSheet(quickState, actions) }
    PersistentReaderPanelLayer(panel == ReaderPanel.CHAPTERS) { ReaderSmartChaptersPanel(chaptersState, actions, currentPosition) }
}

/**
 * Quick/Chapters are high-frequency reader overlays. Keep them composed after Reader opens and
 * move the complete layer outside the viewport while hidden. Graphics-layer property updates avoid
 * remeasure/recomposition of the reader, while hidden semantics are removed from accessibility.
 */
@Composable
private fun PersistentReaderPanelLayer(visible: Boolean, content: @Composable () -> Unit) {
    val hiddenSemantics = if (visible) Modifier else Modifier.clearAndSetSemantics { }
    Box(
        Modifier.fillMaxSize().then(hiddenSemantics).graphicsLayer {
            translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
            alpha = if (visible) 1f else 0f
        },
    ) { content() }
}
'''
new_host = '''/** Only this tiny restart group reads panel state in composition for Back dispatch. */
@Composable
private fun ReaderHotPanelBackHandler(panelState: State<ReaderPanel?>, actions: JingduActions) {
    val panel = panelState.value
    BackHandler(enabled = panel == ReaderPanel.QUICK_SETTINGS || panel == ReaderPanel.CHAPTERS) { actions.onClosePanel() }
}

/**
 * Quick/Chapters stay composed for the whole Reader session. The panel State object is stable;
 * visibility itself is read only in semantics and graphics-layer phases, so open/close never
 * recomposes, remeasures or rebuilds either panel subtree.
 */
@Composable
private fun PersistentReaderPanelLayer(
    panelState: State<ReaderPanel?>,
    target: ReaderPanel,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .semantics { if (panelState.value != target) invisibleToUser() }
            .graphicsLayer {
                val visible = panelState.value == target
                translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
                alpha = if (visible) 1f else 0f
            },
    ) { content() }
}
'''
app = replace_once(app, old_host, new_host, "hot panel phase-owned visibility")
app_path.write_text(app)

# 4) Contracts + #722 independent profile provenance.
verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = verify.replace(
    '''require_literal "$fast_text" 'content.translationY = -value' 'continuous RenderNode translation'
require_literal "$fast_text" 'model.setOffset(model.offsetPx + (lastY - event.y))' 'native direct scroll property update' ''',
    '''require_literal "$fast_text" 'scrollTo(0, y)' 'continuous viewport scroll property'
require_literal "$fast_text" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'continuous cached text layer'
require_literal "$fast_text" 'content.buildLayer()' 'continuous text layer prebuild'
forbid_literal "$fast_text" 'content.translationY = -value' 'continuous tall-child translation'
require_literal "$fast_text" 'model.setOffset(model.offsetPx + (lastY - event.y))' 'native direct scroll property update'
forbid_literal "$fast_text" 'fun consumeDelta(delta: Float)' 'obsolete Compose scroll adapter' ''',
    1,
)
verify = verify.replace(
    '''require_literal "$app" 'ReaderHotPanelHost(hotPanel' 'isolated hot panel restart group' ''',
    '''require_literal "$app" 'val hotPanelState: State<ReaderPanel?>' 'stable hot panel state object'
require_literal "$app" 'ReaderHotPanelBackHandler(hotPanelState' 'isolated hot panel BackHandler'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS)' 'phase-owned quick panel visibility'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS)' 'phase-owned chapters panel visibility'
require_literal "$app" '.semantics { if (panelState.value != target) invisibleToUser() }' 'phase-owned hidden panel semantics'
forbid_literal "$app" 'PersistentReaderPanelLayer(panel ==' 'composition-owned panel visibility' ''',
    1,
)
verify = verify.replace(
    '''require_literal "$screen" 'snapshotFlow { controlsVisible }' 'layer-only controls visibility'
forbid_literal "$screen" 'padding(bottom = if (controlsVisible)' 'controls visibility composition padding' ''',
    '''require_literal "$screen" 'snapshotFlow { controlsVisible }' 'layer-only controls visibility'
require_literal "$screen" 'val controlsVisibility = rememberSaveable(book.id)' 'stable controls visibility state object'
require_literal "$screen" 'ReaderReadingStatusHost(' 'isolated reading status restart group'
forbid_literal "$screen" 'if (settings.showReadingStatus) ReaderReadingStatusV3(' 'root reading-status page subscription'
forbid_literal "$screen" 'padding(bottom = if (controlsVisible)' 'controls visibility composition padding' ''',
    1,
)
verify = verify.replace("'manual continuous settle' require_literal \"$screen\" 'AUTO_SCROLL_POSITION_SAMPLE_NS", "'manual continuous settle'\nrequire_literal \"$screen\" 'AUTO_SCROLL_POSITION_SAMPLE_NS")
verify_path.write_text(verify)

prov_path = Path("docs/READER_V3_PROFILE_PROVENANCE.md")
prov = prov_path.read_text()
prov = prov.replace("`97bd7b952735255d567fb13f7d8777bdf4c7858e`", "`281c3ffa2d301746ae392509ce8ae7338b247f66`")
prov = prov.replace("`#715` / run `33224899370`", "`#722` / run `33227054478`")
prov = prov.replace("24,485 rules, 2,553,730 bytes, SHA-256 `141e3f372636d74862f437ee7a62cb424ca412447012870981c42023b0439509`", "24,028 rules, 2,491,741 bytes, SHA-256 `46d5e7015be3132ccf8881a8592c560a00a6b40dd45a9aeb3d43689864b1ac3b`")
prov = prov.replace("22,827 rules, 2,346,528 bytes, SHA-256 `791ee598c4eb2271a2c8cda213ff7af400f5f395143e4c52675851199618be82`", "22,897 rules, 2,353,766 bytes, SHA-256 `f845cecec641ecc014edc6b0c134d6307fd0d033e68b543daa27c23659d4ccb9`")
prov = prov.replace("The #715 capture confirms", "The #722 capture confirms")
prov_path.write_text(prov)
