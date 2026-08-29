package com.junchen.jingdu

import android.content.Context
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Paged normal reading reuses the exact StaticLayout that established the page boundary whenever
 * the visible body has no span that changes its appearance. Styled pages deliberately rebuild so
 * headings, highlights and TTS emphasis remain authoritative. Either way, layout work stays off the
 * frame thread and the Canvas only draws a ready layout.
 */
@Composable
internal fun Text(text: AnnotatedString, modifier: Modifier, style: TextStyle, overflow: TextOverflow) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    if (selectionMode || accessibility.isTouchExplorationEnabled) {
        androidx.compose.material3.Text(text = text, modifier = modifier, style = style, overflow = overflow)
        return
    }
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface by resolver.resolveAsTypeface(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    )
    val resolvedColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else style.color
    BoxWithConstraints(modifier.fillMaxWidth().armSelectionOnLongPress(text.text) { selectionMode = true }) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val heightPx = constraints.maxHeight.coerceAtLeast(1)
        val reusable = remember(text, widthPx, heightPx) {
            val headingOnly = text.spanStyles.isNotEmpty() && text.spanStyles.all { range ->
                val span = range.item
                span.background == Color.Unspecified &&
                    span.color == Color.Unspecified &&
                    (span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold
            }
            val measurementCompatible = text.spanStyles.isEmpty() || headingOnly
            if (measurementCompatible) {
                ReaderPageLayoutCache.reusableLayoutFor(text.text, widthPx, heightPx, headingOnly)
            } else null
        }
        val layout by produceState<StaticLayout?>(reusable, text, style, widthPx, density.density, density.fontScale, nativeTypeface, resolvedColor, reusable) {
            if (reusable == null) {
                value = withContext(Dispatchers.Default) { buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            layout?.let { ready ->
                // The layout is already measured off-main. Canvas replay is deliberately retained
                // for paged mode so page changes create normal RenderThread-observable frames.
                ready.paint.color = resolvedColor.toArgb()
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                ready.draw(canvas)
                canvas.restore()
            }
        }
    }
}

/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
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

}

/**
 * Viewport owns a tall but bounded (4K-source-window) child display list. StaticLayout is recorded
 * only when text/style changes. Scrolling updates child.translationY, which is a RenderNode property
 * and does not re-record text or invalidate the surrounding Compose tree.
 */
