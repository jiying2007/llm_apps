@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

private data class V3SelectionPayload(val range: ReaderSelectionRange, val clearNative: () -> Unit)

@Composable
internal fun ReaderScreenV3(
    state: AppUiState,
    actions: JingduActions,
    snackbar: SnackbarHostState,
    adaptiveLayout: ReaderAdaptiveLayout,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? Activity
    val book = state.currentBook ?: return
    val settings = state.settings
    val haptics = LocalHapticFeedback.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val touchExploration = accessibility.isTouchExplorationEnabled
    val fontFamily = rememberReaderFontFamily(context, settings)
    val stats = remember(context) { ReaderStatsStore(context) }
    val skim = remember(book.id) { ReaderSkimController(context, book.id) }
    var controlsVisible by rememberSaveable(book.id) { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var pageDirection by remember(book.id) { mutableIntStateOf(0) }
    var selection by remember(book.id) { mutableStateOf<V3SelectionPayload?>(null) }
    var twoStageAnchor by remember(book.id) { mutableStateOf<ReaderSelectionRange?>(null) }
    var twoStageDirection by remember(book.id) { mutableIntStateOf(0) }
    var noteDraft by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var skimFraction by remember { mutableFloatStateOf(if (state.length <= 0) 0f else state.position.toFloat() / state.length.toFloat()) }
    var skimDragging by remember { mutableStateOf(false) }
    var skimPreview by remember { mutableStateOf<ReaderSkimPreview?>(null) }
    var skimOrigin by remember { mutableLongStateOf(state.position) }
    var showSkimReturn by remember { mutableStateOf(false) }

    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
    val currentChapterIndex = remember(state.chapters, state.position) { state.chapters.indexOfLast { it.offset <= state.position } }
    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title

    DisposableEffect(book.id) {
        onDispose { skim.close() }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
            if (activity != null && !activity.isChangingConfigurations && !activity.isFinishing) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
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
            if (controlsVisible || state.panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(controlsVisible, state.panel, settings.controlsAutoHideMs) {
        if (controlsVisible && state.panel == null) {
            delay(settings.controlsAutoHideMs)
            controlsVisible = false
        }
    }
    LaunchedEffect(hudText) {
        if (hudText != null) { delay(1_050L); hudText = null }
    }
    LaunchedEffect(fraction, skimDragging) {
        if (!skimDragging) skimFraction = fraction
    }
    LaunchedEffect(skimDragging, skimFraction, state.chapters, settings) {
        if (!skimDragging) return@LaunchedEffect
        delay(70L)
        skimPreview = runCatching {
            skim.preview(skimFraction, skimOrigin, settings, state.chapters, stats.charsPerMinute())
        }.getOrNull()
    }

    fun tick() { if (settings.hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun previous() {
        selection?.clearNative?.invoke(); selection = null; tick()
        pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION) -1 else 0
        actions.onNavigatePrevious()
    }
    fun next() {
        selection?.clearNative?.invoke(); selection = null; tick()
        pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION) 1 else 0
        actions.onNavigateNext()
    }
    fun updateBrightness(delta: Float) {
        val value = (settings.readerBrightness + delta).coerceIn(0.03f, 1f)
        if (abs(value - settings.readerBrightness) >= 0.005f) {
            hudText = resources.getString(R.string.reader_brightness_hud, (value * 100).roundToInt())
            actions.onSettingsChanged(settings.copy(useSystemBrightness = false, readerBrightness = value))
        }
    }
    fun resizeFont(zoom: Float) {
        if (!settings.pinchFontEnabled || abs(zoom - 1f) < 0.04f) return
        val value = (settings.fontSizeSp * zoom).coerceIn(14f, 40f)
        if (abs(value - settings.fontSizeSp) >= 0.5f) {
            hudText = resources.getString(R.string.reader_font_hud, value.roundToInt())
            actions.onSettingsChanged(settings.copy(fontSizeSp = value, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
        }
    }
    fun acceptSelection(payload: V3SelectionPayload?) {
        if (payload == null) { selection = null; return }
        val anchor = twoStageAnchor
        if (anchor != null && twoStageDirection != 0) {
            val merged = ReaderSelectionController.extendAcrossBoundary(
                anchor,
                if (twoStageDirection < 0) payload.range.sourceStart else payload.range.sourceEnd,
                twoStageDirection < 0,
            ).copy(excerpt = listOf(anchor.excerpt, payload.range.excerpt).filter { it.isNotBlank() }.joinToString(" … ").take(800))
            selection = V3SelectionPayload(merged, payload.clearNative)
            twoStageAnchor = null
            twoStageDirection = 0
        } else selection = payload
        controlsVisible = true
    }

    val background = readerBackgroundV3(settings.palette)
    val textColor = readerTextColorV3(settings.palette)
    Box(Modifier.fillMaxSize().background(background)) {
        if (settings.readingMode == ReaderMode.CONTINUOUS && !state.cleanMode) {
            ContinuousReaderPageV3(
                state, actions, fontFamily, textColor, touchExploration,
                ::previous, ::next, { controlsVisible = !controlsVisible }, ::updateBrightness, ::resizeFont,
                { tick(); actions.onAddBookmark() }, ::acceptSelection,
            )
        } else {
            val slideAnimation = settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION && pageDirection != 0
            if (slideAnimation) {
                val direction = pageDirection
                key(state.position, state.pageText) {
                    val pageVisible = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = pageVisible,
                        enter = slideInHorizontally { direction * it } + fadeIn(),
                        exit = fadeOut(),
                        label = "reader-v3-page-in",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PagedReaderPageV3(
                            state.position, state.pageText, state, adaptiveLayout, fontFamily, textColor, touchExploration,
                            actions.onVisibleCharsChanged, ::previous, ::next, { controlsVisible = !controlsVisible },
                            ::updateBrightness, ::resizeFont, { tick(); actions.onAddBookmark() }, ::acceptSelection,
                        )
                    }
                }
            } else {
                PagedReaderPageV3(
                    state.position, state.pageText, state, adaptiveLayout, fontFamily, textColor, touchExploration,
                    actions.onVisibleCharsChanged, ::previous, ::next, { controlsVisible = !controlsVisible },
                    ::updateBrightness, ::resizeFont, { tick(); actions.onAddBookmark() }, ::acceptSelection,
                )
            }
        }

        if (settings.extraDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.extraDim.coerceIn(0f, 0.80f))))

        if (settings.focusRulerLines > 0) Box(
            Modifier.fillMaxWidth().height((settings.fontSizeSp * settings.lineHeightMultiplier * settings.focusRulerLines).dp)
                .align(Alignment.Center).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
        )

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBarV3(state, currentChapter, actions) { more = true }
        }
        if (controlsVisible && more) Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp)) {
            ReaderMoreMenuV3(state, actions) { more = false }
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBarV3(
                state = state,
                chapter = currentChapter,
                fraction = skimFraction,
                skimPreview = skimPreview,
                skimDragging = skimDragging,
                showSkimReturn = showSkimReturn,
                canLocationBack = canLocationBack,
                canLocationForward = canLocationForward,
                onLocationBack = onLocationBack,
                onLocationForward = onLocationForward,
                onOpenQuick = { actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) },
                onTts = actions.onToggleTts,
                onAutoPage = actions.onToggleAutoPaging,
                onFractionChange = { value ->
                    if (!skimDragging) { skimOrigin = state.position; skimDragging = true; showSkimReturn = false }
                    skimFraction = value
                },
                onFractionCommit = {
                    skimDragging = false
                    showSkimReturn = true
                    actions.onSeekFraction(skimFraction)
                },
                onReturnSkim = {
                    actions.onJump(skimOrigin)
                    skimPreview = null
                    showSkimReturn = false
                },
            )
        }
        if (!controlsVisible && settings.showReadingStatus) ReaderReadingStatusV3(state, currentChapterIndex, textColor, background, stats, Modifier.align(Alignment.BottomCenter))
        if (state.autoScrolling && !controlsVisible) AutoScrollLiveControlV3(settings, actions, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp))

        selection?.let { selected ->
            ReaderSelectionBarV3(
                selection = selected.range,
                settings = settings,
                onHighlight = { style ->
                    actions.onAddAnnotation(selected.range.sourceStart, selected.range.sourceEnd, ReaderAnnotationKind.HIGHLIGHT, style, "", selected.range.excerpt)
                    selected.clearNative(); selection = null
                },
                onNote = { noteDraft = ""; showNoteDialog = true },
                onCopy = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Jingdu", selected.range.excerpt))
                    selected.clearNative(); selection = null
                },
                onShare = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selected.range.excerpt) }, null))
                    selected.clearNative(); selection = null
                },
                onLookup = {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_PROCESS_TEXT).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_PROCESS_TEXT, selected.range.excerpt)
                            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        }, null))
                    }
                },
                onExtendPrevious = if (settings.twoStageSelectionEnabled && settings.readingMode == ReaderMode.PAGED) ({
                    twoStageAnchor = selected.range; twoStageDirection = -1; selected.clearNative(); selection = null; previous()
                }) else null,
                onExtendNext = if (settings.twoStageSelectionEnabled && settings.readingMode == ReaderMode.PAGED) ({
                    twoStageAnchor = selected.range; twoStageDirection = 1; selected.clearNative(); selection = null; next()
                }) else null,
                onDismiss = { selected.clearNative(); selection = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        hudText?.let { text -> ReaderHudV3(text, Modifier.align(Alignment.Center)) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible) 112.dp else 24.dp))
    }

    if (showNoteDialog && selection != null) AlertDialog(
        onDismissRequest = { showNoteDialog = false },
        title = { Text(stringResource(R.string.reader_note)) },
        text = { OutlinedTextField(noteDraft, { noteDraft = it.take(2000) }, label = { Text(stringResource(R.string.reader_note_hint)) }) },
        confirmButton = { TextButton(onClick = {
            selection?.let { selected ->
                actions.onAddAnnotation(selected.range.sourceStart, selected.range.sourceEnd, ReaderAnnotationKind.NOTE, ReaderHighlightStyle.YELLOW, noteDraft, selected.range.excerpt)
                selected.clearNative()
            }
            selection = null; showNoteDialog = false
        }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton({ showNoteDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(state.position) { if (pageDirection != 0) { delay(220); pageDirection = 0 } }
}

@Composable
private fun PagedReaderPageV3(
    sourceStart: Long,
    sourceText: String,
    state: AppUiState,
    adaptiveLayout: ReaderAdaptiveLayout,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onVisibleCharsChanged: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (V3SelectionPayload?) -> Unit,
) {
    val context = LocalContext.current
    val settings = state.settings
    val presented by produceState<ReaderPresentedText?>(null, sourceText, settings.chineseMode, settings.chineseOverrides, settings.compressBlankLines, settings.paragraphSpacingEm) {
        value = withContext(Dispatchers.Default) { ReaderPresentationPipeline.present(sourceText, settings) }
    }
    val presentedValue = presented ?: return
    val displayText = presentedValue.displayText
    val map = presentedValue.map
    val spec = remember(settings) { ReaderTypographySpec.from(settings) }
    val style = spec.composeTextStyle(textColor, fontFamily)
    val typeface = remember(settings.typeface, settings.customFontId, settings.fontWeight) { spec.androidTypeface(context) }
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)
    val columns = when (settings.wideColumns) {
        ReaderWideColumns.SINGLE -> 1
        ReaderWideColumns.DOUBLE -> if (adaptiveLayout.width >= ReaderAdaptiveWidth.MEDIUM && !adaptiveLayout.tabletop) 2 else 1
        ReaderWideColumns.AUTO -> if (adaptiveLayout.prefersTwoColumns) 2 else 1
    }
    val snapshot by produceState<PageLayoutSnapshot?>(null, sourceText, displayText, widthPx, heightPx, columns, spec.fingerprint) {
        if (widthPx > 0 && heightPx > 0 && displayText.isNotEmpty()) {
            value = withContext(Dispatchers.Default) { ReaderPageLayoutCache.measure(sourceText, displayText, widthPx, heightPx, columns, settings, density, typeface, map) }
        }
    }
    val snapshotValue = snapshot
    LaunchedEffect(snapshotValue) { snapshotValue?.sourceCodePoints?.takeIf { it >= ReaderController.MIN_PAGE_CHARS }?.let(onVisibleCharsChanged) }

    val visibleEnd = snapshotValue?.displayedEndUtf16?.coerceIn(0, displayText.length) ?: 0
    val visibleText = remember(displayText, visibleEnd) {
        if (visibleEnd <= 0) "" else displayText.substring(0, visibleEnd)
    }
    val annotated = remember(sourceStart, visibleText, state.annotations, state.tts, settings.emphasizeHeadings, spec.fingerprint) {
        ReaderSelectionController.annotatedForSelection(sourceStart, readerAnnotatedTextV3(sourceStart, visibleText, map, state), map)
    }
    val selectionState = rememberSelectionState()
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        onSelection(range?.let { V3SelectionPayload(it) { selectionState.clear() } })
    }
    val semantics = Modifier.readerAccessibilityActionsV3(onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous), stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls), stringResource(R.string.reader_access_bookmark))
    val gestures = if (touchExploration) Modifier else Modifier.readerGesturesV3(settings, widthPx, heightPx, systemLeft, systemRight, onPrevious, onNext, onToggleControls, onBrightnessDelta, onBookmark)
        .pointerInput(settings.pinchFontEnabled) { if (settings.pinchFontEnabled) detectTransformGestures { _, _, zoom, _ -> onResizeFont(zoom) } }

    SelectionContainer(state = selectionState) {
        Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures), contentAlignment = Alignment.TopCenter) {
            if (columns == 2 && snapshotValue != null && annotated.isNotEmpty()) {
                val firstEnd = snapshotValue.firstColumnEndUtf16.coerceIn(0, annotated.length)
                Row(Modifier.widthIn(max = 1200.dp).fillMaxHeight().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Text(annotated.subSequence(0, firstEnd), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip)
                    Text(annotated.subSequence(firstEnd, annotated.length), Modifier.weight(1f).fillMaxHeight(), style = style, overflow = TextOverflow.Clip)
                }
            } else if (snapshotValue != null && annotated.isNotEmpty()) {
                Text(annotated, Modifier.fillMaxHeight().widthIn(max = 760.dp).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp), style = style, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun ContinuousReaderPageV3(
    state: AppUiState,
    actions: JingduActions,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (V3SelectionPayload?) -> Unit,
) {
    val context = LocalContext.current
    val book = state.currentBook ?: return
    val settings = state.settings
    val engine = remember(book.id) { ReaderViewportEngine(context, book.id) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)
    var window by remember(book.id) { mutableStateOf<ReaderDisplayWindow?>(null) }
    var layoutResult by remember(book.id) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var loading by remember(book.id) { mutableStateOf(false) }
    var lastCommitted by remember(book.id) { mutableLongStateOf(state.position) }
    val localPosition = remember(book.id) { AtomicLong(state.position) }

    suspend fun loadAround(target: Long) {
        if (loading) return
        loading = true
        try {
            val next = withContext(Dispatchers.IO) { engine.readAround(target, settings) }
            window = next
            localPosition.set(target.coerceIn(0L, (next.documentLength - 1).coerceAtLeast(0L)))
            layoutResult = null
        } finally { loading = false }
    }
    DisposableEffect(engine) { onDispose { engine.close() } }
    LaunchedEffect(book.id, settings.chineseMode, settings.chineseOverrides, settings.compressBlankLines, settings.paragraphSpacingEm) {
        withContext(Dispatchers.IO) { engine.clear() }
        loadAround(state.position)
        withContext(Dispatchers.IO) { engine.prefetch(state.position, settings) }
    }
    LaunchedEffect(state.tts.offset, state.tts.active) {
        if (state.tts.active && state.tts.offset >= 0 && abs(state.tts.offset - localPosition.get()) > 128) loadAround(state.tts.offset)
    }
    LaunchedEffect(window, layoutResult) {
        val w = window ?: return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        if (w.displayText.isEmpty()) return@LaunchedEffect
        val utf = utf16IndexV3(w.displayText, w.map.displayForSource((localPosition.get() - w.start).coerceAtLeast(0)))
        val line = layout.getLineForOffset(utf.coerceIn(0, (w.displayText.length - 1).coerceAtLeast(0)))
        scrollState.scrollTo(layout.getLineTop(line).roundToInt().coerceIn(0, scrollState.maxValue))
    }
    LaunchedEffect(scrollState, layoutResult, window, viewportHeight, state.autoScrolling) {
        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }.distinctUntilChanged().collect { (y, scrolling) ->
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
            val nearBottom = scrollState.maxValue > 0 && scrollState.maxValue - y <= edge && w.start + w.map.sourceCodePoints < w.documentLength - 1
            if (!loading && !scrolling && (nearTop || nearBottom)) loadAround(absolute)
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

    val w = window
    val display = w?.displayText.orEmpty()
    val start = w?.start ?: state.position
    val map = w?.map ?: SourceDisplayMap.between("", "")
    val spec = remember(settings) { ReaderTypographySpec.from(settings) }
    val style = spec.composeTextStyle(textColor, fontFamily)
    val annotated = remember(start, display, state.annotations, state.tts, settings.emphasizeHeadings, spec.fingerprint) {
        ReaderSelectionController.annotatedForSelection(start, readerAnnotatedTextV3(start, display, map, state), map)
    }
    val selectionState = rememberSelectionState()
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        onSelection(range?.let { V3SelectionPayload(it) { selectionState.clear() } })
    }
    val semantics = Modifier.readerAccessibilityActionsV3(onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous), stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls), stringResource(R.string.reader_access_bookmark))
    val gestures = if (touchExploration) Modifier else Modifier.readerGesturesV3(settings, widthPx, viewportHeight, systemLeft, systemRight, onPrevious, onNext, onToggleControls, onBrightnessDelta, onBookmark) {
        if (state.autoScrolling) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
    }.pointerInput(settings.pinchFontEnabled) { if (settings.pinchFontEnabled) detectTransformGestures { _, _, zoom, _ -> onResizeFont(zoom) } }

    SelectionContainer(state = selectionState) {
        Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; viewportHeight = it.height }.then(semantics).then(gestures), contentAlignment = Alignment.TopCenter) {
            Text(annotated, Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp), style = style, overflow = TextOverflow.Clip, onTextLayout = { layoutResult = it })
            if (loading && display.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

private fun readerAnnotatedTextV3(sourceStart: Long, displayText: String, map: SourceDisplayMap, state: AppUiState): AnnotatedString = buildAnnotatedString {
    append(displayText)
    if (displayText.isEmpty()) return@buildAnnotatedString
    fun displayIndex(sourceAbsolute: Long): Int = utf16IndexV3(displayText, map.displayForSource((sourceAbsolute - sourceStart).coerceAtLeast(0))).coerceIn(0, displayText.length)
    val sourceEnd = sourceStart + map.sourceCodePoints
    state.annotations.forEach { annotation ->
        if (annotation.sourceEnd <= sourceStart || annotation.sourceStart >= sourceEnd) return@forEach
        val a = displayIndex(annotation.sourceStart)
        val b = displayIndex(annotation.sourceEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = highlightColorV3(annotation.style)), a, b)
    }
    if (state.tts.active && state.tts.rangeEnd > state.tts.rangeStart) {
        val a = displayIndex(state.tts.rangeStart)
        val b = displayIndex(state.tts.rangeEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = Color(0x5558A67A)), a, b)
    }
    if (state.settings.emphasizeHeadings) {
        var cursor = 0
        displayText.lineSequence().forEach { line ->
            val end = (cursor + line.length).coerceAtMost(displayText.length)
            if (ReaderHeadingClassifier.isHeading(line.trim())) addStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), cursor, end)
            cursor = (end + 1).coerceAtMost(displayText.length)
        }
    }
    if (state.settings.paragraphSpacingEm > 0f) {
        val gap = (state.settings.fontSizeSp * state.settings.paragraphSpacingEm).coerceAtLeast(1f).sp
        displayText.forEachIndexed { index, char -> if (char == ReaderTypographySpec.PARAGRAPH_SPACER) addStyle(ParagraphStyle(lineHeight = gap), index, index + 1) }
    }
}

