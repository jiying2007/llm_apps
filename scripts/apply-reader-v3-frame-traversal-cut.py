from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)

# 1) Paged text: replay a worker-built StaticLayout through a native RenderNode instead of
# recording/drawing the full StaticLayout from a Compose Canvas on every new page.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
old_paged = '''        Canvas(Modifier.fillMaxSize()) {
            layout?.let { ready ->
                // Measurement layouts intentionally carry no palette color. Apply the current reader
                // color immediately before drawing; all reusable pages are plain-body pages.
                ready.paint.color = resolvedColor.toArgb()
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                ready.draw(canvas)
                canvas.restore()
            }
        }
'''
new_paged = '''        AndroidView(
            factory = { ReaderPagedTextView(it) },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.setTextLayout(layout, resolvedColor.toArgb()) },
        )
'''
fast = replace_once(fast, old_paged, new_paged, "paged native RenderNode view")
marker = '''/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
internal class ReaderContinuousLayout'''
paged_view = '''private class ReaderPagedTextView(context: Context) : View(context) {
    private var textLayout: StaticLayout? = null
    private var color: Int = 0
    private var recorded: ReaderStaticLayoutRenderNode? = null

    fun setTextLayout(layout: StaticLayout?, nextColor: Int) {
        val changed = textLayout !== layout || color != nextColor
        if (!changed) return
        textLayout = layout
        color = nextColor
        if (layout == null) {
            recorded = null
            invalidate()
            return
        }
        layout.paint.color = nextColor
        if (Build.VERSION.SDK_INT >= 29) {
            recorded = (recorded ?: ReaderStaticLayoutRenderNode()).also { it.record(layout) }
        }
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        if (Build.VERSION.SDK_INT < 29 || recorded?.draw(canvas) != true) textLayout?.draw(canvas)
        canvas.restore()
    }
}

/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
internal class ReaderContinuousLayout'''
fast = replace_once(fast, marker, paged_view, "paged RenderNode class")

# 2) Continuous: keep direct StaticLayout RenderNode recording, but move the tall child with the
# child's RenderNode translation property. ViewGroup.scrollTo() was still forcing scroll traversal.
fast = replace_once(
    fast,
    '''    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        if (scrollY != pendingScrollY) scrollTo(0, pendingScrollY)
    }
''',
    '''    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val translation = -pendingScrollY.toFloat()
        if (content.translationY != translation) content.translationY = translation
    }
''',
    "continuous child translation",
)
fast = replace_once(
    fast,
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        pendingScrollY = offsetPx.roundToInt()
        if (scrollY != pendingScrollY) scrollTo(0, pendingScrollY)
    }
''',
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        pendingScrollY = offsetPx.roundToInt()
        content.translationY = -pendingScrollY.toFloat()
    }
''',
    "continuous layout translation",
)
fast_path.write_text(fast)

# 3) Hot panels: cache the *entire* shell+scrim+Canvas subtree. Hidden panels remain laid out so
# their display list can be recorded, but an Initial-pass pointer guard prevents hidden hit targets.
app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = app.replace("import androidx.compose.ui.graphics.graphicsLayer\n", "import androidx.compose.ui.draw.drawWithContent\nimport androidx.compose.ui.graphics.layer.drawLayer\nimport androidx.compose.ui.graphics.rememberGraphicsLayer\n")
app = app.replace("import androidx.compose.ui.semantics.hideFromAccessibility\n", "import androidx.compose.ui.input.pointer.PointerEventPass\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalLayoutDirection\nimport androidx.compose.ui.semantics.hideFromAccessibility\n")
app = app.replace("import androidx.compose.ui.unit.dp\n", "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.dp\n")
app = replace_once(
    app,
    '''                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS) {
                    ReaderQuickSettingsSheet(quickPanelState, trackedActions)
                }
                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS) {
                    ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition)
                }
''',
    '''                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS, quickPanelState) {
                    ReaderQuickSettingsSheet(quickPanelState, trackedActions)
                }
                PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS, chaptersPanelState) {
                    ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition)
                }
''',
    "panel record keys",
)
old_layer = '''/**
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
            .semantics { if (panelState.value != target) hideFromAccessibility() }
            .graphicsLayer {
                val visible = panelState.value == target
                translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
                alpha = if (visible) 1f else 0f
            },
    ) { content() }
}

private const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384
'''
new_layer = '''private class ReaderPanelDisplayListCache {
    var size: IntSize = IntSize.Zero
    var recordKey: Any? = null
}

/**
 * Quick/Chapters stay laid out for the whole Reader session. Their complete visual subtree is
 * explicitly recorded while hidden and replayed on open. Hot-panel state is read only from
 * semantics, pointer and draw phases; opening/closing never recomposes or remeasures the reader.
 */
@Composable
private fun PersistentReaderPanelLayer(
    panelState: State<ReaderPanel?>,
    target: ReaderPanel,
    recordKey: Any?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val layer = rememberGraphicsLayer()
    val cache = remember { ReaderPanelDisplayListCache() }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size ->
                if (cache.size != size) {
                    cache.size = size
                    cache.recordKey = null
                }
            }
            .semantics { if (panelState.value != target) hideFromAccessibility() }
            .pointerInput(panelState, target) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (panelState.value != target) event.changes.forEach { it.consume() }
                    }
                }
            }
            .drawWithContent panelDraw@{
                val size = cache.size
                if (size.width > 0 && size.height > 0 && cache.recordKey != recordKey) {
                    layer.record(density, layoutDirection, size) { this@panelDraw.drawContent() }
                    cache.recordKey = recordKey
                }
                if (panelState.value == target) {
                    if (cache.recordKey == recordKey) drawLayer(layer) else drawContent()
                }
            },
    ) { content() }
}
'''
app = replace_once(app, old_layer, new_layer, "full hot-panel display-list cache")
app_path.write_text(app)

