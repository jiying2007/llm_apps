from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)

# Continuous: record StaticLayout into a direct Android RenderNode once, avoid a huge rasterized
# hardware layer, and coalesce MOVE-driven viewport scroll changes to one property update per vsync.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = replace_once(fast, "import android.graphics.Paint\nimport android.graphics.Typeface\n", "import android.graphics.Paint\nimport android.graphics.RenderNode\nimport android.graphics.Typeface\nimport android.os.Build\n", "RenderNode imports")
fast = replace_once(
    fast,
    '''    private var flingRunning = false
    private var velocityTracker: VelocityTracker? = null
''',
    '''    private var flingRunning = false
    private var pendingScrollY = 0
    private var scrollScheduled = false
    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        if (scrollY != pendingScrollY) scrollTo(0, pendingScrollY)
    }
    private var velocityTracker: VelocityTracker? = null
''',
    "vsync scroll fields",
)
fast = replace_once(fast, "        content.setLayerType(View.LAYER_TYPE_HARDWARE, null)\n        addView(content)\n", "        addView(content)\n", "remove tall raster layer")
fast = replace_once(
    fast,
    '''    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        val y = value.roundToInt()
        if (scrollY != y) scrollTo(0, y)
    }
''',
    '''    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        pendingScrollY = value.roundToInt()
        if (!scrollScheduled) {
            scrollScheduled = true
            postOnAnimation(applyPendingScroll)
        }
    }
''',
    "vsync scroll coalescing",
)
fast = replace_once(
    fast,
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        scrollTo(0, offsetPx.roundToInt())
        if (changed && content.isHardwareAccelerated) {
            content.post {
                if (content.isHardwareAccelerated && content.layerType == View.LAYER_TYPE_HARDWARE) content.buildLayer()
            }
        }
    }
}

private class ReaderContinuousTextView(context: Context) : View(context) {
    private var textLayout: StaticLayout? = null
    private var color: Int = 0

    fun setTextLayout(layout: StaticLayout, nextColor: Int) {
        val changed = textLayout !== layout || color != nextColor
        textLayout = layout
        color = nextColor
        layout.paint.color = nextColor
        if (changed) invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        textLayout?.draw(canvas)
    }
}
''',
    '''    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        pendingScrollY = offsetPx.roundToInt()
        if (scrollY != pendingScrollY) scrollTo(0, pendingScrollY)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(applyPendingScroll)
        scrollScheduled = false
        super.onDetachedFromWindow()
    }
}

private class ReaderContinuousTextView(context: Context) : View(context) {
    private var textLayout: StaticLayout? = null
    private var color: Int = 0
    private var recorded: ReaderStaticLayoutRenderNode? = null

    fun setTextLayout(layout: StaticLayout, nextColor: Int) {
        val changed = textLayout !== layout || color != nextColor
        textLayout = layout
        color = nextColor
        layout.paint.color = nextColor
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            recorded = (recorded ?: ReaderStaticLayoutRenderNode()).also { it.record(layout) }
        }
        if (changed) invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (Build.VERSION.SDK_INT >= 29 && recorded?.draw(canvas) == true) return
        textLayout?.draw(canvas)
    }
}

@android.annotation.TargetApi(29)
private class ReaderStaticLayoutRenderNode {
    private val node = RenderNode("ReaderContinuousStaticLayout")

