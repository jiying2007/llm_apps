@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun ReaderScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    val book = state.currentBook ?: return
    var more by remember { mutableStateOf(false) }
    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.bookmarks)) }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.BOOKMARKS) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clean)) }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.CLEAN) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.text_encoding)) }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.ENCODING) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reading_settings)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { more = false; actions.onOpenPanel(ReaderPanel.SETTINGS) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { more = false; actions.onRequestDeleteCurrent() })
                        }
                    }
                },
            )
        },
        bottomBar = { ReaderBottomBar(fraction, state.ttsPlaying, state.autoPaging, actions.onNavigatePrevious, actions.onNavigateNext, actions.onSeekFraction, actions.onToggleTts) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding -> ReaderPage(state.pageText, state.settings, Modifier.padding(padding), actions.onVisibleCharsChanged) }
}

@Composable
private fun ReaderPage(text: String, settings: ReaderSettings, modifier: Modifier, onVisibleCharsChanged: (Long) -> Unit) {
    val pageBackground = when (settings.palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.NIGHT -> Color(0xFF151713) }
    val pageText = if (settings.palette == ReaderPalette.NIGHT) Color(0xFFE8E5DA) else Color(0xFF24241F)
    val family = if (settings.typeface == ReaderTypeface.SERIF) FontFamily.Serif else FontFamily.SansSerif
    Surface(modifier = modifier.fillMaxSize(), color = pageBackground) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxTextWidth = if (maxWidth >= 840.dp) 760.dp else maxWidth
            Box(Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 18.dp), contentAlignment = Alignment.TopCenter) {
                SelectionContainer {
                    Text(text = text, modifier = Modifier.widthIn(max = maxTextWidth).fillMaxHeight(), style = TextStyle(color = pageText, fontFamily = family, fontSize = settings.fontSizeSp.sp, lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp, textAlign = TextAlign.Justify), overflow = TextOverflow.Clip,
                        onTextLayout = { layout ->
                            if (layout.lineCount > 0 && text.isNotEmpty() && layout.size.height > 0) {
                                val visibleLine = layout.getLineForVerticalPosition((layout.size.height - 1).toFloat())
                                val end = layout.getLineEnd(visibleLine, visibleEnd = true).coerceIn(0, text.length)
                                val count = text.codePointCount(0, end).toLong()
                                if (count >= ReaderController.MIN_PAGE_CHARS) onVisibleCharsChanged(count)
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
            if (autoPaging || ttsPlaying) Text(if (ttsPlaying) stringResource(R.string.reading_aloud) else stringResource(R.string.auto_paging_active), modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
