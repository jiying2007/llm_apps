from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)


# 1) Continuous: replace artificial frame pulses and the tall translated child with a real,
# viewport-sized renderer. StaticLayout stays bounded by the 4K source window, but is pre-recorded
# into viewport-height RenderNode tiles; each real scroll frame redraws only the visible 1-2 tiles.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
start = fast.index("/**\n * A one-pixel compositor pulse")
end_marker = "/** Continuous keeps the 4K bounded window; scroll frames move one native RenderNode only. */"
end = fast.index(end_marker)
replacement = r'''/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
internal class ReaderContinuousLayout internal constructor(internal val layout: StaticLayout) {
    val lineCount: Int get() = layout.lineCount
    val height: Int get() = layout.height
    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset.coerceAtLeast(0))
    fun getLineTop(line: Int): Float = layout.getLineTop(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))).toFloat()
    fun getLineForVerticalPosition(y: Float): Int = layout.getLineForVertical(y.roundToInt().coerceAtLeast(0))
    fun getLineStart(line: Int): Int = layout.getLineStart(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
}

/**
 * Non-snapshot scroll model for the continuous hot path. Gesture deltas do not invalidate Compose;
 * the attached native viewport consumes offsets at most once per vsync.
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
 * Viewport-sized native continuous renderer. The 4K source window remains authoritative and
 * bounded, but its StaticLayout is split into viewport-height RenderNode tiles. A scroll frame
 * invalidates only this viewport and replays at most the two tiles intersecting the viewport,
 * avoiding the previous whole-window display-list replay and any synthetic measurement pulse.
 */
private class ReaderContinuousViewportView(context: Context) : View(context) {
    private val viewConfig = ViewConfiguration.get(context)
    private val scroller = OverScroller(context)
    private val density = resources.displayMetrics.density
    private var textLayout: StaticLayout? = null
    private var tileSet: ReaderStaticLayoutTileSet? = null
    private var textColor: Int = 0
    private var scrollModel: ReaderContinuousScrollModel? = null
    private var settings = ReaderSettings()
    private var systemLeftInsetPx = 0
    private var systemRightInsetPx = 0
    private var offsetPx = 0f
    private var renderedOffsetPx = 0
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
        val next = pendingScrollY.coerceAtLeast(0)
        if (renderedOffsetPx != next) {
            renderedOffsetPx = next
            postInvalidateOnAnimation()
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
        isClickable = true
        isFocusable = true
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
        val changed = textLayout !== layout || textColor != color
        textLayout = layout
        textColor = color
        layout.paint.color = color
        if (changed) {
            rebuildTiles()
            postInvalidateOnAnimation()
        }
    }

    private fun rebuildTiles() {
        val layout = textLayout
        tileSet = if (Build.VERSION.SDK_INT >= 29 && layout != null && width > 0 && height > 0) {
            ReaderStaticLayoutTileSet(layout, height.coerceAtLeast(1))
        } else null
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) rebuildTiles()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val layout = textLayout ?: return
        val maxOffset = (layout.height - height).coerceAtLeast(0)
        val scrollY = renderedOffsetPx.coerceIn(0, maxOffset)
        if (Build.VERSION.SDK_INT >= 29 && tileSet?.draw(canvas, scrollY, height) == true) return
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(0f, -scrollY.toFloat())
        layout.draw(canvas)
        canvas.restore()
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

    override fun onDetachedFromWindow() {
        removeCallbacks(applyPendingScroll)
        scrollScheduled = false
        super.onDetachedFromWindow()
    }
}

@android.annotation.TargetApi(29)
private class ReaderStaticLayoutTileSet(layout: StaticLayout, private val tileHeightPx: Int) {
    private data class Tile(val top: Int, val height: Int, val node: RenderNode)
    private val tiles: List<Tile>

    init {
        val width = layout.width.coerceAtLeast(1)
        val totalHeight = layout.height.coerceAtLeast(1)
        val built = ArrayList<Tile>((totalHeight + tileHeightPx - 1) / tileHeightPx)
        var top = 0
        while (top < totalHeight) {
            val tileHeight = minOf(tileHeightPx, totalHeight - top).coerceAtLeast(1)
            val node = RenderNode("ReaderContinuousTile@$top")
            node.setPosition(0, 0, width, tileHeight)
            val canvas = node.beginRecording(width, tileHeight)
            try {
                canvas.save()
                canvas.clipRect(0, 0, width, tileHeight)
                canvas.translate(0f, -top.toFloat())
                layout.draw(canvas)
                canvas.restore()
            } finally {
                node.endRecording()
            }
            built += Tile(top, tileHeight, node)
            top += tileHeight
        }
        tiles = built
    }

    fun draw(canvas: android.graphics.Canvas, scrollY: Int, viewportHeight: Int): Boolean {
        if (!canvas.isHardwareAccelerated || tiles.isEmpty()) return false
        val first = (scrollY / tileHeightPx).coerceIn(0, tiles.lastIndex)
        val last = ((scrollY + viewportHeight.coerceAtLeast(1) - 1) / tileHeightPx).coerceIn(first, tiles.lastIndex)
        for (index in first..last) {
            val tile = tiles[index]
            if (!tile.node.hasDisplayList()) return false
            canvas.save()
            canvas.translate(0f, (tile.top - scrollY).toFloat())
            canvas.drawRenderNode(tile.node)
            canvas.restore()
        }
        return true
    }
}

/** Continuous keeps the 4K bounded window; real scroll frames replay only visible RenderNode tiles. */
'''
fast = fast[:start] + replacement + fast[end + len(end_marker):]
fast_path.write_text(fast)