# 4) Reader controls no longer animate Android system bars on every in-app controls show/hide.
# Global full-screen panels may show system bars; ordinary reader controls/hot panels remain immersive.
screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
old_bars = '''    LaunchedEffect(activity, state.panel) {
        snapshotFlow { controlsVisible }.distinctUntilChanged().collect { visible ->
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (visible || state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
'''
new_bars = '''    LaunchedEffect(activity, state.panel) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
'''
screen = replace_once(screen, old_bars, new_bars, "decouple controls from system bars")
screen_path.write_text(screen)

# 5) Contracts: preserve all journeys/SLOs while requiring the new traversal boundaries.
verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = verify.replace(
    '''require_literal "$fast_text" 'postOnAnimation(applyPendingScroll)' 'vsync-coalesced continuous scroll'
require_literal "$fast_text" 'ReaderStaticLayoutRenderNode' 'direct continuous RenderNode display list'
''',
    '''require_literal "$fast_text" 'postOnAnimation(applyPendingScroll)' 'vsync-coalesced continuous scroll'
require_literal "$fast_text" 'ReaderPagedTextView' 'paged native RenderNode view'
require_literal "$fast_text" 'AndroidView(' 'paged AndroidView renderer'
require_literal "$fast_text" 'ReaderStaticLayoutRenderNode' 'shared paged/continuous RenderNode display list'
''',
    1,
)
verify = verify.replace(
    '''forbid_literal "$fast_text" 'content.translationY = -value' 'continuous tall-child translation'
forbid_literal "$fast_text" 'fun consumeDelta(delta: Float)' 'obsolete Compose scroll adapter'
''',
    '''require_literal "$fast_text" 'content.translationY = -pendingScrollY.toFloat()' 'continuous child RenderNode translation'
forbid_literal "$fast_text" 'scrollTo(0, pendingScrollY)' 'continuous ViewGroup scroll traversal'
forbid_literal "$fast_text" 'fun consumeDelta(delta: Float)' 'obsolete Compose scroll adapter'
''',
    1,
)
verify = verify.replace(
    '''require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS)' 'phase-owned quick panel visibility'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS)' 'phase-owned chapters panel visibility'
''',
    '''require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS, quickPanelState)' 'cached quick panel visibility'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS, chaptersPanelState)' 'cached chapters panel visibility'
''',
    1,
)
verify = verify.replace(
    '''require_literal "$app" 'graphicsLayer {' 'RenderNode resident panels'
''',
    '''require_literal "$app" 'rememberGraphicsLayer()' 'full hot-panel graphics layer cache'
require_literal "$app" 'layer.record(density, layoutDirection, size)' 'full hot-panel pre-record'
require_literal "$app" 'drawLayer(layer)' 'full hot-panel replay'
require_literal "$app" 'awaitPointerEvent(PointerEventPass.Initial)' 'hidden hot-panel pointer guard'
''',
    1,
)
verify += '''\n# Reader controls must not drive OS inset animations on every show/hide.\nrequire_literal "$screen" 'if (state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())' 'global-panel-only system bars'\nforbid_literal "$screen" 'if (visible || state.panel != null)' 'controls-driven system bar animation'\n'''
verify_path.write_text(verify)
