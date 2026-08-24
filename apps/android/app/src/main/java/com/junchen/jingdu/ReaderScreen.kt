@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ReaderScreen(
    state: AppUiState,
    actions: JingduActions,
    snackbar: SnackbarHostState,
    canLocationBack: Boolean = false,
    canLocationForward: Boolean = false,
    onLocationBack: () -> Unit = {},
    onLocationForward: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    val book = state.currentBook ?: return
    val settings = state.settings
    val paceStore = remember { ReadingPaceStore(context) }
    var controlsVisible by rememberSaveable(book.id) { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var servicePlaying by remember(book.id) { mutableStateOf(false) }
    var serviceActive by remember(book.id) { mutableStateOf(false) }
    var ttsOffset by remember(book.id) { mutableLongStateOf(-1L) }
    var ttsNextOffset by remember(book.id) { mutableLongStateOf(-1L) }
    var pageDirection by remember(book.id) { mutableIntStateOf(0) }
    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
    val progressPercent = (fraction * 100).roundToInt()
    val remainingMinutes = remember(state.position, state.length) { paceStore.remainingMinutes(state.position, state.length) }

    LaunchedEffect(book.id, state.position) { paceStore.resetSession(book.id, state.position) }

    DisposableEffect(activity) {
        val window = activity?.window
        onDispose {
            ReaderInteractionRuntime.backgroundTtsPlaying = false
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                val attributes = window.attributes
                attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attributes
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
            val attributes = window.attributes
            attributes.screenBrightness = if (settings.useSystemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.readerBrightness.coerceIn(0.05f, 1f)
            window.attributes = attributes
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

    DisposableEffect(activity, settings.autoScrollEnabled) {
        val lifecycle = lifecycleOwner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && settings.autoScrollEnabled) {
                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(state.sleepMinutes, settings.autoScrollEnabled) {
        if (state.sleepMinutes > 0 && settings.autoScrollEnabled) {
            delay(state.sleepMinutes * 60_000L)
            actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
        }
    }

    fun stopBackgroundTts() {
        if (serviceActive) context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP))
        servicePlaying = false
        serviceActive = false
        ReaderInteractionRuntime.backgroundTtsPlaying = false
        ttsOffset = -1L
        ttsNextOffset = -1L
    }

    fun stopMotionForManualNavigation() {
        stopBackgroundTts()
        if (state.ttsPlaying) actions.onToggleTts()
        if (settings.autoScrollEnabled) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
    }

    fun manualPrevious() {
        stopMotionForManualNavigation()
        paceStore.markManualPage(book.id, state.position)
        pageDirection = -1
        actions.onNavigatePrevious()
    }

    fun manualNext() {
        stopMotionForManualNavigation()
        paceStore.markManualPage(book.id, state.position)
        pageDirection = 1
        actions.onNavigateNext()
    }

    fun manualSeek(value: Float) {
        stopMotionForManualNavigation()
        pageDirection = 0
        actions.onSeekFraction(value)
    }

    fun toggleAutoScroll() {
        if (state.cleanMode) return
        if (settings.autoScrollEnabled) {
            actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
        } else {
            stopBackgroundTts()
            if (state.ttsPlaying) actions.onToggleTts()
            if (state.autoPaging) actions.onToggleAutoPaging()
            actions.onSettingsChanged(settings.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = true))
            controlsVisible = false
        }
    }

    DisposableEffect(context, book.id, state.panel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != TtsPlaybackService.ACTION_STATE) return
                val active = intent.getBooleanExtra(TtsPlaybackService.EXTRA_ACTIVE, false)
                val playing = intent.getBooleanExtra(TtsPlaybackService.EXTRA_PLAYING, false)
                val offset = intent.getLongExtra(TtsPlaybackService.EXTRA_OFFSET, -1L)
                serviceActive = active
                servicePlaying = playing
                ReaderInteractionRuntime.backgroundTtsPlaying = playing
                ttsOffset = offset
                ttsNextOffset = intent.getLongExtra(TtsPlaybackService.EXTRA_NEXT_OFFSET, -1L)
                if (active && state.panel == null && offset >= 0 && offset != state.position) actions.onSyncTtsPosition(offset)
            }
        }
        val filter = IntentFilter(TtsPlaybackService.ACTION_STATE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STATE))
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            ReaderInteractionRuntime.backgroundTtsPlaying = false
        }
    }

    LaunchedEffect(state.sleepMinutes, serviceActive) {
        if (serviceActive) context.startService(
            Intent(context, TtsPlaybackService::class.java)
                .setAction(TtsPlaybackService.ACTION_SLEEP)
                .putExtra(TtsPlaybackService.EXTRA_MINUTES, state.sleepMinutes),
        )
    }

    fun startBackgroundTts() {
        if (settings.autoScrollEnabled) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
        if (state.cleanMode) { actions.onToggleTts(); return }
        if (state.autoPaging) actions.onToggleAutoPaging()
        val repository = BookRepository(context)
        val source = repository.list().firstOrNull { it.id == book.id }
        if (source == null) { actions.onToggleTts(); return }
        val file = repository.normalizedFile(source)
        val intent = Intent(context, TtsPlaybackService::class.java)
            .setAction(TtsPlaybackService.ACTION_START)
            .putExtra(TtsPlaybackService.EXTRA_PATH, file.absolutePath)
            .putExtra(TtsPlaybackService.EXTRA_BOOK_ID, book.id)
            .putExtra(TtsPlaybackService.EXTRA_TITLE, stripTxt(book.name))
            .putExtra(TtsPlaybackService.EXTRA_OFFSET, state.position)
            .putExtra(TtsPlaybackService.EXTRA_RATE, settings.ttsRate)
            .putExtra(TtsPlaybackService.EXTRA_PITCH, settings.ttsPitch)
            .putExtra(TtsPlaybackService.EXTRA_VOICE, settings.ttsVoiceName)
        serviceActive = true
        servicePlaying = true
        ReaderInteractionRuntime.backgroundTtsPlaying = true
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun toggleBackgroundTts() {
        if (settings.autoScrollEnabled) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
        if (state.cleanMode) { actions.onToggleTts(); return }
        if (!serviceActive) startBackgroundTts()
        else context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_TOGGLE))
    }

    val anyTtsPlaying = servicePlaying || state.ttsPlaying
    val pageBackground = readerBackground(settings.palette)
    val pageTextColor = readerTextColor(settings.palette)

    Box(Modifier.fillMaxSize().background(pageBackground)) {
        if (settings.readingMode == ReaderMode.CONTINUOUS && !state.cleanMode) {
            ContinuousReaderPage(
                state = state,
                actions = actions,
                onPrevious = ::manualPrevious,
                onNext = ::manualNext,
                onToggleControls = { controlsVisible = !controlsVisible },
                followExternalPosition = anyTtsPlaying,
                onPauseAutoScroll = {
                    if (settings.autoScrollEnabled) {
                        actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
                        controlsVisible = true
                    }
                },
                textColor = pageTextColor,
            )
        } else {
            AnimatedContent(
                targetState = state.position to state.pageText,
                transitionSpec = {
                    if (settings.pageAnimation == ReaderPageAnimation.SLIDE && pageDirection != 0) {
                        (slideInHorizontally { width -> pageDirection * width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -pageDirection * width } + fadeOut())
                    } else fadeIn() togetherWith fadeOut()
                },
                label = "reader-page",
                modifier = Modifier.fillMaxSize(),
            ) { (targetPosition, targetText) ->
                PagedReaderPage(
                    text = targetText,
                    settings = settings,
                    onVisibleCharsChanged = actions.onVisibleCharsChanged,
                    onPrevious = ::manualPrevious,
                    onNext = ::manualNext,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    textColor = pageTextColor,
                    ttsHighlight = servicePlaying && ttsOffset == targetPosition,
                    ttsChunkSourceChars = (ttsNextOffset - ttsOffset).coerceAtLeast(0),
                )
            }
        }

        if (settings.focusRulerLines > 0) {
            val density = LocalDensity.current
            val height = with(density) { (settings.fontSizeSp.sp.toDp() * settings.lineHeightMultiplier * settings.focusRulerLines) }
            Box(
                Modifier.fillMaxWidth().height(height).align(Alignment.Center).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)),
            )
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(state, actions) { more = it }
        }
        if (controlsVisible && more) {
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp)) {
                ReaderMoreMenu(state, actions, onDismiss = { more = false })
            }
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(
                fraction = fraction,
                ttsPlaying = anyTtsPlaying,
                autoPaging = state.autoPaging,
                autoScrolling = settings.autoScrollEnabled,
                autoScrollAvailable = !state.cleanMode,
                canLocationBack = canLocationBack,
                canLocationForward = canLocationForward,
                onLocationBack = { stopMotionForManualNavigation(); pageDirection = 0; onLocationBack() },
                onLocationForward = { stopMotionForManualNavigation(); pageDirection = 0; onLocationForward() },
                onPrevious = ::manualPrevious,
                onNext = ::manualNext,
                onSeek = ::manualSeek,
                onTts = ::toggleBackgroundTts,
                onAutoScroll = ::toggleAutoScroll,
            )
        }

        if (!controlsVisible && settings.showReadingStatus) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
                color = pageBackground.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = if (settings.autoScrollEnabled) stringResource(R.string.reader_auto_scroll_running, settings.autoScrollSpeedDpPerSecond.roundToInt())
                    else remainingMinutes?.let { stringResource(R.string.reader_status_remaining, progressPercent, it) }
                        ?: stringResource(R.string.reader_status_progress, progressPercent),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = pageTextColor.copy(alpha = 0.72f),
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible) 92.dp else 28.dp),
        )
    }

    LaunchedEffect(state.position) {
        if (pageDirection != 0) {
            delay(260L)
            pageDirection = 0
        }
    }
}

