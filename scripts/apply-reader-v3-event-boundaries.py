#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)

# 1) Continuous native gesture pipeline: no Compose scrollable/pointer-input on the hot path.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = replace_once(
    fast,
    "import android.view.View\nimport android.view.ViewGroup\n",
    "import android.view.MotionEvent\nimport android.view.VelocityTracker\nimport android.view.View\nimport android.view.ViewConfiguration\nimport android.view.ViewGroup\nimport android.widget.OverScroller\n",
    "native gesture imports",
)
for stale in (
    "import androidx.compose.foundation.gestures.Orientation\n",
    "import androidx.compose.foundation.gestures.ScrollableState\n",
    "import androidx.compose.foundation.gestures.scrollable\n",
):
    fast = fast.replace(stale, "")
fast = replace_once(fast, "import kotlin.math.roundToInt\n", "import kotlin.math.abs\nimport kotlin.math.hypot\nimport kotlin.math.roundToInt\n", "math imports")

old_viewport = '''private class ReaderContinuousViewportView(context: Context) : ViewGroup(context) {
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
'''
new_viewport = '''private class ReaderContinuousViewportView(context: Context) : ViewGroup(context) {
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
        content.translationY = -value
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
                if (pinching && settings.pinchFontEnabled && abs(pinchScale - 1f) >= 0.04f) onResizeFont(pinchScale)
                else if (!longPressTriggered && !handledScroll) dispatchCompletedGesture(event)
                if (handledScroll) finishScrollWithFling()
                recycleTouch()
                performClick()
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
        content.translationY = -offsetPx
    }
}
'''
fast = replace_once(fast, old_viewport, new_viewport, "native continuous viewport")

fast = replace_once(
    fast,
    '''    overflow: TextOverflow,
    scrollableState: ScrollableState,
    scrollModel: ReaderContinuousScrollModel,
    onTextLayout: (ReaderContinuousLayout) -> Unit,
) {''',
    '''    overflow: TextOverflow,
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
) {''',
    "continuous Text signature",
)
fast = replace_once(
    fast,
    '''    val baseModifier = modifier
        .fillMaxSize()
        .clipToBounds()
        .armSelectionOnLongPress(text.text) { selectionMode = true }
    BoxWithConstraints(if (fallback) baseModifier else baseModifier.scrollable(scrollableState, Orientation.Vertical)) {''',
    '''    val baseModifier = modifier.fillMaxSize().clipToBounds()
    BoxWithConstraints(baseModifier) {''',
    "continuous Compose pointer hot path",
)
old_android_view = '''            AndroidView(
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
            )'''
new_android_view = '''            AndroidView(
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
            )'''
fast = replace_once(fast, old_android_view, new_android_view, "AndroidView native gesture configuration")
fast_path.write_text(fast)