private fun Modifier.readerGesturesV3(
    settings: ReaderSettings,
    widthPx: Int,
    heightPx: Int,
    systemLeftInsetPx: Int,
    systemRightInsetPx: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onBookmark: () -> Unit,
    onAnyTouch: () -> Unit = {},
): Modifier = pointerInput(settings, widthPx, heightPx, systemLeftInsetPx, systemRightInsetPx) {
    val swipe = 52.dp.toPx()
    val tapSlop = 14.dp.toPx()
    var lastCenterTapAt = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onAnyTouch()
        var last = down
        var consumedByChild = down.isConsumed
        var maxPointers = 1
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            maxPointers = maxOf(maxPointers, event.changes.size)
            if (event.changes.any { it.isConsumed }) consumedByChild = true
            event.changes.firstOrNull { it.id == down.id }?.let { last = it }
        } while (last.pressed)
        if (consumedByChild || maxPointers > 1) return@awaitEachGesture
        val delta = last.position - down.position
        val duration = last.uptimeMillis - down.uptimeMillis
        val edgeGuard = 8.dp.toPx()
        if (settings.brightnessGestureEnabled && widthPx > 0 && down.position.x >= systemLeftInsetPx + edgeGuard && down.position.x <= systemLeftInsetPx + widthPx * 0.14f && abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe) {
            onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f); return@awaitEachGesture
        }
        if (settings.swipePagingEnabled && down.position.x > systemLeftInsetPx + edgeGuard && down.position.x < widthPx - systemRightInsetPx - edgeGuard && abs(delta.x) >= swipe && abs(delta.x) > abs(delta.y) * 1.25f) {
            var forward = delta.x < 0
            if (settings.reversePagingGestures) forward = !forward
            if (forward) onNext() else onPrevious()
            return@awaitEachGesture
        }
        if (duration <= 360 && delta.getDistance() <= tapSlop && widthPx > 0) {
            if (down.position.x <= systemLeftInsetPx + edgeGuard || down.position.x >= widthPx - systemRightInsetPx - edgeGuard) return@awaitEachGesture
            val edge = when (settings.tapZonePreset) {
                ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> widthPx * settings.tapZoneEdgeFraction
                ReaderTapZonePreset.RIGHT_HANDED -> widthPx * 0.22f
                ReaderTapZonePreset.LEFT_HANDED -> widthPx * 0.32f
            }
            when {
                down.position.x < edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onNext() else onPrevious()
                down.position.x > widthPx - edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onPrevious() else onNext()
                else -> {
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
                    val tapAt = last.uptimeMillis
                    if (doubleAction != ReaderGestureAction.NONE && lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {
                        lastCenterTapAt = 0L
                        dispatch(doubleAction)
                    } else {
                        lastCenterTapAt = tapAt
                        dispatch(centerAction)
                    }
                }
            }
        }
    }
}

