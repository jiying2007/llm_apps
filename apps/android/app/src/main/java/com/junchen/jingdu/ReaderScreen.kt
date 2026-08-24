@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@Composable
internal fun ReaderScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val book = state.currentBook ?: return
    var more by remember { mutableStateOf(false) }
    var servicePlaying by remember(book.id) { mutableStateOf(false) }
    var serviceActive by remember(book.id) { mutableStateOf(false) }
    var ttsOffset by remember(book.id) { mutableLongStateOf(-1L) }
    var ttsNextOffset by remember(book.id) { mutableLongStateOf(-1L) }
    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)

    fun stopBackgroundTts() {
        if (serviceActive) {
            context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP))
        }
        servicePlaying = false
        serviceActive = false
        ttsOffset = -1L
        ttsNextOffset = -1L
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
                ttsOffset = offset
                ttsNextOffset = intent.getLongExtra(TtsPlaybackService.EXTRA_NEXT_OFFSET, -1L)
                if (active && state.panel == null && offset >= 0 && offset != state.position) actions.onSyncTtsPosition(offset)
            }
        }
        val filter = IntentFilter(TtsPlaybackService.ACTION_STATE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STATE))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(state.sleepMinutes, serviceActive) {
        if (serviceActive) context.startService(
            Intent(context, TtsPlaybackService::class.java)
                .setAction(TtsPlaybackService.ACTION_SLEEP)
                .putExtra(TtsPlaybackService.EXTRA_MINUTES, state.sleepMinutes),
        )
    }

    fun startBackgroundTts() {
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
            .putExtra(TtsPlaybackService.EXTRA_RATE, state.settings.ttsRate)
            .putExtra(TtsPlaybackService.EXTRA_PITCH, state.settings.ttsPitch)
            .putExtra(TtsPlaybackService.EXTRA_VOICE, state.settings.ttsVoiceName)
        serviceActive = true
        servicePlaying = true
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun toggleBackgroundTts() {
        if (state.cleanMode) { actions.onToggleTts(); return }
        if (!serviceActive) startBackgroundTts()
        else context.startService(Intent(context, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_TOGGLE))
    }

    fun manualPrevious() { stopBackgroundTts(); actions.onNavigatePrevious() }
    fun manualNext() { stopBackgroundTts(); actions.onNavigateNext() }
    fun manualSeek(value: Float) { stopBackgroundTts(); actions.onSeekFraction(value) }

    val anyTtsPlaying = servicePlaying || state.ttsPlaying
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = actions.onBackToLibrary) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_to_library)) } },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stripTxt(book.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (state.cleanMode) stringResource(R.string.clean_preview) else stringResource(R.string.reader_status, (fraction * 100).roundToInt(), book.encoding), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { actions.onOpenPanel(ReaderPanel.SEARCH) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.full_text_search)) }
                    IconButton(onClick = { actions.onOpenPanel(ReaderPanel.CHAPTERS) }) { Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.chapters)) }
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_reading_tools)) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_doctor)) }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.DOCTOR) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.smart_clean4)) }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.bookmarks)) }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.BOOKMARKS) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clean)) }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.CLEAN) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.text_encoding)) }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.ENCODING) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reading_settings)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.SETTINGS) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.privacy_verification)) }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.PRIVACY) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { more = false; actions.onRequestDeleteCurrent() })
                        }
                    }
                },
            )
        },
        bottomBar = { ReaderBottomBar(fraction, anyTtsPlaying, state.autoPaging, ::manualPrevious, ::manualNext, ::manualSeek, ::toggleBackgroundTts) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ReaderPage(
            text = state.pageText,
            settings = state.settings,
            modifier = Modifier.padding(padding),
            onVisibleCharsChanged = actions.onVisibleCharsChanged,
            ttsHighlight = servicePlaying && ttsOffset == state.position,
            ttsChunkSourceChars = (ttsNextOffset - ttsOffset).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ReaderPage(
    text: String,
    settings: ReaderSettings,
    modifier: Modifier,
    onVisibleCharsChanged: (Long) -> Unit,
    ttsHighlight: Boolean,
    ttsChunkSourceChars: Long,
) {
    val displayText = remember(text, settings.chineseMode, settings.chineseOverrides) {
        ChineseDisplayConverter.convert(text, settings.chineseMode, settings.chineseOverrides)
    }
    val annotated = remember(displayText, ttsHighlight, ttsChunkSourceChars) {
        if (!ttsHighlight || displayText.isEmpty()) buildAnnotatedString { append(displayText) }
        else {
            val sentenceBoundary = displayText.indexOfAny(charArrayOf('。', '！', '？', '\n')).let { if (it < 0) displayText.length else it + 1 }
            val approximate = if (ttsChunkSourceChars > 0) ttsChunkSourceChars.coerceAtMost(240).toInt() else 120
            val end = minOf(displayText.length, maxOf(1, minOf(sentenceBoundary, approximate)))
            buildAnnotatedString {
                append(displayText)
                addStyle(SpanStyle(background = Color(0x3358A67A)), 0, end)
            }
        }
    }
    val pageBackground = when (settings.palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.NIGHT -> Color(0xFF151713) }
    val pageText = if (settings.palette == ReaderPalette.NIGHT) Color(0xFFE8E5DA) else Color(0xFF24241F)
    val family = if (settings.typeface == ReaderTypeface.SERIF) FontFamily.Serif else FontFamily.SansSerif
    Surface(modifier = modifier.fillMaxSize(), color = pageBackground) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxTextWidth = if (maxWidth >= 840.dp) 760.dp else maxWidth
            Box(Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 18.dp), contentAlignment = Alignment.TopCenter) {
                SelectionContainer {
                    Text(text = annotated, modifier = Modifier.widthIn(max = maxTextWidth).fillMaxHeight(), style = TextStyle(color = pageText, fontFamily = family, fontSize = settings.fontSizeSp.sp, lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp, textAlign = TextAlign.Justify), overflow = TextOverflow.Clip,
                        onTextLayout = { layout ->
                            if (layout.lineCount > 0 && displayText.isNotEmpty() && layout.size.height > 0) {
                                val visibleLine = layout.getLineForVerticalPosition((layout.size.height - 1).toFloat())
                                val end = layout.getLineEnd(visibleLine, visibleEnd = true).coerceIn(0, displayText.length)
                                val displayedCount = displayText.codePointCount(0, end).toLong()
                                val sourceCount = ChineseDisplayConverter.sourceCharsForDisplayed(text, displayText, displayedCount)
                                if (sourceCount >= ReaderController.MIN_PAGE_CHARS) onVisibleCharsChanged(sourceCount)
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(fraction: Float, ttsPlaying: Boolean, autoPaging: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit, onTts: () -> Unit) {
    var sliderValue by remember(fraction) { mutableFloatStateOf(fraction) }
    val progressDescription = stringResource(R.string.reading_progress)
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_page)) }
                Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { onSeek(sliderValue) }, modifier = Modifier.weight(1f).semantics { contentDescription = progressDescription })
                IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_page)) }
                IconButton(onClick = onTts) { Icon(if (ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (ttsPlaying) stringResource(R.string.pause_read_aloud) else stringResource(R.string.start_read_aloud)) }
            }
            if (autoPaging || ttsPlaying) Text(if (ttsPlaying) stringResource(R.string.reading_aloud_background) else stringResource(R.string.auto_paging_active), modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