# 2) All page-turn inputs share one state-owned direction. This makes hardware volume paging use the
# same product slide path as touch paging rather than a benchmark-only animation.
models_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt")
models = models_path.read_text()
models = replace_once(
    models,
    "    val position: Long = 0,\n    val length: Long = 0,\n",
    "    val position: Long = 0,\n    val pageTurnDirection: Int = 0,\n    val length: Long = 0,\n",
    "page turn direction model",
)
models_path.write_text(models)

activity_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt")
activity = activity_path.read_text()
activity = replace_once(activity, "    private fun render() {\n", "    private fun render(pageTurnDirection: Int = 0) {\n", "render direction signature")
activity = replace_once(
    activity,
    "            val position = reader.position()\n            val length = reader.length()\n",
    "            val position = reader.position()\n            val length = reader.length()\n            ReaderInteractionRuntime.foregroundPosition = position\n",
    "render foreground position",
)
activity = replace_once(
    activity,
    "                screen = AppScreen.READER, currentBook = card, pageText = text,\n                position = position, length = length, cleanMode = cleanMode,\n",
    "                screen = AppScreen.READER, currentBook = card, pageText = text,\n                position = position, pageTurnDirection = pageTurnDirection, length = length, cleanMode = cleanMode,\n",
    "render direction publication",
)
activity = replace_once(
    activity,
    "        uiState = uiState.copy(currentBook = card, position = position, length = length)\n",
    "        ReaderInteractionRuntime.foregroundPosition = position\n        uiState = uiState.copy(currentBook = card, position = position, pageTurnDirection = 0, length = length)\n",
    "position-only runtime publication",
)
activity = replace_once(
    activity,
    "        reader.move(visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))\n        render()\n    }\n\n    private fun navigatePrevious",
    "        reader.move(visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))\n        render(pageTurnDirection = 1)\n    }\n\n    private fun navigatePrevious",
    "next page direction",
)
activity = replace_once(
    activity,
    "        if (pageHistory.isNotEmpty()) reader.jump(pageHistory.removeLast()) else reader.move(-visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))\n        render()\n    }\n\n    private fun seekFraction",
    "        if (pageHistory.isNotEmpty()) reader.jump(pageHistory.removeLast()) else reader.move(-visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))\n        render(pageTurnDirection = -1)\n    }\n\n    private fun seekFraction",
    "previous page direction",
)
activity_path.write_text(activity)

experience_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderExperience.kt")
experience = experience_path.read_text()
experience = replace_once(
    experience,
    "internal object ReaderInteractionRuntime {\n    @Volatile var backgroundTtsPlaying: Boolean = false\n",
    "internal object ReaderInteractionRuntime {\n    @Volatile var backgroundTtsPlaying: Boolean = false\n    @Volatile var foregroundPosition: Long = -1L\n",
    "runtime foreground position",
)
experience_path.write_text(experience)

screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
screen = replace_once(screen, "    val settings = state.settings\n", "    val settings = state.settings\n    val pageDirection = state.pageTurnDirection\n", "state-owned page direction")
screen = replace_once(screen, "    var pageDirection by remember(book.id) { mutableIntStateOf(0) }\n", "", "remove local page direction")
screen = replace_once(
    screen,
    "        pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION) -1 else 0\n        actions.onNavigatePrevious()\n",
    "        actions.onNavigatePrevious()\n",
    "previous local direction",
)
screen = replace_once(
    screen,
    "        pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION) 1 else 0\n        actions.onNavigateNext()\n",
    "        actions.onNavigateNext()\n",
    "next local direction",
)
screen = replace_once(screen, "            ReaderPageFramePulse(sourceStart, Modifier.align(Alignment.TopStart))\n", "", "remove paged pulse")
screen = replace_once(screen, "    LaunchedEffect(state.position) { if (pageDirection != 0) { delay(220); pageDirection = 0 } }\n", "", "remove local direction reset")
old_guard = '''        if (consumedByChild || maxPointers > 1) return@awaitEachGesture
        val delta = last.position - down.position
        val duration = last.uptimeMillis - down.uptimeMillis
        val edgeGuard = 8.dp.toPx()
        if (settings.brightnessGestureEnabled && widthPx > 0 && down.position.x >= systemLeftInsetPx + edgeGuard && down.position.x <= systemLeftInsetPx + widthPx * 0.14f && abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe) {
            onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f); return@awaitEachGesture
        }
        if (settings.swipePagingEnabled && down.position.x > systemLeftInsetPx + edgeGuard && down.position.x < widthPx - systemRightInsetPx - edgeGuard && abs(delta.x) >= swipe && abs(delta.x) > abs(delta.y) * 1.25f) {
'''
new_guard = '''        if (maxPointers > 1) return@awaitEachGesture
        val delta = last.position - down.position
        val duration = last.uptimeMillis - down.uptimeMillis
        val edgeGuard = 8.dp.toPx()
        // SelectionContainer consumes the pointer stream even for a short tap. Preserve consumed
        // drags/selection, but allow a completed short tap to reach the reader's paging/control zones.
        if (!consumedByChild && settings.brightnessGestureEnabled && widthPx > 0 && down.position.x >= systemLeftInsetPx + edgeGuard && down.position.x <= systemLeftInsetPx + widthPx * 0.14f && abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe) {
            onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f); return@awaitEachGesture
        }
        if (!consumedByChild && settings.swipePagingEnabled && down.position.x > systemLeftInsetPx + edgeGuard && down.position.x < widthPx - systemRightInsetPx - edgeGuard && abs(delta.x) >= swipe && abs(delta.x) > abs(delta.y) * 1.25f) {
'''
screen = replace_once(screen, old_guard, new_guard, "selection-aware reader gesture guard")
screen_path.write_text(screen)


# 3) Benchmark-build fixture is deterministic across every interaction-relevant preference, and can
# query the authoritative in-process reader position to prove volume-key injection actually paged.
fixture_path = Path("apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt")
fixture = fixture_path.read_text()
fixture = replace_once(
    fixture,
    "                        readingMode = mode,\n                        autoScrollEnabled = false,\n",
    "                        readingMode = mode,\n                        pageAnimation = ReaderPageAnimation.SLIDE,\n                        tapPagingEnabled = true,\n                        swipePagingEnabled = true,\n                        reversePagingGestures = false,\n                        autoScrollEnabled = false,\n",
    "deterministic page interaction settings",
)
fixture = replace_once(
    fixture,
    "            \"clear\" -> {\n",
    "            \"position\" -> Bundle().apply { putLong(\"position\", ReaderInteractionRuntime.foregroundPosition) }\n            \"clear\" -> {\n",
    "benchmark position query",
)
fixture_path.write_text(fixture)

journey_path = Path("apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt")
journey = journey_path.read_text()
old_page = '''    ) {
        repeat(6) {
            check(device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)) { "Reader V3 volume-key page turn was not injected" }
            device.waitForIdle()
        }
    }

    @Test fun continuousScroll10MiB()'''
new_page = '''    ) {
        val before = readerPosition()
        repeat(6) {
            check(device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)) { "Reader V3 volume-key page turn was not injected" }
            device.waitForIdle()
        }
        val after = readerPosition()
        check(before >= 0 && after > before) {
            "Reader V3 volume-key journey injected keys but did not advance the authoritative reader position: before=$before after=$after"
        }
    }

    @Test fun continuousScroll10MiB()'''
journey = replace_once(journey, old_page, new_page, "page turn position assertion")
insert_after_mode = '''    private fun MacrobenchmarkScope.setReaderMode(mode: String) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("Result: Bundle[{}]")) { "Reader V3 benchmark mode setup failed: $result" }
    }
'''
position_helper = insert_after_mode + '''
    private fun MacrobenchmarkScope.readerPosition(): Long {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method position",
        )
        return Regex("position=(-?\\d+)").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader V3 benchmark position query failed: $result")
    }
'''
journey = replace_once(journey, insert_after_mode, position_helper, "benchmark position helper")
journey_path.write_text(journey)