private fun Modifier.readerAccessibilityActionsV3(
    previous: () -> Unit, next: () -> Unit, controls: () -> Unit, bookmark: () -> Unit,
    surfaceLabel: String, previousLabel: String, nextLabel: String, controlsLabel: String, bookmarkLabel: String,
): Modifier = semantics {
    contentDescription = surfaceLabel
    customActions = listOf(
        CustomAccessibilityAction(previousLabel) { previous(); true },
        CustomAccessibilityAction(nextLabel) { next(); true },
        CustomAccessibilityAction(controlsLabel) { controls(); true },
        CustomAccessibilityAction(bookmarkLabel) { bookmark(); true },
    )
}

@Composable
private fun ReaderTopBarV3(state: AppUiState, chapter: String?, actions: JingduActions, onMore: () -> Unit) {
    val book = state.currentBook ?: return
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            navigationIcon = { IconButton(actions.onBackToLibrary) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_library)) } },
            title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(book.name.removeSuffix(".txt").removeSuffix(".TXT"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                chapter?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
            } },
            actions = {
                TextButton({ actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) }) { Text("Aa") }
                IconButton({ actions.onOpenPanel(ReaderPanel.CHAPTERS) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.chapters)) }
                IconButton(onMore) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_reading_tools)) }
            },
        )
    }
}