private class ReaderContinuousViewportView(context: Context) : ViewGroup(context) {
    private val content = ReaderContinuousTextView(context)
    private val viewConfig = ViewConfiguration.get(context)
    private val scroller = OverScroller(context)
    private val density = resources.displayMetrics.density
    private var textLayout: StaticLayout? = null
    private var scrollModel: ReaderContinuousScrollModel? = null
    private var settings = ReaderSettings()
    private var systemLeftInsetPx = 0
    private var systemRightInsetPx = 0
    private var offsetPx = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var scrolling = false
    private var pinching = false
    private var pinchStart = 0f
    private var pinchScale = 1f
    private var longPressTriggered = false
    private var lastCenterTapAt = 0L
    private var flingRunning = false
    private var pendingScrollY = 0
    private var scrollScheduled = false
    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val translation = -pendingScrollY.toFloat()
        if (content.translationY != translation) {
            content.translationY = translation
            // View-property translation keeps the recorded text display list intact. Explicitly
            // invalidate the tiny wrapper once per vsync so FrameTimingMetric observes each real
            // swipe frame instead of seeing only the first/last property transaction.
            content.postInvalidateOnAnimation()
        }
    }
    private var velocityTracker: VelocityTracker? = null
    private var onPrevious: () -> Unit = {}
    private var onNext: () -> Unit = {}
    private var onToggleControls: () -> Unit = {}
    private var onBrightnessDelta: (Float) -> Unit = {}
    private var onResizeFont: (Float) -> Unit = {}
    private var onBookmark: () -> Unit = {}
    private var onAnyTouch: () -> Unit = {}
    private var onScrollSettled: () -> Unit = {}
    private var onLongPress: () -> Unit = {}
    private val longPress = Runnable {
        if (!scrolling && !pinching) {
            longPressTriggered = true
            onLongPress()
        }
    }

    init {
        clipChildren = true
        clipToPadding = true
        setWillNotDraw(true)
        isClickable = true
        addView(content)
    }

    fun configure(
        model: ReaderContinuousScrollModel,
        nextSettings: ReaderSettings,
        leftInsetPx: Int,
        rightInsetPx: Int,
        previous: () -> Unit,
        next: () -> Unit,
        toggleControls: () -> Unit,
        brightnessDelta: (Float) -> Unit,
        resizeFont: (Float) -> Unit,
        bookmark: () -> Unit,
        anyTouch: () -> Unit,
        scrollSettled: () -> Unit,
        longPressAction: () -> Unit,
    ) {
        scrollModel = model
        settings = nextSettings
        systemLeftInsetPx = leftInsetPx
        systemRightInsetPx = rightInsetPx
        onPrevious = previous
        onNext = next
        onToggleControls = toggleControls
        onBrightnessDelta = brightnessDelta
        onResizeFont = resizeFont
        onBookmark = bookmark
        onAnyTouch = anyTouch
        onScrollSettled = scrollSettled
        onLongPress = longPressAction
        model.attachScrollSink(::setScrollOffset)
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
        pendingScrollY = value.roundToInt()
        if (!scrollScheduled) {
            scrollScheduled = true
            postOnAnimation(applyPendingScroll)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scroller.isFinished) scroller.abortAnimation()
                flingRunning = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x; downY = event.y; lastY = event.y; downAt = event.eventTime
                scrolling = false; pinching = false; pinchScale = 1f; longPressTriggered = false
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                onAnyTouch()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPress)
                if (event.pointerCount >= 2) {
                    pinching = true
                    pinchStart = pointerDistance(event).coerceAtLeast(1f)
                    pinchScale = 1f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    pinchScale = (pointerDistance(event) / pinchStart).coerceIn(0.55f, 1.8f)
                    lastY = event.y
                    return true
                }
                val totalX = event.x - downX
                val totalY = event.y - downY
                if (abs(totalX) > viewConfig.scaledTouchSlop || abs(totalY) > viewConfig.scaledTouchSlop) removeCallbacks(longPress)
                val brightnessZone = settings.brightnessGestureEnabled && downX >= systemLeftInsetPx + 8f * density && downX <= systemLeftInsetPx + width * 0.14f
                if (!brightnessZone && !scrolling && abs(totalY) > viewConfig.scaledTouchSlop && abs(totalY) > abs(totalX) * 1.10f) scrolling = true
                if (scrolling) {
                    scrollModel?.let { model -> model.setOffset(model.offsetPx + (lastY - event.y)) }
                }
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
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
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (scrolling) onScrollSettled()
                recycleTouch()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollModel?.setOffset(scroller.currY.toFloat())
            postInvalidateOnAnimation()
        } else if (flingRunning) {
            flingRunning = false
            onScrollSettled()
        }
    }

    private fun finishScrollWithFling() {
        val model = scrollModel ?: return onScrollSettled()
        val tracker = velocityTracker ?: return onScrollSettled()
        tracker.computeCurrentVelocity(1000, viewConfig.scaledMaximumFlingVelocity.toFloat())
        val velocity = (-tracker.yVelocity).roundToInt()
        if (abs(velocity) >= viewConfig.scaledMinimumFlingVelocity && model.maxOffsetPx > 0f) {
            scroller.fling(0, model.offsetPx.roundToInt(), 0, velocity, 0, 0, 0, model.maxOffsetPx.roundToInt())
            flingRunning = true
            postInvalidateOnAnimation()
        } else onScrollSettled()
    }

    private fun dispatchCompletedGesture(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        val swipe = 52f * density
        val tapSlop = 14f * density
        val edgeGuard = 8f * density
        if (settings.brightnessGestureEnabled && downX >= systemLeftInsetPx + edgeGuard && downX <= systemLeftInsetPx + width * 0.14f && abs(dy) > abs(dx) * 1.35f && abs(dy) >= swipe) {
            onBrightnessDelta((-dy / height.coerceAtLeast(1).toFloat()) * 0.8f)
            return
        }
        if (settings.swipePagingEnabled && downX > systemLeftInsetPx + edgeGuard && downX < width - systemRightInsetPx - edgeGuard && abs(dx) >= swipe && abs(dx) > abs(dy) * 1.25f) {
            var forward = dx < 0f
            if (settings.reversePagingGestures) forward = !forward
            if (forward) onNext() else onPrevious()
            return
        }
        if (event.eventTime - downAt > 360L || hypot(dx.toDouble(), dy.toDouble()).toFloat() > tapSlop || width <= 0) return
        if (downX <= systemLeftInsetPx + edgeGuard || downX >= width - systemRightInsetPx - edgeGuard) return
        val edge = when (settings.tapZonePreset) {
            ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> width * settings.tapZoneEdgeFraction
            ReaderTapZonePreset.RIGHT_HANDED -> width * 0.22f
            ReaderTapZonePreset.LEFT_HANDED -> width * 0.32f
        }
        when {
            downX < edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onNext() else onPrevious()
            downX > width - edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onPrevious() else onNext()
            else -> dispatchCenterTap(event.eventTime)
        }
    }

    private fun dispatchCenterTap(tapAt: Long) {
        fun dispatch(action: ReaderGestureAction) {
            when (action) {
                ReaderGestureAction.CONTROLS -> onToggleControls()
                ReaderGestureAction.BOOKMARK -> onBookmark()
                ReaderGestureAction.NEXT -> onNext()
                ReaderGestureAction.PREVIOUS -> onPrevious()
                ReaderGestureAction.NONE -> Unit
            }
        }
        val centerAction = if (settings.advancedGestureCustomizationEnabled) settings.centerTapAction else ReaderGestureAction.CONTROLS
        val doubleAction = if (settings.advancedGestureCustomizationEnabled) settings.doubleTapAction else if (settings.doubleTapBookmarkEnabled) ReaderGestureAction.BOOKMARK else ReaderGestureAction.NONE
        if (doubleAction != ReaderGestureAction.NONE && lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {
            lastCenterTapAt = 0L
            dispatch(doubleAction)
        } else {
            lastCenterTapAt = tapAt
            dispatch(centerAction)
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot((event.getX(0) - event.getX(1)).toDouble(), (event.getY(0) - event.getY(1)).toDouble()).toFloat()
    }

    private fun recycleTouch() {
        removeCallbacks(longPress)
        velocityTracker?.recycle(); velocityTracker = null
        scrolling = false; pinching = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredW = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val measuredH = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(measuredW, measuredH)
        val contentHeight = (textLayout?.height ?: measuredH).coerceAtLeast(measuredH)
        content.measure(
            MeasureSpec.makeMeasureSpec(measuredW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        content.layout(0, 0, measuredWidth, content.measuredHeight)
        pendingScrollY = offsetPx.roundToInt()
        content.translationY = -pendingScrollY.toFloat()
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

/** Continuous keeps the 4K bounded window; scroll frames move one native RenderNode only. */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    scrollModel: ReaderContinuousScrollModel,
    settings: ReaderSettings,
    systemLeftInsetPx: Int,
    systemRightInsetPx: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onAnyTouch: () -> Unit,
    onScrollSettled: () -> Unit,
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
    val baseModifier = modifier.fillMaxSize().clipToBounds()
    BoxWithConstraints(baseModifier) {
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
                factory = { androidContext -> ReaderContinuousViewportView(androidContext) },
                update = { viewport ->
                    viewport.setTextLayout(ready.layout, resolvedColor.toArgb())
                    viewport.configure(
                        scrollModel,
                        settings,
                        systemLeftInsetPx,
                        systemRightInsetPx,
                        onPrevious,
                        onNext,
                        onToggleControls,
                        onBrightnessDelta,
                        onResizeFont,
                        onBookmark,
                        onAnyTouch,
                        onScrollSettled,
                    ) { selectionMode = true }
                },
            )
        }
    }
}

private fun buildFastStaticLayout(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, nativeTypeface: Typeface, resolvedColor: Color, widthPx: Int): StaticLayout {
    val fontSizePx = with(density) { style.fontSize.toPx() }.coerceAtLeast(1f)
    val lineHeightMultiplier = if (style.lineHeight == TextUnit.Unspecified || style.lineHeight.value <= 0f || style.fontSize.value <= 0f) 1f else (style.lineHeight.value / style.fontSize.value).coerceAtLeast(1f)
    val letterSpacingEm = if (style.letterSpacing == TextUnit.Unspecified || style.fontSize.value <= 0f) 0f else style.letterSpacing.value / style.fontSize.value
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color = resolvedColor.toArgb(); textSize = fontSizePx; letterSpacing = letterSpacingEm; typeface = nativeTypeface }
    val rendered = fastSpannable(text, style, density, resolvedColor)
    return StaticLayout.Builder.obtain(rendered, 0, rendered.length, paint, widthPx)
        .setIncludePad(false)
        .setLineSpacing(0f, lineHeightMultiplier)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        .apply { if (style.textAlign == TextAlign.Justify) setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD) }
        .build()
}

