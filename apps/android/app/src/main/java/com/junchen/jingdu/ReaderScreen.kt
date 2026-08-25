@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ReaderSelection(val start: Long, val end: Long, val excerpt: String)

@Composable
internal fun ReaderScreen(
    state: AppUiState,
    actions: JingduActions,
    snackbar: SnackbarHostState,
    adaptiveLayout: ReaderAdaptiveLayout = ReaderAdaptiveLayout(ReaderAdaptiveWidth.COMPACT, false, false),
    canLocationBack: Boolean = false,
    canLocationForward: Boolean = false,
    onLocationBack: () -> Unit = {},
    onLocationForward: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val book = state.currentBook ?: return
    val settings = state.settings
    val haptics = LocalHapticFeedback.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val touchExploration = accessibility.isTouchExplorationEnabled
    val fontFamily = rememberReaderFontFamily(context, settings)
    var controlsVisible by rememberSaveable(book.id) { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var pageDirection by remember(book.id) { mutableIntStateOf(0) }
    var selection by remember(book.id) { mutableStateOf<ReaderSelection?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }

    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
    val progressPercent = (fraction * 100).roundToInt()
    val currentChapter = remember(state.chapters, state.position) { state.chapters.lastOrNull { it.offset <= state.position }?.title }

    DisposableEffect(activity) {
        val window = activity?.window
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
                val attrs = it.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = attrs
            }
            if (activity != null && !activity.isChangingConfigurations && !activity.isFinishing) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(activity, settings.orientation) {
        activity?.requestedOrientation = when (settings.orientation) {
            ReaderOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    LaunchedEffect(activity, settings.useSystemBrightness, settings.readerBrightness) {
        activity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = if (settings.useSystemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.readerBrightness.coerceIn(0.03f, 1f)
            window.attributes = attrs
        }
    }
    LaunchedEffect(activity, state.motion) {
        activity?.window?.let { window ->
            if (state.motion == ReaderMotionState.IDLE) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(activity, controlsVisible, state.panel) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible || state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(controlsVisible, state.panel, settings.controlsAutoHideMs) {
        if (controlsVisible && state.panel == null) { delay(settings.controlsAutoHideMs); controlsVisible = false }
    }

    fun tick() { if (settings.hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun previous() { selection = null; tick(); pageDirection = -1; actions.onNavigatePrevious() }
    fun next() { selection = null; tick(); pageDirection = 1; actions.onNavigateNext() }
    fun seek(value: Float) { selection = null; pageDirection = 0; actions.onSeekFraction(value) }
    fun updateBrightness(delta: Float) {
        val value = (settings.readerBrightness + delta).coerceIn(0.03f, 1f)
        if (abs(value - settings.readerBrightness) >= 0.005f) actions.onSettingsChanged(settings.copy(useSystemBrightness = false, readerBrightness = value))
    }
    fun resizeFont(zoom: Float) {
        if (!settings.pinchFontEnabled || abs(zoom - 1f) < 0.05f) return
        val value = (settings.fontSizeSp * zoom).coerceIn(14f, 40f)
        if (abs(value - settings.fontSizeSp) >= 0.5f) actions.onSettingsChanged(settings.copy(fontSizeSp = value, preset = ReaderPreset.CUSTOM))
    }
    fun addBookmark() { tick(); actions.onAddBookmark() }

    val background = readerBackground(settings.palette)
    val textColor = readerTextColor(settings.palette)
    Box(Modifier.fillMaxSize().background(background)) {
        if (settings.readingMode == ReaderMode.CONTINUOUS && !state.cleanMode) {
            ContinuousReaderPage(state, actions, fontFamily, textColor, touchExploration, ::previous, ::next,
                { controlsVisible = !controlsVisible }, ::updateBrightness, ::resizeFont, ::addBookmark,
                { selection = it; controlsVisible = true })
        } else {
            AnimatedContent(
                targetState = state.position to state.pageText,
                transitionSpec = {
                    if (settings.pageAnimation == ReaderPageAnimation.SLIDE && pageDirection != 0) {
                        (slideInHorizontally { pageDirection * it } + fadeIn()) togetherWith (slideOutHorizontally { -pageDirection * it } + fadeOut())
                    } else fadeIn() togetherWith fadeOut()
                },
                label = "reader-page",
                modifier = Modifier.fillMaxSize(),
            ) { (position, text) ->
                PagedReaderPage(position, text, state, adaptiveLayout, fontFamily, textColor, touchExploration,
                    actions.onVisibleCharsChanged, ::previous, ::next, { controlsVisible = !controlsVisible },
                    ::updateBrightness, ::resizeFont, ::addBookmark, { selection = it; controlsVisible = true })
            }
        }

        if (settings.focusRulerLines > 0) Box(
            Modifier.fillMaxWidth().height((settings.fontSizeSp * settings.lineHeightMultiplier * settings.focusRulerLines).dp)
                .align(Alignment.Center).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
        )

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(state, currentChapter, actions) { more = true }
        }
        if (controlsVisible && more) Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp)) {
            ReaderMoreMenu(state, actions) { more = false }
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(fraction, state, currentChapter, canLocationBack, canLocationForward, onLocationBack, onLocationForward,
                ::seek, { actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) }, actions.onToggleTts, actions.onToggleAutoPaging)
        }
        if (!controlsVisible && settings.showReadingStatus) ReaderReadingStatus(state, progressPercent, currentChapter, textColor, background, Modifier.align(Alignment.BottomCenter))
        if (state.autoScrolling && !controlsVisible) AutoScrollLiveControl(settings, actions, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp))

        selection?.let { selected ->
            ReaderSelectionBar(selected,
                onHighlight = { style -> actions.onAddAnnotation(selected.start, selected.end, ReaderAnnotationKind.HIGHLIGHT, style, "", selected.excerpt); selection = null },
                onNote = { noteDraft = ""; showNoteDialog = true },
                onCopy = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Jingdu", selected.excerpt)); selection = null
                },
                onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selected.excerpt) }, null)); selection = null },
                onDismiss = { selection = null }, modifier = Modifier.align(Alignment.Center))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible) 104.dp else 24.dp))
    }

    if (showNoteDialog && selection != null) AlertDialog(
        onDismissRequest = { showNoteDialog = false },
        title = { Text(stringResource(R.string.reader_note)) },
        text = { OutlinedTextField(noteDraft, { noteDraft = it.take(2000) }, label = { Text(stringResource(R.string.reader_note_hint)) }) },
        confirmButton = { TextButton(onClick = {
            selection?.let { actions.onAddAnnotation(it.start, it.end, ReaderAnnotationKind.NOTE, ReaderHighlightStyle.YELLOW, noteDraft, it.excerpt) }
            selection = null; showNoteDialog = false
        }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton({ showNoteDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(state.position) { if (pageDirection != 0) { delay(220); pageDirection = 0 } }
}

@Composable
private fun ReaderTopBar(state: AppUiState, chapter: String?, actions: JingduActions, onMore: () -> Unit) {
    val book = state.currentBook ?: return
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            navigationIcon = { IconButton(actions.onBackToLibrary) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_library)) } },
            title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stripTxt(book.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                chapter?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
            } },
            actions = {
                TextButton({ actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) }) { Text("Aa") }
                IconButton({ actions.onOpenPanel(ReaderPanel.CHAPTERS); actions.onEnsureChapters() }) { Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.chapters)) }
                IconButton(onMore) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_reading_tools)) }
            },
        )
    }
}