@Composable
private fun ReaderMoreMenuV3(state: AppUiState, actions: JingduActions, onDismiss: () -> Unit) {
    DropdownMenu(true, onDismissRequest = onDismiss) {
        fun close(action: () -> Unit) { onDismiss(); action() }
        DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reader_annotations)) }, { close { actions.onOpenPanel(ReaderPanel.ANNOTATIONS) } }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reader_reading_map)) }, { close { actions.onOpenPanel(ReaderPanel.READING_MAP); actions.onEnsureChapters() } }, leadingIcon = { Icon(Icons.Outlined.Map, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reader_reading_history)) }, { close { actions.onOpenPanel(ReaderPanel.READING_HISTORY) } }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
        DropdownMenuItem({ Text(stringResource(R.string.txt_doctor)) }, { close { actions.onOpenPanel(ReaderPanel.DOCTOR) } }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) })
        DropdownMenuItem({ Text(stringResource(R.string.smart_clean4)) }, { close { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) } }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) })
        DropdownMenuItem({ Text(stringResource(R.string.clean)) }, { close { actions.onOpenPanel(ReaderPanel.CLEAN) } }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
        DropdownMenuItem({ Text(stringResource(R.string.reading_settings)) }, { close { actions.onOpenPanel(ReaderPanel.SETTINGS) } }, leadingIcon = { Icon(Icons.Default.Settings, null) })
        if (!state.cleanMode) DropdownMenuItem({ Text(stringResource(R.string.reader_access_bookmark)) }, { close(actions.onAddBookmark) }, leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) })
    }
}

