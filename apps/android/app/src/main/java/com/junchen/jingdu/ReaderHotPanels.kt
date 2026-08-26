package com.junchen.jingdu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Reader-hot panels avoid Material3 ModalBottomSheet's animated transition/layout path. These
 * surfaces are intentionally instant: back/outside-tap dismiss, static drag affordance, identical
 * content semantics, and no state/threshold shortcut specific to benchmarks.
 */
@Composable
private fun ReaderFastModalSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val sheetInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismissRequest),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .clickable(interactionSource = sheetInteraction, indication = null) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 1.dp,
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(32.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f), RoundedCornerShape(2.dp)))
                    }
                    content()
                }
            }
        }
    }
}

@Composable
internal fun ReaderFastQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    ReaderFastModalSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.reader_quick_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.reader_quick_settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(ReaderPalette.PAPER, ReaderPalette.SEPIA, ReaderPalette.LIGHT, ReaderPalette.NIGHT, ReaderPalette.OLED)) { palette ->
                    FilterChip(s.palette == palette, { actions.onSettingsChanged(s.copy(palette = palette, preset = ReaderPreset.CUSTOM, activeThemeId = "")) }, label = { Text(quickPaletteLabelFast(palette)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp - 1).coerceAtLeast(14f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Remove, stringResource(R.string.font_size)) }
                Text("${s.fontSizeSp.roundToInt()}sp", Modifier.width(58.dp), textAlign = TextAlign.Center)
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp + 1).coerceAtMost(40f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Add, stringResource(R.string.font_size)) }
                Slider(s.lineHeightMultiplier, { actions.onSettingsChanged(s.copy(lineHeightMultiplier = it, preset = ReaderPreset.CUSTOM, activeThemeId = "")) }, valueRange = 1.15f..2.2f, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(s.readingMode == ReaderMode.PAGED, { actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false)) }, label = { Text(stringResource(R.string.reader_mode_paged)) })
                FilterChip(s.readingMode == ReaderMode.CONTINUOUS, { actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS)) }, label = { Text(stringResource(R.string.reader_mode_continuous)) })
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(actions.onAddBookmark) { Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.reader_access_bookmark)) }
            }
            if (s.readingMode == ReaderMode.CONTINUOUS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond - 8f).coerceAtLeast(12f))) }) { Icon(Icons.Default.Remove, stringResource(R.string.reader_auto_scroll_slow)) }
                    Text(stringResource(R.string.reader_auto_scroll_speed_value, s.autoScrollSpeedDpPerSecond.roundToInt()), Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton({ actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond + 8f).coerceAtMost(320f))) }) { Icon(Icons.Default.Add, stringResource(R.string.reader_auto_scroll_fast)) }
                }
                Button(
                    onClick = {
                        actions.onClosePanel()
                        actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = !state.autoScrolling))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (state.autoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.autoScrolling) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll))
                }
            }
            OutlinedButton({ actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.reader_advanced_settings)) }
        }
    }
}

@Composable
private fun quickPaletteLabelFast(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> stringResource(R.string.reader_oled)
}

@Composable
internal fun ReaderFastChaptersSheet(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val book = state.currentBook
    val store = remember(book?.id) { TocOverrideStore(context) }
    var base by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var report by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var loading by remember(book?.id) { mutableStateOf(!state.chaptersLoaded) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) {
            base = null
            report = null
            loading = false
            return@LaunchedEffect
        }
        if (!state.chaptersLoaded) {
            loading = true
            actions.onEnsureChapters()
            return@LaunchedEffect
        }
        val (computed, repaired) = withContext(Dispatchers.Default) {
            val evaluated = SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
            evaluated to store.apply(evaluated, store.load(book.id, state.length))
        }
        base = computed
        report = repaired
        loading = false
    }

    ReaderFastModalSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_toc), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    report?.let { Text(stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.toc_add_here)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            report?.let { value ->
                if (value.anomalyCount > 0) AssistChip(onClick = {}, label = { Text(stringResource(R.string.smart_toc_anomalies, value.duplicateTitles, value.numericGaps, value.suspiciousTitles)) })
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(value.chapters, key = { it.offset }) { chapter ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { actions.onJump(chapter.offset) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (chapter.source != "core") Text(stringResource(R.string.toc_source_user_or_special), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = {
                                if (book != null) {
                                    store.hide(book.id, chapter.offset, state.length)
                                    report = base?.let { store.apply(it, store.load(book.id, state.length)) }
                                }
                            }) { Icon(Icons.Default.Delete, stringResource(R.string.toc_hide_heading)) }
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = {
                    if (book != null) { store.reset(book.id); report = base }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.toc_reset_repairs)) }
            }
        }
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(stringResource(R.string.toc_add_here)) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.toc_custom_title)) }) },
        confirmButton = { TextButton(onClick = {
            store.add(book.id, state.position, title, state.length)
            report = base?.let { store.apply(it, store.load(book.id, state.length)) }
            title = ""
            addDialog = false
        }, enabled = title.isNotBlank()) { Text(stringResource(R.string.toc_add_action)) } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