# 2) Continuous caller + controlsVisible state reads stay out of composition hot path.
screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
screen = screen.replace("import androidx.compose.foundation.gestures.rememberScrollableState\n", "")
screen = screen.replace("import androidx.compose.foundation.gestures.scrollBy\n", "")
screen = replace_once(screen, "import kotlinx.coroutines.flow.collect\n", "import kotlinx.coroutines.flow.MutableSharedFlow\nimport kotlinx.coroutines.flow.collect\nimport kotlinx.coroutines.flow.collectLatest\n", "flow imports")
screen = replace_once(
    screen,
    '''    LaunchedEffect(activity, controlsVisible, state.panel) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible || state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(controlsVisible, state.panel, settings.controlsAutoHideMs) {
        if (controlsVisible && state.panel == null) {
            delay(settings.controlsAutoHideMs)
            controlsVisible = false
        }
    }''',
    '''    LaunchedEffect(activity, state.panel) {
        snapshotFlow { controlsVisible }.distinctUntilChanged().collect { visible ->
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (visible || state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(state.panel, settings.controlsAutoHideMs) {
        snapshotFlow { controlsVisible }.distinctUntilChanged().collectLatest { visible ->
            if (visible && state.panel == null) {
                delay(settings.controlsAutoHideMs)
                controlsVisible = false
            }
        }
    }''',
    "controls effects",
)
screen = replace_once(
    screen,
    "    val background = readerBackgroundV3(settings.palette)\n",
    "    val snackbarControlsShiftPx = with(LocalDensity.current) { 88.dp.toPx() }\n    val background = readerBackgroundV3(settings.palette)\n",
    "snackbar layer shift",
)
screen = replace_once(
    screen,
    '''        if (controlsVisible && more) Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp)) {
            ReaderMoreMenuV3(state.cleanMode, actions) { more = false }
        }''',
    '''        if (more) Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp).graphicsLayer {
                translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) { ReaderMoreMenuV3(state.cleanMode, actions) { more = false } }''',
    "more menu layer visibility",
)
screen = replace_once(
    screen,
    '''        if (!controlsVisible && settings.showReadingStatus) ReaderReadingStatusV3(state, currentChapterIndex, textColor, background, stats, Modifier.align(Alignment.BottomCenter))
        if (state.autoScrolling && !controlsVisible) AutoScrollLiveControlV3(settings, actions, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp))''',
    '''        if (settings.showReadingStatus) ReaderReadingStatusV3(
            state, currentChapterIndex, textColor, background, stats,
            Modifier.align(Alignment.BottomCenter).graphicsLayer {
                translationY = if (controlsVisible) READER_HIDDEN_LAYER_OFFSET_PX.toFloat() else 0f
                alpha = if (controlsVisible) 0f else 1f
            },
        )
        if (state.autoScrolling) AutoScrollLiveControlV3(
            settings, actions,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp).graphicsLayer {
                translationY = if (controlsVisible) READER_HIDDEN_LAYER_OFFSET_PX.toFloat() else 0f
                alpha = if (controlsVisible) 0f else 1f
            },
        )''',
    "reading status layer visibility",
)
screen = replace_once(
    screen,
    '        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible) 112.dp else 24.dp))',
    '''        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).graphicsLayer {
                translationY = if (controlsVisible) -snackbarControlsShiftPx else 0f
            },
        )''',
    "snackbar composition visibility",
)
screen = replace_once(
    screen,
    '''    val scrollModel = remember(book.id) { ReaderContinuousScrollModel() }
    val scrollableState = rememberScrollableState(scrollModel::consumeDelta)
    val density = LocalDensity.current''',
    '''    val scrollModel = remember(book.id) { ReaderContinuousScrollModel() }
    val settleEvents = remember(book.id) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val density = LocalDensity.current''',
    "continuous settle event model",
)
screen = replace_once(
    screen,
    '''    LaunchedEffect(scrollableState, layoutResult, window, viewportHeight) {
        snapshotFlow { scrollableState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling) settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false)
        }
    }''',
    '''    LaunchedEffect(settleEvents, layoutResult, window, viewportHeight) {
        settleEvents.collect { settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false) }
    }''',
    "continuous scroll-end event",
)
old_gestures = '''    val semantics = Modifier.readerAccessibilityActionsV3(onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous), stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls), stringResource(R.string.reader_access_bookmark))
    val gestures = if (touchExploration) Modifier else Modifier.readerGesturesV3(settings, widthPx, viewportHeight, systemLeft, systemRight, onPrevious, onNext, onToggleControls, onBrightnessDelta, onBookmark) {
        if (state.autoScrolling) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
    }.pointerInput(settings.pinchFontEnabled) { if (settings.pinchFontEnabled) detectTransformGestures { _, _, zoom, _ -> onResizeFont(zoom) } }

    SelectionContainer(state = selectionState) {
        Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; viewportHeight = it.height }.then(semantics).then(gestures), contentAlignment = Alignment.TopCenter) {
            Text(
                annotated,
                Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp),
                style = style,
                overflow = TextOverflow.Clip,
                scrollableState = scrollableState,
                scrollModel = scrollModel,
                onTextLayout = { layoutResult = it },
            )'''
new_gestures = '''    val semantics = Modifier.readerAccessibilityActionsV3(onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous), stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls), stringResource(R.string.reader_access_bookmark))

    SelectionContainer(state = selectionState) {
        Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; viewportHeight = it.height }.then(semantics), contentAlignment = Alignment.TopCenter) {
            Text(
                annotated,
                Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp),
                style = style,
                overflow = TextOverflow.Clip,
                scrollModel = scrollModel,
                settings = settings,
                systemLeftInsetPx = systemLeft,
                systemRightInsetPx = systemRight,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleControls = onToggleControls,
                onBrightnessDelta = onBrightnessDelta,
                onResizeFont = onResizeFont,
                onBookmark = onBookmark,
                onAnyTouch = { if (state.autoScrolling) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)) },
                onScrollSettled = { settleEvents.tryEmit(Unit) },
                onTextLayout = { layoutResult = it },
            )'''