@Composable
private fun ReaderTopBar(state: AppUiState, actions: JingduActions, onMore: (Boolean) -> Unit) {
    val book = state.currentBook ?: return
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            navigationIcon = { IconButton(onClick = actions.onBackToLibrary) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_library)) } },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stripTxt(book.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
                    Text(if (state.cleanMode) stringResource(R.string.clean_preview) else stringResource(R.string.reader_status, (fraction * 100).roundToInt(), book.encoding), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                IconButton(onClick = { actions.onOpenPanel(ReaderPanel.SEARCH) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.full_text_search)) }
                IconButton(onClick = { actions.onOpenPanel(ReaderPanel.CHAPTERS) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.chapters)) }
                IconButton(onClick = { onMore(true) }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_reading_tools)) }
            },
        )
    }
}

@Composable
private fun ReaderMoreMenu(state: AppUiState, actions: JingduActions, onDismiss: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.txt_doctor)) }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.DOCTOR) })
        DropdownMenuItem(text = { Text(stringResource(R.string.smart_clean4)) }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) })
        DropdownMenuItem(text = { Text(stringResource(R.string.bookmarks)) }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.BOOKMARKS) })
        DropdownMenuItem(text = { Text(stringResource(R.string.clean)) }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.CLEAN) })
        DropdownMenuItem(text = { Text(stringResource(R.string.text_encoding)) }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.ENCODING) })
        DropdownMenuItem(text = { Text(stringResource(R.string.reading_settings)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.SETTINGS) })
        DropdownMenuItem(text = { Text(stringResource(R.string.privacy_verification)) }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { onDismiss(); actions.onOpenPanel(ReaderPanel.PRIVACY) })
        DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { onDismiss(); actions.onRequestDeleteCurrent() })
    }
}