@Composable
private fun ReaderMoreMenu(state: AppUiState, actions: JingduActions, onDismiss: () -> Unit) {
    DropdownMenu(true, onDismissRequest = onDismiss) {
        fun close(action: () -> Unit) { onDismiss(); action() }
        DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reader_annotations)) }, { close { actions.onOpenPanel(ReaderPanel.ANNOTATIONS) } }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reader_reading_map)) }, { close { actions.onOpenPanel(ReaderPanel.READING_MAP); actions.onEnsureChapters() } }, leadingIcon = { Icon(Icons.Outlined.Map, null) })
        DropdownMenuItem({ Text(stringResource(R.string.txt_doctor)) }, { close { actions.onOpenPanel(ReaderPanel.DOCTOR) } }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) })
        DropdownMenuItem({ Text(stringResource(R.string.smart_clean4)) }, { close { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) } }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) })
        DropdownMenuItem({ Text(stringResource(R.string.clean)) }, { close { actions.onOpenPanel(ReaderPanel.CLEAN) } }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reading_settings)) }, { close { actions.onOpenPanel(ReaderPanel.SETTINGS) } }, leadingIcon = { Icon(Icons.Default.Settings, null) })
        if (!state.cleanMode) DropdownMenuItem({ Text(stringResource(R.string.reader_access_bookmark)) }, { close(actions.onAddBookmark) }, leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) })
    }
}