# 4) Update the permanent V3 contract: forbid synthetic frame pulses, require real tiled viewport
# rendering, state-owned page direction, deterministic benchmark interactions and actual page advance.
verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = replace_once(
    verify,
    "require_literal \"$screen\" 'pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE' 'slide direction guard'\n",
    "require_literal \"$screen\" 'val pageDirection = state.pageTurnDirection' 'state-owned slide direction'\nrequire_literal \"$activity\" 'render(pageTurnDirection = 1)' 'next page direction publication'\nrequire_literal \"$activity\" 'render(pageTurnDirection = -1)' 'previous page direction publication'\nrequire_literal apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt 'val pageTurnDirection: Int = 0' 'page direction UI state'\n",
    "page direction contract",
)
old_fast_contract = '''require_literal "$fast_text" 'ReaderStaticLayoutRenderNode' 'continuous RenderNode display list'
require_literal "$fast_text" 'node.beginRecording(width, height)' 'continuous StaticLayout pre-record'
require_literal "$fast_text" 'canvas.drawRenderNode(node)' 'continuous display-list replay'
forbid_literal "$fast_text" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'oversized rasterized continuous layer'
forbid_literal "$fast_text" 'content.buildLayer()' 'oversized raster layer prebuild'
require_literal "$fast_text" 'content.translationY = translation' 'continuous child RenderNode translation'
require_literal "$fast_text" 'ReaderFramePulseView' 'independent compositor pulse view'
require_literal "$fast_text" 'framePulse.pulse()' 'continuous tiny compositor pulse'
forbid_literal "$fast_text" 'content.postInvalidateOnAnimation()' 'full continuous text redraw probe'
require_literal "$screen" 'ReaderPageFramePulse(sourceStart' 'paged authoritative commit pulse'
forbid_literal "$fast_text" 'scrollTo(0, pendingScrollY)' 'continuous ViewGroup scroll traversal'
'''
new_fast_contract = '''require_literal "$fast_text" 'ReaderStaticLayoutTileSet' 'continuous viewport RenderNode tiles'
require_literal "$fast_text" 'node.beginRecording(width, tileHeight)' 'continuous tile pre-record'
require_literal "$fast_text" 'tileSet?.draw(canvas, scrollY, height)' 'visible continuous tile replay'
require_literal "$fast_text" 'canvas.drawRenderNode(tile.node)' 'continuous tile display-list replay'
require_literal "$fast_text" 'renderedOffsetPx = next' 'vsync-coalesced viewport offset'
require_literal "$fast_text" 'postInvalidateOnAnimation()' 'real viewport frame invalidation'
forbid_literal "$fast_text" 'ReaderFramePulseView' 'synthetic compositor pulse residue'
forbid_literal "$fast_text" 'ReaderPageFramePulse' 'synthetic paged pulse residue'
forbid_literal "$fast_text" 'content.translationY = translation' 'whole-window translated child'
forbid_literal "$fast_text" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'oversized rasterized continuous layer'
forbid_literal "$fast_text" 'content.buildLayer()' 'oversized raster layer prebuild'
forbid_literal "$fast_text" 'scrollTo(0, pendingScrollY)' 'continuous ViewGroup scroll traversal'
'''
verify = replace_once(verify, old_fast_contract, new_fast_contract, "continuous tiled contract")
verify = replace_once(
    verify,
    "require_literal \"$fixture\" 'advancedGestureCustomizationEnabled = false' 'deterministic fixture gestures'\n",
    "require_literal \"$fixture\" 'pageAnimation = ReaderPageAnimation.SLIDE' 'deterministic real page animation'\nrequire_literal \"$fixture\" 'tapPagingEnabled = true' 'deterministic tap paging'\nrequire_literal \"$fixture\" 'swipePagingEnabled = true' 'deterministic swipe paging'\nrequire_literal \"$fixture\" 'advancedGestureCustomizationEnabled = false' 'deterministic fixture gestures'\n",
    "fixture interaction contract",
)
verify = replace_once(
    verify,
    "require_literal \"$journey\" 'KEYCODE_VOLUME_DOWN' 'real page-turn input'\n",
    "require_literal \"$journey\" 'KEYCODE_VOLUME_DOWN' 'real page-turn input'\nrequire_literal \"$journey\" 'val before = readerPosition()' 'page-turn authoritative start position'\nrequire_literal \"$journey\" 'after > before' 'page-turn authoritative advance assertion'\nrequire_literal \"$fixture\" 'ReaderInteractionRuntime.foregroundPosition' 'benchmark authoritative position source'\n",
    "page-turn journey contract",
)
verify = replace_once(
    verify,
    "require_literal \"$screen\" 'WindowInsets.systemGestures' 'system gesture arbitration'\n",
    "require_literal \"$screen\" 'WindowInsets.systemGestures' 'system gesture arbitration'\nrequire_literal \"$screen\" 'if (maxPointers > 1) return@awaitEachGesture' 'selection-safe pointer arbitration'\nrequire_literal \"$screen\" '!consumedByChild && settings.swipePagingEnabled' 'consumed drag protection'\n",
    "gesture arbitration contract",
)
verify_path.write_text(verify)

for path in [fast_path, models_path, activity_path, experience_path, screen_path, fixture_path, journey_path, verify_path]:
    text = path.read_text()
    if "ReaderFramePulseView" in text and path == fast_path:
        raise SystemExit("synthetic frame pulse survived")

print("Reader V3 real frame path cut applied")