@Composable
private fun PagedReaderPage(
    text: String,
    settings: ReaderSettings,
    onVisibleCharsChanged: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    textColor: Color,
    ttsHighlight: Boolean,
    ttsChunkSourceChars: Long,
) {
    val displayText = remember(text, settings.chineseMode, settings.chineseOverrides) { ChineseDisplayConverter.convert(text, settings.chineseMode, settings.chineseOverrides) }
    val style = readerTextStyle(settings, textColor)
    var widthPx by remember { mutableIntStateOf(0) }
    val surfaceDescription = stringResource(R.string.reader_surface)
    val gestureModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { widthPx = it.width }
        .readerGestures(settings, widthPx, onPrevious, onNext, onToggleControls, surfaceDescription)

    BoxWithConstraints(gestureModifier) {
        val useTwoColumns = when (settings.wideColumns) {
            ReaderWideColumns.SINGLE -> false
            ReaderWideColumns.DOUBLE -> maxWidth >= 600.dp
            ReaderWideColumns.AUTO -> maxWidth >= 840.dp
        }
        if (useTwoColumns) {
            TwoColumnPage(text, displayText, settings, style, onVisibleCharsChanged, ttsHighlight, ttsChunkSourceChars)
        } else {
            Box(Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp), contentAlignment = Alignment.TopCenter) {
                SelectionContainer {
                    Text(
                        text = highlightedText(displayText, ttsHighlight, ttsChunkSourceChars),
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxHeight(),
                        style = style,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { layout -> reportVisibleSourceChars(text, displayText, layout, onVisibleCharsChanged) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoColumnPage(
    sourceText: String,
    displayText: String,
    settings: ReaderSettings,
    style: TextStyle,
    onVisibleCharsChanged: (Long) -> Unit,
    ttsHighlight: Boolean,
    ttsChunkSourceChars: Long,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val gap = 28.dp
        val contentWidth = (maxWidth.coerceAtMost(1200.dp) - gap) / 2
        val maxWidthPx = with(density) { contentWidth.roundToPx().coerceAtLeast(1) }
        val maxHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val constraints = remember(maxWidthPx, maxHeightPx) { Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx) }
        val firstLayout = remember(displayText, style, constraints) { textMeasurer.measure(displayText, style = style, overflow = TextOverflow.Clip, constraints = constraints) }
        val firstLine = if (firstLayout.lineCount <= 0) 0 else firstLayout.getLineForVerticalPosition((maxHeightPx - 1).toFloat()).coerceIn(0, firstLayout.lineCount - 1)
        val firstEnd = if (displayText.isEmpty() || firstLayout.lineCount <= 0) 0 else firstLayout.getLineEnd(firstLine, visibleEnd = true).coerceIn(0, displayText.length)
        val secondSource = displayText.substring(firstEnd)
        val secondLayout = remember(secondSource, style, constraints) { textMeasurer.measure(secondSource, style = style, overflow = TextOverflow.Clip, constraints = constraints) }
        val secondLine = if (secondLayout.lineCount <= 0) 0 else secondLayout.getLineForVerticalPosition((maxHeightPx - 1).toFloat()).coerceIn(0, secondLayout.lineCount - 1)
        val secondEnd = if (secondSource.isEmpty() || secondLayout.lineCount <= 0) 0 else secondLayout.getLineEnd(secondLine, visibleEnd = true).coerceIn(0, secondSource.length)
        val displayedEnd = (firstEnd + secondEnd).coerceIn(0, displayText.length)
        LaunchedEffect(sourceText, displayText, displayedEnd) {
            if (displayedEnd > 0) {
                val displayedCount = displayText.codePointCount(0, displayedEnd).toLong()
                val sourceCount = ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayedCount)
                if (sourceCount >= ReaderController.MIN_PAGE_CHARS) onVisibleCharsChanged(sourceCount)
            }
        }
        Row(Modifier.widthIn(max = 1200.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(highlightedText(displayText.substring(0, firstEnd), ttsHighlight, ttsChunkSourceChars), modifier = Modifier.fillMaxHeight(), style = style, overflow = TextOverflow.Clip)
            }
            SelectionContainer(Modifier.weight(1f)) {
                Text(AnnotatedString(secondSource.substring(0, secondEnd)), modifier = Modifier.fillMaxHeight(), style = style, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun ContinuousReaderPage(
    state: AppUiState,
    actions: JingduActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    followExternalPosition: Boolean,
    onPauseAutoScroll: () -> Unit,
    textColor: Color,
) {
    val context = LocalContext.current
    val book = state.currentBook ?: return
    val settings = state.settings
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var session by remember(book.id) { mutableStateOf<ContinuousWindowReader?>(null) }
    var sourceText by remember(book.id) { mutableStateOf("") }
    var windowStart by remember(book.id) { mutableLongStateOf(0L) }
    var documentLength by remember(book.id) { mutableLongStateOf(state.length) }
    var localPosition by remember(book.id) { mutableLongStateOf(state.position) }
    var layoutResult by remember(book.id) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var loading by remember(book.id) { mutableStateOf(false) }
    var lastReported by remember(book.id) { mutableLongStateOf(state.position) }
    var lastRebaseAt by remember(book.id) { mutableLongStateOf(0L) }
    val displayText = remember(sourceText, settings.chineseMode, settings.chineseOverrides) { ChineseDisplayConverter.convert(sourceText, settings.chineseMode, settings.chineseOverrides) }
    val style = readerTextStyle(settings, textColor)
    var widthPx by remember { mutableIntStateOf(0) }

    suspend fun loadAround(target: Long) {
        if (loading) return
        val active = session ?: return
        loading = true
        try {
            val result = withContext(Dispatchers.IO) { active.readAround(target) }
            localPosition = target.coerceIn(0L, (result.documentLength - 1).coerceAtLeast(0L))
            windowStart = result.start
            sourceText = result.text
            documentLength = result.documentLength
            layoutResult = null
        } finally {
            loading = false
        }
    }

    DisposableEffect(book.id) {
        onDispose {
            session?.close()
            session = null
        }
    }

    LaunchedEffect(book.id) {
        val opened = withContext(Dispatchers.IO) { runCatching { ContinuousWindowReader(context, book.id) } }.getOrNull()
        session?.close()
        session = opened
        if (opened != null) loadAround(state.position)
    }

    LaunchedEffect(state.position, session, followExternalPosition) {
        if (session != null && (followExternalPosition || abs(state.position - localPosition) > EXTERNAL_POSITION_REBASE_CHARS)) {
            loadAround(state.position)
        }
    }

    LaunchedEffect(windowStart, sourceText, layoutResult) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (displayText.isEmpty()) return@LaunchedEffect
        val sourceDelta = (localPosition - windowStart).coerceAtLeast(0L)
        val displayedPoints = ChineseDisplayConverter.displayedCharsForSource(sourceText, displayText, sourceDelta)
        val charIndex = utf16IndexForCodePoints(displayText, displayedPoints)
        val line = layout.getLineForOffset(charIndex.coerceIn(0, (displayText.length - 1).coerceAtLeast(0)))
        scrollState.scrollTo(layout.getLineTop(line).roundToInt().coerceIn(0, scrollState.maxValue))
    }

    LaunchedEffect(scrollState, layoutResult, windowStart, sourceText, displayText, viewportHeightPx) {
        snapshotFlow { scrollState.value }.distinctUntilChanged().collect { scrollY ->
            val layout = layoutResult ?: return@collect
            if (displayText.isEmpty() || layout.lineCount <= 0) return@collect
            val line = layout.getLineForVerticalPosition(scrollY.toFloat()).coerceIn(0, layout.lineCount - 1)
            val charIndex = layout.getLineStart(line).coerceIn(0, displayText.length)
            val displayedPoints = displayText.codePointCount(0, charIndex).toLong()
            val sourceDelta = ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayedPoints)
            val absolute = (windowStart + sourceDelta).coerceIn(0L, (documentLength - 1).coerceAtLeast(0L))
            localPosition = absolute
            if (abs(absolute - lastReported) >= CONTINUOUS_REPORT_CHARS) {
                lastReported = absolute
                actions.onSyncTtsPosition(absolute)
            }

            val now = SystemClock.elapsedRealtime()
            if (!loading && now - lastRebaseAt >= CONTINUOUS_REBASE_INTERVAL_MS && viewportHeightPx > 0) {
                val edge = (viewportHeightPx * 0.30f).roundToInt()
                val windowSourcePoints = sourceText.codePointCount(0, sourceText.length).toLong()
                val nearTop = scrollY <= edge && windowStart > 0
                val nearBottom = scrollState.maxValue > 0 && scrollState.maxValue - scrollY <= edge && windowStart + windowSourcePoints < documentLength - 1
                if (nearTop || nearBottom) {
                    lastRebaseAt = now
                    loadAround(absolute)
                }
            }
        }
    }

    LaunchedEffect(settings.autoScrollEnabled, settings.autoScrollSpeedDpPerSecond, sourceText) {
        if (!settings.autoScrollEnabled) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive && settings.autoScrollEnabled) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0
                    val deltaPx = with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() }
                    scrollState.scrollBy(deltaPx)
                }
                lastFrame = now
            }
            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 1) {
                val windowSourcePoints = sourceText.codePointCount(0, sourceText.length).toLong()
                if (windowStart + windowSourcePoints >= documentLength - 1) {
                    actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
                    break
                }
            }
        }
    }

    val touchPauseModifier = Modifier.pointerInput(settings.autoScrollEnabled) {
        if (settings.autoScrollEnabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPauseAutoScroll()
            }
        }
    }

    val surfaceDescription = stringResource(R.string.reader_surface)
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height; widthPx = it.width }
            .then(touchPauseModifier)
            .readerGestures(settings, widthPx, onPrevious, onNext, onToggleControls, surfaceDescription),
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionContainer {
            Text(
                text = displayText,
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp)
                    .widthIn(max = 760.dp),
                style = style,
                overflow = TextOverflow.Clip,
                onTextLayout = { layoutResult = it },
            )
        }
        if (loading && sourceText.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

private fun Modifier.readerGestures(
    settings: ReaderSettings,
    widthPx: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    surfaceDescription: String,
): Modifier = pointerInput(
    settings.tapPagingEnabled,
    settings.swipePagingEnabled,
    settings.reversePagingGestures,
    settings.tapZoneEdgeFraction,
    widthPx,
) {
    val swipeThreshold = 56.dp.toPx()
    val tapSlop = 14.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var last = down
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.firstOrNull { it.id == down.id }?.let { last = it }
        } while (last.pressed)

        val delta = last.position - down.position
        val duration = last.uptimeMillis - down.uptimeMillis
        if (settings.swipePagingEnabled && abs(delta.x) >= swipeThreshold && abs(delta.x) > abs(delta.y) * 1.25f) {
            var forward = delta.x < 0f
            if (settings.reversePagingGestures) forward = !forward
            if (forward) onNext() else onPrevious()
            return@awaitEachGesture
        }

        if (duration <= 350L && delta.getDistance() <= tapSlop && widthPx > 0) {
            val edge = widthPx * settings.tapZoneEdgeFraction
            when {
                down.position.x < edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onNext() else onPrevious()
                down.position.x > widthPx - edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onPrevious() else onNext()
                else -> onToggleControls()
            }
        }
    }
}.semantics { contentDescription = surfaceDescription }