@Composable
private fun PagedReaderPage(
    sourceStart: Long,
    sourceText: String,
    state: AppUiState,
    adaptiveLayout: ReaderAdaptiveLayout,
    fontFamily: FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onVisibleCharsChanged: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (ReaderSelection) -> Unit,
) {
    val settings = state.settings
    val displayText by produceState(sourceText, sourceText, settings.chineseMode, settings.chineseOverrides, settings.compressBlankLines) {
        value = withContext(Dispatchers.Default) {
            val normalized = if (settings.compressBlankLines) sourceText.replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n") else sourceText
            ChineseDisplayConverter.convert(normalized, settings.chineseMode, settings.chineseOverrides)
        }
    }
    val style = readerTextStyle(settings, textColor, fontFamily)
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val columns = when (settings.wideColumns) {
        ReaderWideColumns.SINGLE -> 1
        ReaderWideColumns.DOUBLE -> if (adaptiveLayout.width >= ReaderAdaptiveWidth.MEDIUM && !adaptiveLayout.tabletop) 2 else 1
        ReaderWideColumns.AUTO -> if (adaptiveLayout.prefersTwoColumns) 2 else 1
    }
    LaunchedEffect(sourceText, displayText, widthPx, heightPx, columns, settings.fontSizeSp, settings.lineHeightMultiplier, settings.letterSpacingEm) {
        if (widthPx <= 0 || heightPx <= 0 || displayText.isEmpty()) return@LaunchedEffect
        val snapshot = withContext(Dispatchers.Default) { ReaderPageLayoutCache.measure(sourceText, displayText, widthPx, heightPx, columns, settings, density) }
        if (snapshot.sourceCodePoints >= ReaderController.MIN_PAGE_CHARS) onVisibleCharsChanged(snapshot.sourceCodePoints)
    }
    val semantics = Modifier.readerAccessibilityActions(
        onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous),
        stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls),
        stringResource(R.string.reader_access_bookmark),
    )
    val gestures = if (touchExploration) Modifier else Modifier.readerGesturesV2(settings, widthPx, heightPx, onPrevious, onNext, onToggleControls, onBrightnessDelta, onBookmark,
        onLongPress = { point ->
            val layout = layoutResult ?: return@readerGesturesV2
            if (displayText.isEmpty()) return@readerGesturesV2
            val y = (point.y - with(density) { settings.verticalPaddingDp.dp.toPx() }).coerceAtLeast(0f)
            val line = layout.getLineForVerticalPosition(y).coerceIn(0, layout.lineCount - 1)
            val a = layout.getLineStart(line).coerceIn(0, displayText.length)
            val b = layout.getLineEnd(line, visibleEnd = true).coerceIn(a, displayText.length)
            val sourceA = sourceStart + ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayText.codePointCount(0, a).toLong())
            val sourceB = sourceStart + ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayText.codePointCount(0, b).toLong())
            if (sourceB > sourceA) onSelection(ReaderSelection(sourceA, sourceB, displayText.substring(a, b).trim().take(800)))
        }).pointerInput(settings.pinchFontEnabled) { if (settings.pinchFontEnabled) detectTransformGestures { _, _, zoom, _ -> onResizeFont(zoom) } }
    Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures), contentAlignment = Alignment.TopCenter) {
        if (columns == 2) TwoColumnPage(sourceStart, sourceText, displayText, state, style) { layoutResult = it }
        else Text(readerAnnotatedText(sourceStart, sourceText, displayText, state),
            Modifier.fillMaxHeight().widthIn(max = 760.dp).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp),
            style = style, overflow = TextOverflow.Clip, onTextLayout = { layoutResult = it })
    }
}

