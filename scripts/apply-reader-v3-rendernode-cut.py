#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)


# Continuous reader: keep the bounded 4K StaticLayout, but record it once in an Android child View.
# Scroll deltas then update only the child RenderNode translation instead of invalidating Compose Canvas.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = replace_once(fast, "import android.view.accessibility.AccessibilityManager\n", "import android.view.View\nimport android.view.ViewGroup\nimport android.view.accessibility.AccessibilityManager\n", "native view imports")
fast = replace_once(fast, "import androidx.compose.ui.unit.dp\n", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.viewinterop.AndroidView\n", "AndroidView import")
start = fast.index("/** Native bounded continuous layout:")
end = fast.index("private fun buildFastStaticLayout", start)
new_continuous = r'''/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
internal class ReaderContinuousLayout internal constructor(internal val layout: StaticLayout) {
    val lineCount: Int get() = layout.lineCount
    val height: Int get() = layout.height
    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset.coerceAtLeast(0))
    fun getLineTop(line: Int): Float = layout.getLineTop(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))).toFloat()
    fun getLineForVerticalPosition(y: Float): Int = layout.getLineForVertical(y.roundToInt().coerceAtLeast(0))
    fun getLineStart(line: Int): Int = layout.getLineStart(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
}

/**
 * Non-snapshot scroll model for the continuous hot path. Gesture deltas must not invalidate the
 * Compose reader tree. The attached viewport consumes offset changes as RenderNode properties.
 */
internal class ReaderContinuousScrollModel {
    var offsetPx: Float = 0f
        private set
    var maxOffsetPx: Float = 0f
        private set
    private var scrollSink: ((Float) -> Unit)? = null

    fun attachScrollSink(sink: (Float) -> Unit) {
        scrollSink = sink
        sink(offsetPx)
    }

    fun setRange(rangePx: Int) {
        maxOffsetPx = rangePx.toFloat().coerceAtLeast(0f)
        setOffset(offsetPx)
    }

    fun setOffset(value: Float) {
        val next = value.coerceIn(0f, maxOffsetPx)
        if (next == offsetPx) return
        offsetPx = next
        scrollSink?.invoke(next)
    }

    fun consumeDelta(delta: Float): Float {
        val previous = offsetPx
        setOffset(previous - delta)
        return previous - offsetPx
    }
}

/**
 * Viewport owns a tall but bounded (4K-source-window) child display list. StaticLayout is recorded
 * only when text/style changes. Scrolling updates child.translationY, which is a RenderNode property
 * and does not re-record text or invalidate the surrounding Compose tree.
 */
private class ReaderContinuousViewportView(context: Context) : ViewGroup(context) {
    private val content = ReaderContinuousTextView(context)
    private var textLayout: StaticLayout? = null
    private var offsetPx = 0f

    init {
        clipChildren = true
        addView(content)
    }

    fun setTextLayout(layout: StaticLayout, color: Int) {
        val changed = textLayout !== layout
        textLayout = layout
        content.setTextLayout(layout, color)
        if (changed) requestLayout()
    }

    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        content.translationY = -value
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        val contentHeight = (textLayout?.height ?: height).coerceAtLeast(height)
        content.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        content.translationY = -offsetPx
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

/** Continuous keeps the 4K bounded window; scroll frames move one native RenderNode only. */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    scrollableState: ScrollableState,
    scrollModel: ReaderContinuousScrollModel,
    onTextLayout: (ReaderContinuousLayout) -> Unit,
) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    val fallback = selectionMode || accessibility.isTouchExplorationEnabled
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface by resolver.resolveAsTypeface(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    )
    val resolvedColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else style.color
    val baseModifier = modifier
        .fillMaxSize()
        .clipToBounds()
        .armSelectionOnLongPress(text.text) { selectionMode = true }
    BoxWithConstraints(if (fallback) baseModifier else baseModifier.scrollable(scrollableState, Orientation.Vertical)) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val viewportHeightPx = constraints.maxHeight.coerceAtLeast(1)
        val layout by produceState<ReaderContinuousLayout?>(
            null,
            text,
            style,
            overflow,
            widthPx,
            density.density,
            density.fontScale,
            nativeTypeface,
            resolvedColor,
        ) {
            value = withContext(Dispatchers.Default) {
                ReaderContinuousLayout(buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx))
            }
        }
        LaunchedEffect(layout, viewportHeightPx) {
            layout?.let { ready ->
                scrollModel.setRange((ready.height - viewportHeightPx).coerceAtLeast(0))
                onTextLayout(ready)
            }
        }
        val ready = layout
        if (fallback) {
            val fallbackScroll = rememberScrollState(initial = scrollModel.offsetPx.roundToInt().coerceAtLeast(0))
            androidx.compose.material3.Text(
                text = text,
                modifier = Modifier.fillMaxSize().verticalScroll(fallbackScroll),
                style = style,
                overflow = overflow,
            )
        } else if (ready != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { androidContext ->
                    ReaderContinuousViewportView(androidContext).also { viewport ->
                        viewport.setTextLayout(ready.layout, resolvedColor.toArgb())
                        scrollModel.attachScrollSink(viewport::setScrollOffset)
                    }
                },
                update = { viewport ->
                    viewport.setTextLayout(ready.layout, resolvedColor.toArgb())
                    scrollModel.attachScrollSink(viewport::setScrollOffset)
                },
            )
        }
    }
}

'''
fast = fast[:start] + new_continuous + fast[end:]
fast_path.write_text(fast)

screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
screen = replace_once(screen, "import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\n", "controls graphicsLayer import")
old_top = '''        Box(
            Modifier.align(Alignment.TopCenter).offset {
                androidx.compose.ui.unit.IntOffset(0, if (controlsVisible) 0 else -READER_HIDDEN_LAYER_OFFSET_PX)
            },
        ) {
'''
new_top = '''        Box(
            Modifier.align(Alignment.TopCenter).graphicsLayer {
                translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) {
'''
screen = replace_once(screen, old_top, new_top, "top controls RenderNode visibility")
old_bottom = '''        Box(
            Modifier.align(Alignment.BottomCenter).offset {
                androidx.compose.ui.unit.IntOffset(0, if (controlsVisible) 0 else READER_HIDDEN_LAYER_OFFSET_PX)
            },
        ) {
'''
new_bottom = '''        Box(
            Modifier.align(Alignment.BottomCenter).graphicsLayer {
                translationY = if (controlsVisible) 0f else READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) {
'''
screen = replace_once(screen, old_bottom, new_bottom, "bottom controls RenderNode visibility")

function_start = screen.index("@Composable\nprivate fun ContinuousReaderPageV3(")
function_end = screen.index("\nprivate fun readerAnnotatedTextV3(", function_start)
continuous = screen[function_start:function_end]
old_scroll_state = '''    var scrollOffsetPx by remember(book.id) { mutableFloatStateOf(0f) }
    var maxScrollPx by remember(book.id) { mutableFloatStateOf(0f) }
    val scrollableState = rememberScrollableState { delta ->
        val previous = scrollOffsetPx
        val next = (previous - delta).coerceIn(0f, maxScrollPx)
        scrollOffsetPx = next
        previous - next
    }
'''
new_scroll_state = '''    val scrollModel = remember(book.id) { ReaderContinuousScrollModel() }
    val scrollableState = rememberScrollableState(scrollModel::consumeDelta)
'''
continuous = replace_once(continuous, old_scroll_state, new_scroll_state, "non-snapshot continuous scroll model")
continuous = replace_once(continuous, "        scrollOffsetPx = layout.getLineTop(line).coerceIn(0f, maxScrollPx)\n", "        scrollModel.setOffset(layout.getLineTop(line))\n", "initial continuous position")
continuous = continuous.replace("maxScrollPx > 0f", "scrollModel.maxOffsetPx > 0f")
continuous = continuous.replace("maxScrollPx - y.toFloat()", "scrollModel.maxOffsetPx - y.toFloat()")
continuous = continuous.replace("scrollOffsetPx.roundToInt()", "scrollModel.offsetPx.roundToInt()")
continuous = replace_once(
    continuous,
    "                    scrollOffsetPx = (scrollOffsetPx + deltaPx).coerceIn(0f, maxScrollPx)\n",
    "                    scrollModel.setOffset(scrollModel.offsetPx + deltaPx)\n",
    "auto-scroll RenderNode update",
)
continuous = continuous.replace("scrollOffsetPx >= maxScrollPx - 1f", "scrollModel.offsetPx >= scrollModel.maxOffsetPx - 1f")
old_text_args = '''                scrollableState = scrollableState,
                scrollOffsetPx = { scrollOffsetPx },
                onScrollRange = { range ->
                    maxScrollPx = range.toFloat().coerceAtLeast(0f)
                    scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScrollPx)
                },
                onTextLayout = { layoutResult = it },
'''
new_text_args = '''                scrollableState = scrollableState,
                scrollModel = scrollModel,
                onTextLayout = { layoutResult = it },
'''
continuous = replace_once(continuous, old_text_args, new_text_args, "continuous native viewport arguments")
if "scrollOffsetPx" in continuous or "maxScrollPx" in continuous:
    raise SystemExit("snapshot continuous offset state remains")
screen = screen[:function_start] + continuous + screen[function_end:]
screen_path.write_text(screen)

app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = app.replace("import androidx.compose.foundation.layout.offset\n", "")
app = app.replace("import androidx.compose.ui.unit.IntOffset\n", "")
app = replace_once(app, "import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\n", "panel graphicsLayer import")
old_layer = '''private fun PersistentReaderPanelLayer(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().offset {
            IntOffset(0, if (visible) 0 else READER_PANEL_HIDDEN_OFFSET_PX)
        },
    ) { content() }
}
'''
new_layer = '''private fun PersistentReaderPanelLayer(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
            alpha = if (visible) 1f else 0f
        },
    ) { content() }
}
'''
app = replace_once(app, old_layer, new_layer, "panel RenderNode visibility")
app_path.write_text(app)

verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
old_verify = '''require_literal "$fast_text" 'Canvas(Modifier.fillMaxSize())' 'continuous viewport canvas'
require_literal "$fast_text" 'canvas.translate(0f, -scrollOffsetPx().coerceIn(0f, maxOffset))' 'continuous draw-phase translation'
forbid_literal "$fast_text" 'graphicsLayer { translationY = -scrollOffsetPx()' 'oversized continuous graphics layer'
require_literal "$fast_text" 'scrollable(scrollableState, Orientation.Vertical)' 'continuous gesture layer'
'''
new_verify = '''require_literal "$fast_text" 'ReaderContinuousScrollModel' 'non-snapshot continuous scroll model'
require_literal "$fast_text" 'ReaderContinuousViewportView' 'bounded native continuous viewport'
require_literal "$fast_text" 'content.translationY = -value' 'RenderNode continuous translation'
require_literal "$fast_text" 'AndroidView(' 'native continuous host'
require_literal "$fast_text" 'scrollable(scrollableState, Orientation.Vertical)' 'continuous gesture layer'
forbid_literal "$fast_text" 'canvas.translate(0f, -scrollOffsetPx()' 'Compose per-frame text translation'
'''
verify = replace_once(verify, old_verify, new_verify, "continuous render contract")
verify = replace_once(verify, "require_literal \"$screen\" 'settleContinuousPosition(scrollOffsetPx.roundToInt(), auto = false)' 'manual continuous settle'\n", "require_literal \"$screen\" 'settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false)' 'manual continuous settle'\n", "manual settle contract")
verify = replace_once(verify, "require_literal \"$screen\" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'\n", "require_literal \"$screen\" 'graphicsLayer {' 'RenderNode reader controls'\nrequire_literal \"$screen\" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'\nrequire_literal \"$app\" 'graphicsLayer {' 'RenderNode resident panels'\n", "RenderNode overlay contract")
verify_path.write_text(verify)

print("Reader V3 RenderNode cut applied")