@Composable
private fun ReaderBottomBar(
    fraction: Float,
    ttsPlaying: Boolean,
    autoPaging: Boolean,
    autoScrolling: Boolean,
    autoScrollAvailable: Boolean,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onTts: () -> Unit,
    onAutoScroll: () -> Unit,
) {
    var sliderValue by remember(fraction) { mutableFloatStateOf(fraction) }
    val progressDescription = stringResource(R.string.reading_progress)
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLocationBack, enabled = canLocationBack) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.reader_location_back)) }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_page)) }
                Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { onSeek(sliderValue) }, modifier = Modifier.weight(1f).semantics { contentDescription = progressDescription })
                IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_page)) }
                IconButton(onClick = onLocationForward, enabled = canLocationForward) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.reader_location_forward)) }
                IconButton(onClick = onAutoScroll, enabled = autoScrollAvailable) { Icon(if (autoScrolling) Icons.Default.Pause else Icons.Outlined.UnfoldMore, contentDescription = stringResource(if (autoScrolling) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll)) }
                IconButton(onClick = onTts) { Icon(if (ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (ttsPlaying) stringResource(R.string.pause_read_aloud) else stringResource(R.string.start_read_aloud)) }
            }
            if (autoScrolling || autoPaging || ttsPlaying) {
                Text(
                    when {
                        autoScrolling -> stringResource(R.string.reader_auto_scroll)
                        ttsPlaying -> stringResource(R.string.reading_aloud_background)
                        else -> stringResource(R.string.auto_paging_active)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun readerTextStyle(settings: ReaderSettings, color: Color): TextStyle = TextStyle(
    color = color,
    fontFamily = if (settings.typeface == ReaderTypeface.SERIF) FontFamily.Serif else FontFamily.SansSerif,
    fontWeight = when (settings.fontWeight) {
        ReaderFontWeight.NORMAL -> FontWeight.Normal
        ReaderFontWeight.MEDIUM -> FontWeight.Medium
        ReaderFontWeight.SEMIBOLD -> FontWeight.SemiBold
    },
    fontSize = settings.fontSizeSp.sp,
    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
    textAlign = if (settings.textAlignment == ReaderTextAlignment.START) TextAlign.Start else TextAlign.Justify,
    textIndent = TextIndent(firstLine = (settings.fontSizeSp * settings.firstLineIndentEm).sp),
)

private fun highlightedText(displayText: String, highlight: Boolean, sourceChars: Long): AnnotatedString {
    if (!highlight || displayText.isEmpty()) return AnnotatedString(displayText)
    val sentenceBoundary = displayText.indexOfAny(charArrayOf('。', '！', '？', '\n')).let { if (it < 0) displayText.length else it + 1 }
    val approximate = if (sourceChars > 0) sourceChars.coerceAtMost(240).toInt() else 120
    val end = minOf(displayText.length, maxOf(1, minOf(sentenceBoundary, approximate)))
    return buildAnnotatedString {
        append(displayText)
        addStyle(SpanStyle(background = Color(0x3358A67A)), 0, end)
    }
}

private fun reportVisibleSourceChars(source: String, displayed: String, layout: TextLayoutResult, callback: (Long) -> Unit) {
    if (layout.lineCount <= 0 || displayed.isEmpty() || layout.size.height <= 0) return
    val visibleLine = layout.getLineForVerticalPosition((layout.size.height - 1).toFloat()).coerceIn(0, layout.lineCount - 1)
    val end = layout.getLineEnd(visibleLine, visibleEnd = true).coerceIn(0, displayed.length)
    val displayedCount = displayed.codePointCount(0, end).toLong()
    val sourceCount = ChineseDisplayConverter.sourceCharsForDisplayed(source, displayed, displayedCount)
    if (sourceCount >= ReaderController.MIN_PAGE_CHARS) callback(sourceCount)
}

private fun utf16IndexForCodePoints(text: String, codePoints: Long): Int {
    if (text.isEmpty()) return 0
    val total = text.codePointCount(0, text.length)
    return text.offsetByCodePoints(0, codePoints.coerceIn(0, total.toLong()).toInt())
}

private fun readerBackground(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}

private fun readerTextColor(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.NIGHT -> Color(0xFFE8E5DA)
    ReaderPalette.OLED -> Color(0xFFE8E8E8)
    else -> Color(0xFF24241F)
}

private const val CONTINUOUS_REPORT_CHARS = 64L
private const val EXTERNAL_POSITION_REBASE_CHARS = 512L
private const val CONTINUOUS_REBASE_INTERVAL_MS = 450L