@Composable
private fun TwoColumnPage(sourceStart: Long, sourceText: String, displayText: String, state: AppUiState, style: TextStyle, onLayout: (TextLayoutResult) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = state.settings.horizontalPaddingDp.dp, vertical = state.settings.verticalPaddingDp.dp), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.coerceAtMost(1200.dp).roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val snapshot = remember(sourceText, displayText, widthPx, heightPx, state.settings) { ReaderPageLayoutCache.measure(sourceText, displayText, widthPx, heightPx, 2, state.settings, density) }
        val firstEnd = snapshot.firstColumnEndUtf16.coerceIn(0, displayText.length)
        val fullEnd = snapshot.displayedEndUtf16.coerceIn(firstEnd, displayText.length)
        Row(Modifier.widthIn(max = 1200.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Text(readerAnnotatedText(sourceStart, sourceText, displayText.substring(0, firstEnd), state), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip, onTextLayout = onLayout)
            val secondStart = sourceStart + ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayText.codePointCount(0, firstEnd).toLong())
            Text(readerAnnotatedText(secondStart, sourceText, displayText.substring(firstEnd, fullEnd), state), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun ContinuousReaderPage(
    state: AppUiState,
    actions: JingduActions,
    fontFamily: FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (ReaderSelection) -> Unit,
) {
    val context = LocalContext.current
    val book = state.currentBook ?: return
    val settings = state.settings
    val engine = remember(book.id) { ReaderViewportEngine(context, book.id) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var window by remember(book.id) { mutableStateOf<ReaderDisplayWindow?>(null) }
    var layoutResult by remember(book.id) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var loading by remember(book.id) { mutableStateOf(false) }
    var lastCommitted by remember(book.id) { mutableLongStateOf(state.position) }
    var localPosition by remember(book.id) { mutableLongStateOf(state.position) }

    suspend fun loadAround(target: Long) {
        if (loading) return
        loading = true
        try {
            val next = withContext(Dispatchers.IO) { engine.readAround(target, settings) }
            window = next; localPosition = target.coerceIn(0L, (next.documentLength - 1).coerceAtLeast(0L)); layoutResult = null
        } finally { loading = false }
    }
    DisposableEffect(engine) { onDispose { engine.close() } }
    LaunchedEffect(book.id, settings.chineseMode, settings.chineseOverrides, settings.compressBlankLines) {
        withContext(Dispatchers.IO) { engine.clear() }; loadAround(state.position); withContext(Dispatchers.IO) { engine.prefetch(state.position, settings) }
    }
    LaunchedEffect(state.tts.offset, state.tts.active) { if (state.tts.active && state.tts.offset >= 0 && abs(state.tts.offset - localPosition) > 128) loadAround(state.tts.offset) }
    LaunchedEffect(window, layoutResult) {
        val w = window ?: return@LaunchedEffect; val layout = layoutResult ?: return@LaunchedEffect
        if (w.displayText.isEmpty()) return@LaunchedEffect
        val utf = utf16IndexForCodePoints(w.displayText, w.map.displayForSource((localPosition - w.start).coerceAtLeast(0)))
        val line = layout.getLineForOffset(utf.coerceIn(0, (w.displayText.length - 1).coerceAtLeast(0)))
        scrollState.scrollTo(layout.getLineTop(line).roundToInt().coerceIn(0, scrollState.maxValue))
    }
    LaunchedEffect(scrollState, layoutResult, window, viewportHeight) {
        snapshotFlow { scrollState.value }.distinctUntilChanged().collect { y ->
            val w = window ?: return@collect; val layout = layoutResult ?: return@collect
            if (w.displayText.isEmpty() || layout.lineCount <= 0) return@collect
            val line = layout.getLineForVerticalPosition(y.toFloat()).coerceIn(0, layout.lineCount - 1)
            val utf = layout.getLineStart(line).coerceIn(0, w.displayText.length)
            val absolute = (w.start + w.map.sourceForDisplay(w.displayText.codePointCount(0, utf).toLong())).coerceIn(0L, (w.documentLength - 1).coerceAtLeast(0L))
            localPosition = absolute
            if (abs(absolute - lastCommitted) >= 192) { lastCommitted = absolute; actions.onSyncTtsPosition(absolute) }
            val edge = (viewportHeight * 0.25f).roundToInt()
            val nearTop = y <= edge && w.start > 0
            val nearBottom = scrollState.maxValue > 0 && scrollState.maxValue - y <= edge && w.start + w.map.sourceCodePoints < w.documentLength - 1
            if (!loading && (nearTop || nearBottom)) loadAround(absolute)
        }
    }
    LaunchedEffect(state.autoScrolling, settings.autoScrollSpeedDpPerSecond, window) {
        if (!state.autoScrolling) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive && state.autoScrolling) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0
                    scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })
                }
                lastFrame = now
            }
            val w = window ?: continue
            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 1 && w.start + w.map.sourceCodePoints >= w.documentLength - 1) {
                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)); break
            }
        }
    }
    val display = window?.displayText.orEmpty(); val source = window?.sourceText.orEmpty(); val start = window?.start ?: state.position
    val style = readerTextStyle(settings, textColor, fontFamily)
    val semantics = Modifier.readerAccessibilityActions(
        onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous),
        stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls),
        stringResource(R.string.reader_access_bookmark),
    )
    val gestures = if (touchExploration) Modifier else Modifier.readerGesturesV2(settings, widthPx, viewportHeight, onPrevious, onNext, onToggleControls, onBrightnessDelta, onBookmark,
        onLongPress = { point ->
            val layout = layoutResult ?: return@readerGesturesV2; val w = window ?: return@readerGesturesV2
            if (display.isEmpty()) return@readerGesturesV2
            val y = (point.y + scrollState.value - with(density) { settings.verticalPaddingDp.dp.toPx() }).coerceAtLeast(0f)
            val line = layout.getLineForVerticalPosition(y).coerceIn(0, layout.lineCount - 1)
            val a = layout.getLineStart(line).coerceIn(0, display.length); val b = layout.getLineEnd(line, true).coerceIn(a, display.length)
            val sa = w.start + w.map.sourceForDisplay(display.codePointCount(0, a).toLong()); val sb = w.start + w.map.sourceForDisplay(display.codePointCount(0, b).toLong())
            if (sb > sa) onSelection(ReaderSelection(sa, sb, display.substring(a, b).trim().take(800)))
        }, onAnyTouch = { if (state.autoScrolling) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)) })
        .pointerInput(settings.pinchFontEnabled) { if (settings.pinchFontEnabled) detectTransformGestures { _, _, zoom, _ -> onResizeFont(zoom) } }
    Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; viewportHeight = it.height }.then(semantics).then(gestures), contentAlignment = Alignment.TopCenter) {
        Text(readerAnnotatedText(start, source, display, state), Modifier.fillMaxWidth().verticalScroll(scrollState)
            .padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp),
            style = style, overflow = TextOverflow.Clip, onTextLayout = { layoutResult = it })
        if (loading && display.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

private fun Modifier.readerGesturesV2(
    settings: ReaderSettings,
    widthPx: Int,
    heightPx: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onBookmark: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onAnyTouch: () -> Unit = {},
): Modifier = pointerInput(settings, widthPx, heightPx) {
    val swipe = 52.dp.toPx(); val tapSlop = 14.dp.toPx()
    var lastCenterTapAt = 0L
    var pendingCenterTap: Job? = null
    val gestureScope = CoroutineScope(currentCoroutineContext())
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false); onAnyTouch(); var last = down; var maxPointers = 1
        do { val event = awaitPointerEvent(PointerEventPass.Final); maxPointers = maxOf(maxPointers, event.changes.size); event.changes.firstOrNull { it.id == down.id }?.let { last = it } } while (last.pressed)
        if (maxPointers > 1) return@awaitEachGesture
        val delta = last.position - down.position; val duration = last.uptimeMillis - down.uptimeMillis
        if (settings.brightnessGestureEnabled && widthPx > 0 && down.position.x <= widthPx * 0.14f && abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe) {
            onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f); return@awaitEachGesture
        }
        if (settings.swipePagingEnabled && abs(delta.x) >= swipe && abs(delta.x) > abs(delta.y) * 1.25f) {
            var forward = delta.x < 0; if (settings.reversePagingGestures) forward = !forward; if (forward) onNext() else onPrevious(); return@awaitEachGesture
        }
        if (duration >= 520 && delta.getDistance() <= tapSlop * 1.5f) { onLongPress(down.position); return@awaitEachGesture }
        if (duration <= 360 && delta.getDistance() <= tapSlop && widthPx > 0) {
            val edge = when (settings.tapZonePreset) {
                ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> widthPx * settings.tapZoneEdgeFraction
                ReaderTapZonePreset.RIGHT_HANDED -> widthPx * 0.22f
                ReaderTapZonePreset.LEFT_HANDED -> widthPx * 0.32f
            }
            when {
                down.position.x < edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onNext() else onPrevious()
                down.position.x > widthPx - edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onPrevious() else onNext()
                else -> {
                    if (!settings.doubleTapBookmarkEnabled) {
                        onToggleControls()
                    } else {
                        val tapAt = last.uptimeMillis
                        if (lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {
                            pendingCenterTap?.cancel(); pendingCenterTap = null; lastCenterTapAt = 0L; onBookmark()
                        } else {
                            lastCenterTapAt = tapAt
                            pendingCenterTap?.cancel()
                            pendingCenterTap = gestureScope.launch { delay(280L); onToggleControls() }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.readerAccessibilityActions(
    previous: () -> Unit, next: () -> Unit, controls: () -> Unit, bookmark: () -> Unit,
    surfaceLabel: String, previousLabel: String, nextLabel: String, controlsLabel: String, bookmarkLabel: String,
): Modifier = semantics {
    contentDescription = surfaceLabel
    customActions = listOf(
        CustomAccessibilityAction(previousLabel) { previous(); true }, CustomAccessibilityAction(nextLabel) { next(); true },
        CustomAccessibilityAction(controlsLabel) { controls(); true }, CustomAccessibilityAction(bookmarkLabel) { bookmark(); true },
    )
}

@Composable
private fun ReaderBottomBar(
    fraction: Float, state: AppUiState, chapter: String?, canLocationBack: Boolean, canLocationForward: Boolean,
    onLocationBack: () -> Unit, onLocationForward: () -> Unit, onSeek: (Float) -> Unit,
    onOpenQuick: () -> Unit, onTts: () -> Unit, onAutoPage: () -> Unit,
) {
    var value by remember(fraction) { mutableFloatStateOf(fraction) }
    val progressDescription = stringResource(R.string.reading_progress)
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(chapter ?: "", Modifier.align(Alignment.CenterHorizontally), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            Slider(value, { value = it }, onValueChangeFinished = { onSeek(value) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onLocationBack, enabled = canLocationBack) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.reader_location_back)) }
                TextButton(onOpenQuick) { Text("Aa") }
                IconButton(onAutoPage) { Icon(if (state.autoPaging) Icons.Default.Pause else Icons.Outlined.Timer, stringResource(if (state.autoPaging) R.string.stop_auto_page else R.string.start_auto_page)) }
                IconButton(onTts) { Icon(if (state.ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (state.ttsPlaying) R.string.pause_read_aloud else R.string.start_read_aloud)) }
                IconButton(onLocationForward, enabled = canLocationForward) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.reader_location_forward)) }
            }
        }
    }
}