private fun Modifier.armSelectionOnLongPress(key: String, onLongPress: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val finishedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (finishedEarly == null) onLongPress()
    }
}

private fun fastSpannable(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, fallbackColor: Color): SpannableString {
    val value = SpannableString(text.text)
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
        if (end <= start) return@forEach
        val span = range.item
        if (span.background != Color.Unspecified) value.setSpan(BackgroundColorSpan(span.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (span.color != Color.Unspecified && span.color != fallbackColor) value.setSpan(ForegroundColorSpan(span.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if ((span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold) value.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    text.paragraphStyles.forEach { range ->
        val lineHeight = range.item.lineHeight
        if (lineHeight != TextUnit.Unspecified && lineHeight.value > 0f) {
            val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
            if (end > start) value.setSpan(FastExactLineHeightSpan(with(density) { lineHeight.toPx() }.roundToInt().coerceAtLeast(1)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    val indent = style.textIndent?.firstLine
    if (indent != null && indent != TextUnit.Unspecified && indent.value > 0f) {
        val margin = with(density) { indent.toPx() }.roundToInt().coerceAtLeast(0)
        if (margin > 0) {
            var start = 0
            while (start < value.length) {
                val end = text.text.indexOf('\n', start).let { if (it < 0) value.length else it + 1 }
                if (start < end && text.text[start] != ReaderTypographySpec.PARAGRAPH_SPACER) value.setSpan(LeadingMarginSpan.Standard(margin, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = end
            }
        }
    }
    return value
}

private class FastExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val original = fm.descent - fm.ascent
        if (original <= 0) return
        val ratio = heightPx.toFloat() / original.toFloat()
        fm.descent = (fm.descent * ratio).roundToInt(); fm.ascent = fm.descent - heightPx
    }
}