screen = replace_once(screen, old_gestures, new_gestures, "continuous native gesture ownership")
screen_path.write_text(screen)

# 3) Hot Quick/Chapters overlay visibility gets its own flow; reader state never republishes for it.
vm_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt")
vm = vm_path.read_text()
vm = replace_once(
    vm,
    '''    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private val back = ArrayDeque<Long>()''',
    '''    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private val mutableHotPanel = MutableStateFlow<ReaderPanel?>(null)
    val hotPanel: StateFlow<ReaderPanel?> = mutableHotPanel.asStateFlow()

    fun openHotPanel(panel: ReaderPanel) {
        require(panel == ReaderPanel.QUICK_SETTINGS || panel == ReaderPanel.CHAPTERS)
        mutableHotPanel.value = panel
    }
    fun closeHotPanel() { mutableHotPanel.value = null }

    private val back = ArrayDeque<Long>()''',
    "hot panel flow",
)
vm = replace_once(vm, "            locationBookId = nextBookId\n            resetLocationHistory()", "            locationBookId = nextBookId\n            mutableHotPanel.value = null\n            resetLocationHistory()", "hot panel book reset")
vm_path.write_text(vm)

main_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt")
main = main_path.read_text()
main = replace_once(main, "            onClosePanel = { uiState = uiState.copy(panel = null) },", "            onClosePanel = ::closePanel,", "close panel action")
main = replace_once(
    main,
    '''            JingduApp(
                state = state, actions = actions, location = location,
                onTrackLocation = { current, target, length -> readerViewModel.trackLocation(current, target, length) },''',
    '''            JingduApp(
                state = state, actions = actions, location = location, hotPanel = readerViewModel.hotPanel,
                onTrackLocation = { current, target, length -> readerViewModel.trackLocation(current, target, length) },''',
    "hot panel flow injection",
)
main = replace_once(main, "        uiState = uiState.copy(panel = null)\n        render()\n    }\n\n    private fun syncTtsPosition", "        readerViewModel.closeHotPanel()\n        uiState = uiState.copy(panel = null)\n        render()\n    }\n\n    private fun syncTtsPosition", "jump closes hot panel")
main = replace_once(
    main,
    '''    private fun backToLibrary() {
        uiState.panel?.let { uiState = uiState.copy(panel = null); return }''',
    '''    private fun backToLibrary() {
        readerViewModel.hotPanel.value?.let { readerViewModel.closeHotPanel(); return }
        uiState.panel?.let { uiState = uiState.copy(panel = null); return }''',
    "back hot panel",
)
old_open = '''    private fun openPanel(panel: ReaderPanel) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAutoScroll()
        uiState = uiState.copy(panel = panel)
        when (panel) {
            ReaderPanel.BOOKMARKS -> refreshBookmarks()
            ReaderPanel.CLEAN -> refreshRules()
            ReaderPanel.SETTINGS -> refreshTtsVoices()
            else -> Unit
        }
    }
'''
new_open = '''    private fun closePanel() {
        if (readerViewModel.hotPanel.value != null) readerViewModel.closeHotPanel()
        else uiState = uiState.copy(panel = null)
    }

    private fun openPanel(panel: ReaderPanel) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAutoScroll()
        if (panel == ReaderPanel.QUICK_SETTINGS || panel == ReaderPanel.CHAPTERS) {
            readerViewModel.openHotPanel(panel)
            return
        }
        readerViewModel.closeHotPanel()
        uiState = uiState.copy(panel = panel)
        when (panel) {
            ReaderPanel.BOOKMARKS -> refreshBookmarks()
            ReaderPanel.CLEAN -> refreshRules()
            ReaderPanel.SETTINGS -> refreshTtsVoices()
            else -> Unit
        }
    }
'''
main = replace_once(main, old_open, new_open, "hot panel open boundary")
main = replace_once(main, "if (!isDestroyed && currentBook?.id == book.id && uiState.panel == ReaderPanel.CHAPTERS)", "if (!isDestroyed && currentBook?.id == book.id && readerViewModel.hotPanel.value == ReaderPanel.CHAPTERS)", "chapter error panel boundary")
main_path.write_text(main)