@Composable
private fun AutoScrollLiveControl(settings: ReaderSettings, actions: JingduActions, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 5.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond - 8).coerceAtLeast(12f))) }) { Icon(Icons.Default.Remove, stringResource(R.string.reader_auto_scroll_slow)) }
            Text(stringResource(R.string.reader_auto_scroll_speed_value, settings.autoScrollSpeedDpPerSecond.roundToInt()), style = MaterialTheme.typography.labelMedium)
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)) }) { Icon(Icons.Default.Pause, stringResource(R.string.reader_stop_auto_scroll)) }
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond + 8).coerceAtMost(320f))) }) { Icon(Icons.Default.Add, stringResource(R.string.reader_auto_scroll_fast)) }
        }
    }
}

@Composable
private fun ReaderReadingStatus(state: AppUiState, progressPercent: Int, chapter: String?, color: Color, background: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current; var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000) } }
    val locale = LocalConfiguration.current.locales[0]
    val clock = if (state.settings.showClock) SimpleDateFormat("HH:mm", locale).format(now) else null
    val battery = if (state.settings.showBattery) (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }?.let { "$it%" } else null
    val pieces = listOfNotNull(chapter?.take(24), "$progressPercent%", clock, battery)
    Surface(modifier.navigationBarsPadding().padding(bottom = 6.dp), color = background.copy(alpha = 0.80f), shape = MaterialTheme.shapes.small) {
        Text(pieces.joinToString(" · "), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
    }
}