@Composable
private fun ReaderBottomBarV3(
    state: AppUiState,
    chapter: String?,
    fraction: Float,
    skimPreview: ReaderSkimPreview?,
    skimDragging: Boolean,
    showSkimReturn: Boolean,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
    onOpenQuick: () -> Unit,
    onTts: () -> Unit,
    onAutoPage: () -> Unit,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
    onReturnSkim: () -> Unit,
) {
    val progressDescription = stringResource(R.string.reading_progress)
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (skimDragging || (showSkimReturn && skimPreview != null)) ReaderSkimPreviewCardV3(skimPreview, showSkimReturn, onReturnSkim)
            else Text(chapter ?: "", Modifier.align(Alignment.CenterHorizontally), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            ReaderChapterTicksV3(state, fraction)
            Slider(fraction, onFractionChange, onValueChangeFinished = onFractionCommit, modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription })
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
private fun ReaderChapterTicksV3(state: AppUiState, fraction: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val tickOffsets = remember(state.chapters, state.length) {
        if (state.length <= 0 || state.chapters.isEmpty()) emptyList()
        else {
            val stride = ((state.chapters.size + MAX_CHAPTER_TICKS - 1) / MAX_CHAPTER_TICKS).coerceAtLeast(1)
            state.chapters.filterIndexed { index, _ -> index % stride == 0 }.map { it.offset }.take(MAX_CHAPTER_TICKS)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawLine(outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        if (state.length > 0) tickOffsets.forEach { offset ->
            val x = (offset.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f) * size.width
            drawLine(primary.copy(alpha = 0.55f), Offset(x, 1f), Offset(x, size.height - 1f), strokeWidth = 1.dp.toPx())
        }
        val x = fraction.coerceIn(0f, 1f) * size.width
        drawCircle(primary, radius = 3.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

@Composable
private fun ReaderSkimPreviewCardV3(preview: ReaderSkimPreview?, showReturn: Boolean, onReturn: () -> Unit) {
    if (preview == null) return
    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            preview.chapter?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(preview.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reader_chapter_progress_value, preview.chapterProgressPercent), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.reader_book_progress_value, preview.bookProgressPercent), style = MaterialTheme.typography.labelSmall)
                preview.chapterRemainingMinutes?.let { Text(stringResource(R.string.reader_chapter_remaining, it), style = MaterialTheme.typography.labelSmall) }
            }
            if (showReturn) TextButton(onReturn, Modifier.align(Alignment.End)) { Text(stringResource(R.string.reader_skim_return)) }
        }
    }
}

@Composable
private fun ReaderReadingStatusV3(state: AppUiState, chapterIndex: Int, color: Color, background: Color, stats: ReaderStatsStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000) } }
    val locale = LocalConfiguration.current.locales[0]
    val clock = if (state.settings.showClock) SimpleDateFormat("HH:mm", locale).format(now) else null
    val battery = if (state.settings.showBattery) (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }?.let { "$it%" } else null
    val chapter = state.chapters.getOrNull(chapterIndex)
    val chapterEnd = state.chapters.getOrNull(chapterIndex + 1)?.offset ?: state.length
    val chapterProgress = chapter?.let { (((state.position - it.offset).coerceAtLeast(0).toDouble() / (chapterEnd - it.offset).coerceAtLeast(1).toDouble()) * 100).roundToInt().coerceIn(0, 100) }
    val bookProgress = if (state.length <= 0) 0 else ((state.position.toDouble() / state.length.toDouble()) * 100).roundToInt().coerceIn(0, 100)
    val remaining = stats.remainingMinutes(state.position, state.length)
    val pieces = buildList {
        chapter?.title?.take(18)?.let(::add)
        chapterProgress?.let { add(resources.getString(R.string.reader_chapter_progress_value, it)) }
        add(resources.getString(R.string.reader_book_progress_value, bookProgress))
        remaining?.let { add(resources.getString(R.string.reader_book_remaining, it)) }
        clock?.let(::add); battery?.let(::add)
    }
    Surface(modifier.navigationBarsPadding().padding(bottom = 6.dp), color = background.copy(alpha = 0.80f), shape = MaterialTheme.shapes.small) {
        Text(pieces.joinToString(" · "), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun AutoScrollLiveControlV3(settings: ReaderSettings, actions: JingduActions, modifier: Modifier = Modifier) {
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
private fun ReaderSelectionBarV3(
    selection: ReaderSelectionRange,
    settings: ReaderSettings,
    onHighlight: (ReaderHighlightStyle) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onLookup: () -> Unit,
    onExtendPrevious: (() -> Unit)?,
    onExtendNext: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.padding(16.dp), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(Modifier.widthIn(max = 460.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(selection.excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderHighlightStyle.entries.forEach { style -> TextButton({ onHighlight(style) }) { Text(highlightLabelV3(style)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onNote) { Text(stringResource(R.string.reader_note)) }
                TextButton(onCopy) { Text(stringResource(R.string.reader_copy)) }
                TextButton(onShare) { Text(stringResource(R.string.reader_share)) }
                if (settings.dictionaryProcessTextEnabled) TextButton(onLookup) { Text(stringResource(R.string.reader_lookup)) }
            }
            if (onExtendPrevious != null || onExtendNext != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onExtendPrevious?.let { TextButton(it) { Text(stringResource(R.string.reader_selection_extend_previous)) } }
                onExtendNext?.let { TextButton(it) { Text(stringResource(R.string.reader_selection_extend_next)) } }
            }
            TextButton(onDismiss, Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun highlightLabelV3(style: ReaderHighlightStyle): String = stringResource(when (style) {
    ReaderHighlightStyle.YELLOW -> R.string.reader_highlight_yellow
    ReaderHighlightStyle.GREEN -> R.string.reader_highlight_green
    ReaderHighlightStyle.BLUE -> R.string.reader_highlight_blue
    ReaderHighlightStyle.PINK -> R.string.reader_highlight_pink
})

@Composable
private fun ReaderHudV3(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f), contentColor = MaterialTheme.colorScheme.inverseOnSurface, shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
    }
}

private const val MAX_CHAPTER_TICKS = 96
private const val AUTO_SCROLL_COMMIT_CHARS = 512L

private fun highlightColorV3(style: ReaderHighlightStyle): Color = when (style) {
    ReaderHighlightStyle.YELLOW -> Color(0x55FFD54F)
    ReaderHighlightStyle.GREEN -> Color(0x554CAF50)
    ReaderHighlightStyle.BLUE -> Color(0x5542A5F5)
    ReaderHighlightStyle.PINK -> Color(0x55EC407A)
}

private fun utf16IndexV3(text: String, codePoints: Long): Int = if (text.isEmpty()) 0 else text.offsetByCodePoints(0, codePoints.coerceIn(0, text.codePointCount(0, text.length).toLong()).toInt())
private fun readerBackgroundV3(palette: ReaderPalette): Color = when (palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.SEPIA -> Color(0xFFF3E5C8); ReaderPalette.NIGHT -> Color(0xFF151713); ReaderPalette.OLED -> Color.Black }
private fun readerTextColorV3(palette: ReaderPalette): Color = when (palette) { ReaderPalette.NIGHT -> Color(0xFFE8E5DA); ReaderPalette.OLED -> Color(0xFFE8E8E8); else -> Color(0xFF24241F) }