app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
app = replace_once(app, "import androidx.compose.ui.graphics.graphicsLayer\n", "import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.semantics.clearAndSetSemantics\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport kotlinx.coroutines.flow.StateFlow\n", "hot panel imports")
app = replace_once(
    app,
    '''    location: ReaderLocationState = ReaderLocationState(),
    onTrackLocation: (current: Long, target: Long, length: Long) -> Unit = { _, _, _ -> },''',
    '''    location: ReaderLocationState = ReaderLocationState(),
    hotPanel: StateFlow<ReaderPanel?>? = null,
    onTrackLocation: (current: Long, target: Long, length: Long) -> Unit = { _, _, _ -> },''',
    "JingduApp hot panel parameter",
)
old_layers = '''            if (state.screen == AppScreen.READER) {
                PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS) {
                    ReaderQuickSettingsSheet(quickPanelState, trackedActions)
                }
                PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS) {
                    ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition)
                }
            }'''
new_layers = '''            if (state.screen == AppScreen.READER) {
                if (hotPanel != null) ReaderHotPanelHost(hotPanel, quickPanelState, chaptersPanelState, trackedActions, currentReaderPosition)
                else {
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS) { ReaderQuickSettingsSheet(quickPanelState, trackedActions) }
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS) { ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition) }
                }
            }'''
app = replace_once(app, old_layers, new_layers, "hot panel host")
insert_point = '''/**
 * Quick/Chapters are high-frequency reader overlays. Keep them composed after Reader opens and
 * move the complete layer outside the viewport while hidden. Modifier.offset reads visibility in
 * layout, so open/close does not destroy and recreate the panel composition or its Canvas display list.
 */
@Composable
private fun PersistentReaderPanelLayer'''
host = '''/** Hot overlay state is collected in this restart group only; ReaderRoute never subscribes to it. */
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
private fun PersistentReaderPanelLayer'''
app = replace_once(app, insert_point, host, "hot panel host insertion")
app = replace_once(
    app,
    '''    Box(
        Modifier.fillMaxSize().graphicsLayer {
            translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
            alpha = if (visible) 1f else 0f
        },
    ) { content() }''',
    '''    val hiddenSemantics = if (visible) Modifier else Modifier.clearAndSetSemantics { }
    Box(
        Modifier.fillMaxSize().then(hiddenSemantics).graphicsLayer {
            translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
            alpha = if (visible) 1f else 0f
        },
    ) { content() }''',
    "hidden hot panel semantics",
)
app_path.write_text(app)

