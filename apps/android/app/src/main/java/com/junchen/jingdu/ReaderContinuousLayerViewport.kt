package com.junchen.jingdu

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Retained two-slice continuous viewport.
 *
 * The bounded 4K source window and worker-rasterized bitmap tile set remain authoritative. Steady
 * scroll frames do not re-record full-screen bitmap draws: two viewport-sized child display lists
 * are rebound only when crossing a viewport boundary, while normal frames update real child-layer
 * translations. There is no whole-document layer, ViewGroup scroll traversal, or synthetic pulse.
 */
internal class ReaderContinuousLayerViewport(context: Context) : ViewGroup(context) {
    private class SliceView(context: Context) : View(context) {
        private var raster: ReaderStaticLayoutBitmapTileSet? = null
        private var sliceTop = Int.MIN_VALUE
        private var sliceHeight = 0

        init { setWillNotDraw(false) }

        fun bind(nextRaster: ReaderStaticLayoutBitmapTileSet, top: Int, height: Int) {
            if (raster === nextRaster && sliceTop == top && sliceHeight == height) return
            raster = nextRaster
            sliceTop = top
            sliceHeight = height
            invalidate()
        }

        fun clearBinding() {
            raster = null
            sliceTop = Int.MIN_VALUE
            sliceHeight = 0
            visibility = INVISIBLE
        }

        override fun isOpaque(): Boolean = raster != null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val ready = raster ?: return
            ready.draw(canvas, sliceTop.coerceAtLeast(0), sliceHeight.coerceAtLeast(1))
        }
    }

    private val viewConfig = ViewConfiguration.get(context)
    private val scroller = OverScroller(context)
    private val density = resources.displayMetrics.density
    private val slices = arrayOf(SliceView(context), SliceView(context))
    private var ready: ReaderContinuousLayout? = null
    private var scrollModel: ReaderContinuousScrollModel? = null
    private var settings = ReaderSettings()
    private var systemLeftInsetPx = 0
    private var systemRightInsetPx = 0
    private var offsetPx = 0f
    private var renderedOffsetPx = 0
    private var pendingScrollY = 0
    private var scrollScheduled = false
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

    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val layout = ready?.layout
        val maxOffset = if (layout == null) 0 else (layout.height - height).coerceAtLeast(0)
        val next = pendingScrollY.coerceIn(0, maxOffset)
        if (renderedOffsetPx != next) {
            renderedOffsetPx = next
            updateRetainedSlices(next)
        }
    }
    private val flingStep = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                scrollModel?.setOffset(scroller.currY.toFloat())
                postOnAnimation(this)
            } else if (flingRunning) {
                flingRunning = false
                onScrollSettled()
            }
        }
    }
    private val longPress = Runnable {
        if (!scrolling && !pinching) {
            longPressTriggered = true
            onLongPress()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        clipChildren = true
        setWillNotDraw(true)
        slices.forEach(::addView)
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

    fun setTextLayout(next: ReaderContinuousLayout, @Suppress("UNUSED_PARAMETER") color: Int) {
        if (ready === next) return
        ready = next
        slices.forEach(SliceView::clearBinding)
        requestLayout()
        post { updateRetainedSlices(renderedOffsetPx) }
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

    private fun updateRetainedSlices(scrollY: Int) {
        val layout = ready ?: return
        if (width <= 0 || height <= 0) return
        val viewportHeight = height.coerceAtLeast(1)
        val firstSlice = (scrollY / viewportHeight).coerceAtLeast(0)
        val activeSlots = BooleanArray(slices.size)
        for (sliceIndex in firstSlice..firstSlice + 1) {
            val top = sliceIndex * viewportHeight
            if (top >= layout.height) continue
            val slotIndex = sliceIndex and 1
            val slot = slices[slotIndex]
            val sliceHeight = minOf(viewportHeight, layout.height - top).coerceAtLeast(1)
            slot.bind(layout.raster, top, sliceHeight)
            slot.visibility = VISIBLE
            slot.translationY = (top - scrollY).toFloat()
            activeSlots[slotIndex] = true
        }
        slices.forEachIndexed { index, slice -> if (!activeSlots[index]) slice.visibility = INVISIBLE }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(measuredWidth, measuredHeight)
        val childWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        slices.forEach { it.measure(childWidth, childHeight) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)
        slices.forEach { it.layout(0, 0, w, h) }
        updateRetainedSlices(renderedOffsetPx)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scroller.isFinished) scroller.abortAnimation()
                removeCallbacks(flingStep)
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
                if (scrolling) scrollModel?.let { model -> model.setOffset(model.offsetPx + (lastY - event.y)) }
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

    private fun finishScrollWithFling() {
        val model = scrollModel ?: return onScrollSettled()
        val tracker = velocityTracker ?: return onScrollSettled()
        tracker.computeCurrentVelocity(1000, viewConfig.scaledMaximumFlingVelocity.toFloat())
        val velocity = (-tracker.yVelocity).roundToInt()
        if (abs(velocity) >= viewConfig.scaledMinimumFlingVelocity && model.maxOffsetPx > 0f) {
            scroller.fling(0, model.offsetPx.roundToInt(), 0, velocity, 0, 0, 0, model.maxOffsetPx.roundToInt())
            flingRunning = true
            postOnAnimation(flingStep)
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

    override fun onDetachedFromWindow() {
        removeCallbacks(applyPendingScroll)
        removeCallbacks(flingStep)
        scrollScheduled = false
        super.onDetachedFromWindow()
    }
}