    fun record(layout: StaticLayout) {
        val width = layout.width.coerceAtLeast(1)
        val height = layout.height.coerceAtLeast(1)
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            layout.draw(canvas)
        } finally {
            node.endRecording()
        }
    }

    fun draw(canvas: android.graphics.Canvas): Boolean {
        if (!canvas.isHardwareAccelerated || !node.hasDisplayList()) return false
        canvas.drawRenderNode(node)
        return true
    }
}
''',
    "direct RenderNode text display list",
)
fast_path.write_text(fast)

# Hot panels: explicitly record their DrawScope commands as soon as the hidden panel has a size.
# Visibility changes then draw an already-built GraphicsLayer rather than recording Canvas commands.
panel_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanelCanvas.kt")
panel = panel_path.read_text()
panel = replace_once(panel, "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\n", "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\nimport androidx.compose.runtime.setValue\n", "panel runtime imports")
panel = replace_once(panel, "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.drawscope.DrawScope\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.drawscope.DrawScope\nimport androidx.compose.ui.graphics.layer.drawLayer\nimport androidx.compose.ui.graphics.rememberGraphicsLayer\n", "GraphicsLayer imports")
panel = replace_once(panel, "import androidx.compose.ui.platform.LocalDensity\n", "import androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalLayoutDirection\n", "panel layout imports")
panel = replace_once(panel, "import androidx.compose.ui.unit.Dp\n", "import androidx.compose.ui.unit.Dp\nimport androidx.compose.ui.unit.IntSize\n", "panel IntSize import")
panel = replace_once(
    panel,
    '''internal fun ReaderCanvasPanel(
    height: Dp,
    description: String,
    actions: List<CustomAccessibilityAction>,
    onTap: (Offset, Float, Float) -> Unit,
    draw: DrawScope.() -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(description) {
                detectTapGestures { point -> onTap(point, size.width.toFloat(), size.height.toFloat()) }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = actions
                role = Role.Button
            },
        onDraw = draw,
    )
}
''',
    '''internal fun ReaderCanvasPanel(
    height: Dp,
    description: String,
    actions: List<CustomAccessibilityAction>,
    onTap: (Offset, Float, Float) -> Unit,
    recordKey: Any? = Unit,
    draw: DrawScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val layer = rememberGraphicsLayer()
    val latestDraw = rememberUpdatedState(draw)
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var recordedKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(recordKey, layerSize, density.density, density.fontScale, layoutDirection) {
        if (layerSize.width <= 0 || layerSize.height <= 0) return@LaunchedEffect
        layer.record(density, layoutDirection, layerSize) { latestDraw.value(this) }
        recordedKey = recordKey
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { layerSize = it }
            .pointerInput(description) {
                detectTapGestures { point -> onTap(point, size.width.toFloat(), size.height.toFloat()) }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = actions
                role = Role.Button
            },
    ) {
        if (recordedKey == recordKey && layerSize.width > 0 && layerSize.height > 0) drawLayer(layer)
        else latestDraw.value(this)
    }
}
''',
    "panel display-list pre-record",
)
panel_path.write_text(panel)

quick_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt")
quick = quick_path.read_text()
quick = replace_once(
    quick,
    '''                actions = accessibilityActions,
                onTap = { point, width, _ ->
''',
    '''                actions = accessibilityActions,
                recordKey = listOf(colors, s.palette, s.fontSizeSp, s.lineHeightMultiplier, s.readingMode, state.autoScrolling, s.autoScrollSpeedDpPerSecond),
                onTap = { point, width, _ ->
''',
    "quick panel record key",
)
quick_path.write_text(quick)

chapters_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt")
chapters = chapters_path.read_text()
chapters = replace_once(
    chapters,
    '''                actions = accessibilityActions,
                onTap = { point, width, _ ->
''',
    '''                actions = accessibilityActions,
                recordKey = listOf(colors, loading, windowStart, current),
                onTap = { point, width, _ ->
''',
    "chapters panel record key",
)
chapters_path.write_text(chapters)

# Current Compose API deprecates invisibleToUser; keep hidden panels out of accessibility with the
# supported replacement without changing visibility semantics.
app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = app.replace("import androidx.compose.ui.semantics.invisibleToUser\n", "import androidx.compose.ui.semantics.hideFromAccessibility\n")
app = app.replace("invisibleToUser()", "hideFromAccessibility()")
app_path.write_text(app)

# Contracts: preserve the same SLO and journeys, require explicit pre-recording and forbid the old
# giant raster layer / immediate per-MOVE scroll traversal.
verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = verify.replace("require_literal \"$fast_text\" 'scrollTo(0, y)' 'continuous viewport scroll property'\n", "require_literal \"$fast_text\" 'postOnAnimation(applyPendingScroll)' 'vsync-coalesced continuous scroll'\nrequire_literal \"$fast_text\" 'ReaderStaticLayoutRenderNode' 'direct continuous RenderNode display list'\nrequire_literal \"$fast_text\" 'node.beginRecording(width, height)' 'continuous StaticLayout pre-record'\nrequire_literal \"$fast_text\" 'canvas.drawRenderNode(node)' 'continuous display-list replay'\n")
verify = verify.replace("require_literal \"$fast_text\" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'continuous cached text layer'\n", "forbid_literal \"$fast_text\" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'oversized rasterized continuous layer'\n")
verify = verify.replace("require_literal \"$fast_text\" 'content.buildLayer()' 'continuous text layer prebuild'\n", "forbid_literal \"$fast_text\" 'content.buildLayer()' 'oversized raster layer prebuild'\n")
verify += '''\n# Explicit display-list pre-recording keeps first visible panel draw and continuous text recording outside interaction frames.\nrequire_literal "$hot_panel_canvas" 'rememberGraphicsLayer()' 'hot panel graphics layer'\nrequire_literal "$hot_panel_canvas" 'layer.record(density, layoutDirection, layerSize)' 'hot panel pre-record'\nrequire_literal "$hot_panel_canvas" 'drawLayer(layer)' 'hot panel display-list replay'\nrequire_literal "$quick_panel" 'recordKey = listOf(colors' 'quick panel record invalidation key'\nrequire_literal "$smart_panel" 'recordKey = listOf(colors' 'chapters panel record invalidation key'\nrequire_literal "$app" 'hideFromAccessibility()' 'supported hidden panel accessibility semantics'\nforbid_literal "$app" 'invisibleToUser()' 'deprecated hidden panel accessibility semantics'\n'''
verify_path.write_text(verify)