# 4) Contract + independently captured #715 profile evidence.
verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = replace_once(
    verify,
    '''require_literal "$screen" 'rememberScrollableState' 'continuous layer scroll state'
require_literal "$screen" 'scrollableState.isScrollInProgress' 'continuous gesture settling'
forbid_literal "$screen" '.verticalScroll(scrollState)' 'layout-driven continuous scrolling'
require_literal "$fast_text" 'AndroidView(' 'native continuous viewport'
require_literal "$fast_text" 'content.translationY = -value' 'continuous RenderNode translation'
forbid_literal "$fast_text" 'Canvas(Modifier.fillMaxSize()) {' 'continuous Compose viewport canvas'
forbid_literal "$fast_text" 'graphicsLayer { translationY = -scrollOffsetPx()' 'oversized continuous graphics layer'
require_literal "$fast_text" 'scrollable(scrollableState, Orientation.Vertical)' 'continuous gesture layer'
require_literal "$engine" 'reusableHasHeadingStyle' 'heading-aware measured layout reuse'
require_literal "$screen" 'snapshotFlow { scrollableState.isScrollInProgress }' 'scroll-end continuous mapping'
forbid_literal "$screen" 'snapshotFlow { scrollOffsetPx.roundToInt()' 'per-delta continuous source mapping'
require_literal "$screen" 'settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false)' 'manual continuous settle' ''',
    '''forbid_literal "$screen" 'rememberScrollableState' 'Compose continuous scroll state'
forbid_literal "$fast_text" 'scrollable(scrollableState, Orientation.Vertical)' 'Compose continuous scrollable layer'
forbid_literal "$screen" 'snapshotFlow { scrollableState.isScrollInProgress }' 'Compose scroll progress observer'
require_literal "$fast_text" 'override fun onTouchEvent(event: MotionEvent)' 'native continuous gesture ownership'
require_literal "$fast_text" 'OverScroller(context)' 'native continuous fling'
require_literal "$fast_text" 'content.translationY = -value' 'continuous RenderNode translation'
require_literal "$fast_text" 'model.setOffset(model.offsetPx + (lastY - event.y))' 'native direct scroll property update'
require_literal "$screen" 'MutableSharedFlow<Unit>(extraBufferCapacity = 1)' 'settle-only continuous event channel'
require_literal "$screen" 'settleEvents.collect' 'settle-only continuous mapping'
require_literal "$engine" 'reusableHasHeadingStyle' 'heading-aware measured layout reuse'
forbid_literal "$screen" 'snapshotFlow { scrollOffsetPx.roundToInt()' 'per-delta continuous source mapping'
require_literal "$screen" 'settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false)' 'manual continuous settle' ''',
    "continuous contract",
)
verify = replace_once(
    verify,
    '''require_literal "$app" 'PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS)' 'resident quick settings layer'
require_literal "$app" 'PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS)' 'resident chapters layer'
require_literal "$app" 'ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit' 'persistent panel route ownership' ''',
    '''require_literal "$app" 'ReaderHotPanelHost(hotPanel' 'isolated hot panel restart group'
require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt 'val hotPanel: StateFlow<ReaderPanel?>' 'hot panel state flow'
require_literal "$activity" 'readerViewModel.openHotPanel(panel)' 'hot panel publication boundary'
require_literal "$activity" 'hotPanel = readerViewModel.hotPanel' 'hot panel flow injection'
require_literal "$app" 'ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit' 'persistent panel route ownership' ''',
    "hot panel contract",
)
verify = replace_once(verify, "require_literal \"$screen\" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'", "require_literal \"$screen\" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'\nrequire_literal \"$screen\" 'snapshotFlow { controlsVisible }' 'layer-only controls visibility'\nforbid_literal \"$screen\" 'padding(bottom = if (controlsVisible)' 'controls visibility composition padding'", "controls visibility contract")
verify_path.write_text(verify)

baseline_path = Path("apps/android/app/src/main/baseline-prof.txt")
baseline = baseline_path.read_text()
for rule in (
    "HPLcom/junchen/jingdu/ReaderContinuousScrollModel;->**(**)**",
    "HPLcom/junchen/jingdu/ReaderContinuousViewportView;->**(**)**",
    "HPLcom/junchen/jingdu/ReaderContinuousTextView;->**(**)**",
):
    if rule not in baseline:
        baseline += ("" if baseline.endswith("\n") else "\n") + rule + "\n"
baseline_path.write_text(baseline)

prov_path = Path("docs/READER_V3_PROFILE_PROVENANCE.md")
prov = prov_path.read_text()
prov = prov.replace("`09e0d7f988c74507d5a6c5442f95e91e8864f9bc`", "`97bd7b952735255d567fb13f7d8777bdf4c7858e`")
prov = prov.replace("`#701` / run `33191024009`", "`#715` / run `33224899370`")
prov = prov.replace("24,466 rules, 2,549,632 bytes, SHA-256 `03ec774b23504e8397980382602a8df6f50f4bb6cbabf569730ab28ec984a426`", "24,485 rules, 2,553,730 bytes, SHA-256 `141e3f372636d74862f437ee7a62cb424ca412447012870981c42023b0439509`")
prov = prov.replace("22,781 rules, 2,342,202 bytes, SHA-256 `946667b8ea7cd0a156fd75bd449694aae3115ce78a4e9226626d4c251b4048ce`", "22,827 rules, 2,346,528 bytes, SHA-256 `791ee598c4eb2271a2c8cda213ff7af400f5f395143e4c52675851199618be82`")
prov = prov.replace("The #701 capture specifically promoted `ReaderFastTextKt`, `ReaderHotControlsKt`, `ReaderHotPanelCanvasKt`, Canvas/gesture/layout and Material button/icon paths.", "The #715 capture confirms `ReaderFastTextKt` and the native `ReaderContinuousScrollModel`, `ReaderContinuousViewportView` and `ReaderContinuousTextView` hot paths, in addition to the previously promoted controls/panel/framework families.")
prov_path.write_text(prov)
