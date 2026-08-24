@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ReaderQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.reader_quick_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.reader_quick_settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(ReaderPalette.PAPER, ReaderPalette.SEPIA, ReaderPalette.LIGHT, ReaderPalette.NIGHT, ReaderPalette.OLED)) { palette ->
                    FilterChip(
                        selected = settings.palette == palette,
                        onClick = { actions.onSettingsChanged(settings.copy(palette = palette, preset = ReaderPreset.CUSTOM)) },
                        label = { Text(quickPaletteLabel(palette)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { actions.onSettingsChanged(settings.copy(fontSizeSp = (settings.fontSizeSp - 1).coerceAtLeast(14f), preset = ReaderPreset.CUSTOM)) }) {
                    Icon(Icons.Default.Remove, stringResource(R.string.font_size))
                }
                Text("${settings.fontSizeSp.roundToInt()}sp", modifier = Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { actions.onSettingsChanged(settings.copy(fontSizeSp = (settings.fontSizeSp + 1).coerceAtMost(40f), preset = ReaderPreset.CUSTOM)) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.font_size))
                }
                Slider(
                    value = settings.lineHeightMultiplier,
                    onValueChange = { actions.onSettingsChanged(settings.copy(lineHeightMultiplier = it, preset = ReaderPreset.CUSTOM)) },
                    valueRange = 1.15f..2.2f,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.readingMode == ReaderMode.PAGED,
                    onClick = { actions.onSettingsChanged(settings.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false)) },
                    label = { Text(stringResource(R.string.reader_mode_paged)) },
                )
                FilterChip(
                    selected = settings.readingMode == ReaderMode.CONTINUOUS,
                    onClick = { actions.onSettingsChanged(settings.copy(readingMode = ReaderMode.CONTINUOUS)) },
                    label = { Text(stringResource(R.string.reader_mode_continuous)) },
                )
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { actions.onAddBookmark() }) {
                    Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.reader_access_bookmark))
                }
            }

            if (settings.readingMode == ReaderMode.CONTINUOUS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond - 8f).coerceAtLeast(12f)))
                    }) { Icon(Icons.Default.Remove, stringResource(R.string.reader_auto_scroll_slow)) }
                    Text(stringResource(R.string.reader_auto_scroll_speed_value, settings.autoScrollSpeedDpPerSecond.roundToInt()), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = {
                        actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond + 8f).coerceAtMost(320f)))
                    }) { Icon(Icons.Default.Add, stringResource(R.string.reader_auto_scroll_fast)) }
                }
            }

            Button(
                onClick = { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reader_advanced_settings)) }
        }
    }
}

@Composable
internal fun ReaderAnnotationsSheet(state: AppUiState, actions: JingduActions) {
    val annotations = state.annotations.filter { it.kind != ReaderAnnotationKind.BOOKMARK }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.reader_annotations), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (annotations.isEmpty()) {
                Text(stringResource(R.string.reader_annotation_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else LazyColumn(Modifier.heightIn(max = 620.dp)) {
                items(annotations, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.excerpt.ifBlank { item.note }.ifBlank { stringResource(if (item.kind == ReaderAnnotationKind.NOTE) R.string.reader_note else R.string.reader_highlight) }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            if (item.note.isNotBlank()) Text(item.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(if (item.kind == ReaderAnnotationKind.NOTE) Icons.Default.EditNote else Icons.Default.FormatColorFill, null) },
                        trailingContent = {
                            IconButton(onClick = { actions.onDeleteAnnotation(item.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.reader_delete_annotation)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { actions.onJump(item.sourceStart) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reader_chapter_progress, "@${item.sourceStart}", if (state.length <= 0) 0 else (item.sourceStart * 100 / state.length).toInt()))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private data class ReadingMapEntry(val offset: Long, val title: String, val kind: Int)

@Composable
internal fun ReaderReadingMapSheet(state: AppUiState, actions: JingduActions) {
    LaunchedEffect(state.currentBook?.id, state.chaptersLoaded) { if (!state.chaptersLoaded) actions.onEnsureChapters() }
    val entries = remember(state.chapters, state.annotations, state.position, state.length) {
        buildList {
            state.chapters.forEach { add(ReadingMapEntry(it.offset, it.title, 0)) }
            state.annotations.forEach {
                val title = when (it.kind) {
                    ReaderAnnotationKind.BOOKMARK -> "🔖"
                    ReaderAnnotationKind.HIGHLIGHT -> "▰ ${it.excerpt.take(36)}"
                    ReaderAnnotationKind.NOTE -> "✎ ${it.note.ifBlank { it.excerpt }.take(36)}"
                }
                add(ReadingMapEntry(it.sourceStart, title, 1))
            }
            add(ReadingMapEntry(state.position, "●", 2))
        }.distinctBy { it.offset to it.title }.sortedBy { it.offset }
    }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Map, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.reader_reading_map), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.reader_reading_map_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) Text(stringResource(R.string.reader_no_chapters), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 640.dp)) {
                items(entries, key = { "${it.kind}:${it.offset}:${it.title}" }) { entry ->
                    val percent = if (state.length <= 0) 0 else (entry.offset.coerceIn(0, state.length) * 100 / state.length).toInt()
                    TextButton(onClick = { actions.onJump(entry.offset) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (entry.kind == 2) stringResource(R.string.reader_current_position) else entry.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("$percent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun ReaderGestureCoach(settings: ReaderSettings, actions: JingduActions) {
    if (settings.gestureCoachDismissed) return
    AlertDialog(
        onDismissRequest = { actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) },
        title = { Text(stringResource(R.string.reader_gesture_coach_title)) },
        text = { Text(stringResource(R.string.reader_gesture_coach_body)) },
        confirmButton = {
            TextButton(onClick = { actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) }) {
                Text(stringResource(R.string.reader_gesture_coach_done))
            }
        },
    )
}

@Composable
private fun quickPaletteLabel(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> "OLED"
}