@Composable
private fun ReaderSelectionBar(selection: ReaderSelection, onHighlight: (ReaderHighlightStyle) -> Unit, onNote: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(16.dp), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(Modifier.widthIn(max = 420.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(selection.excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderHighlightStyle.entries.forEach { style ->
                    val label = stringResource(when (style) {
                        ReaderHighlightStyle.YELLOW -> R.string.reader_highlight_yellow
                        ReaderHighlightStyle.GREEN -> R.string.reader_highlight_green
                        ReaderHighlightStyle.BLUE -> R.string.reader_highlight_blue
                        ReaderHighlightStyle.PINK -> R.string.reader_highlight_pink
                    })
                    TextButton({ onHighlight(style) }) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onNote) { Text(stringResource(R.string.reader_note)) }; TextButton(onCopy) { Text(stringResource(R.string.reader_copy)) }
                TextButton(onShare) { Text(stringResource(R.string.reader_share)) }; TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

private fun readerTextStyle(settings: ReaderSettings, color: Color, fontFamily: FontFamily): TextStyle = TextStyle(
    color = color, fontFamily = fontFamily,
    fontWeight = when (settings.fontWeight) { ReaderFontWeight.NORMAL -> FontWeight.Normal; ReaderFontWeight.MEDIUM -> FontWeight.Medium; ReaderFontWeight.SEMIBOLD -> FontWeight.SemiBold },
    fontSize = settings.fontSizeSp.sp, lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
    letterSpacing = (settings.fontSizeSp * settings.letterSpacingEm).sp,
    textAlign = if (settings.textAlignment == ReaderTextAlignment.START) TextAlign.Start else TextAlign.Justify,
    textIndent = TextIndent(firstLine = (settings.fontSizeSp * settings.firstLineIndentEm).sp),
)

private fun readerAnnotatedText(sourceStart: Long, sourceText: String, displayText: String, state: AppUiState): AnnotatedString = buildAnnotatedString {
    append(displayText); if (displayText.isEmpty()) return@buildAnnotatedString
    fun displayIndex(sourceAbsolute: Long): Int = utf16IndexForCodePoints(displayText, ChineseDisplayConverter.displayedCharsForSource(sourceText, displayText, (sourceAbsolute - sourceStart).coerceAtLeast(0))).coerceIn(0, displayText.length)
    val sourceEnd = sourceStart + sourceText.codePointCount(0, sourceText.length)
    state.annotations.forEach { annotation ->
        if (annotation.sourceEnd <= sourceStart || annotation.sourceStart >= sourceEnd) return@forEach
        val a = displayIndex(annotation.sourceStart); val b = displayIndex(annotation.sourceEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = highlightColor(annotation.style)), a, b)
    }
    if (state.tts.active && state.tts.rangeEnd > state.tts.rangeStart) {
        val a = displayIndex(state.tts.rangeStart); val b = displayIndex(state.tts.rangeEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = Color(0x5558A67A)), a, b)
    }
    if (state.settings.emphasizeHeadings) {
        var cursor = 0
        displayText.lineSequence().forEach { line ->
            val end = (cursor + line.length).coerceAtMost(displayText.length); val trimmed = line.trim()
            if (ReaderHeadingClassifier.isHeading(trimmed)) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), cursor, end)
            cursor = (end + 1).coerceAtMost(displayText.length)
        }
    }
    if (state.settings.paragraphSpacingEm > 0f) addStyle(ParagraphStyle(lineHeight = (state.settings.fontSizeSp * state.settings.lineHeightMultiplier).sp), 0, displayText.length)
}

private fun highlightColor(style: ReaderHighlightStyle): Color = when (style) {
    ReaderHighlightStyle.YELLOW -> Color(0x55FFD54F); ReaderHighlightStyle.GREEN -> Color(0x554CAF50)
    ReaderHighlightStyle.BLUE -> Color(0x5542A5F5); ReaderHighlightStyle.PINK -> Color(0x55EC407A)
}
private fun utf16IndexForCodePoints(text: String, codePoints: Long): Int = if (text.isEmpty()) 0 else text.offsetByCodePoints(0, codePoints.coerceIn(0, text.codePointCount(0, text.length).toLong()).toInt())
private fun readerBackground(palette: ReaderPalette): Color = when (palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.SEPIA -> Color(0xFFF3E5C8); ReaderPalette.NIGHT -> Color(0xFF151713); ReaderPalette.OLED -> Color.Black }
private fun readerTextColor(palette: ReaderPalette): Color = when (palette) { ReaderPalette.NIGHT -> Color(0xFFE8E5DA); ReaderPalette.OLED -> Color(0xFFE8E8E8); else -> Color(0xFF24241F) }
