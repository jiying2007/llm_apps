@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ReaderImmersiveTopBar(
    bookName: String,
    chapter: String?,
    actions: JingduActions,
    onMore: () -> Unit,
    onInteraction: () -> Unit,
) {
    val cleanBookName = bookName.removeSuffix(".txt").removeSuffix(".TXT")
    val title = chapter ?: cleanBookName
    val settingsDescription = stringResource(R.string.reading_settings)
    Surface(
        Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
            IconButton(actions.onBackToLibrary, Modifier.align(Alignment.CenterStart).size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_library))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 144.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (chapter != null && chapter != cleanBookName) {
                    Text(
                        cleanBookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onInteraction(); actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = settingsDescription },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Aa", style = MaterialTheme.typography.titleMedium) }
                IconButton({ onInteraction(); actions.onOpenPanel(ReaderPanel.CHAPTERS) }, Modifier.size(48.dp)) {
                    Icon(androidx.compose.material.icons.automirrored.filled.MenuBook, stringResource(R.string.chapters))
                }
                IconButton({ onInteraction(); onMore() }, Modifier.size(48.dp)) {
                    Icon(androidx.compose.material.icons.filled.MoreVert, stringResource(R.string.more_reading_tools))
                }
            }
        }
    }
}

@Composable
internal fun ReaderImmersiveBottomDock(
    chapters: List<ChapterModel>,
    length: Long,
    autoPaging: Boolean,
    ttsPlaying: Boolean,
    fraction: Float,
    skimPreview: ReaderSkimPreview?,
    skimDragging: Boolean,
    showSkimReturn: Boolean,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
    onBookmarks: () -> Unit,
    onTts: () -> Unit,
    onAutoPage: () -> Unit,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
    onReturnSkim: () -> Unit,
    onInteraction: () -> Unit,
) {
    val progressDescription = stringResource(R.string.reading_progress)
    val percent = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
    var moreOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (canLocationBack || canLocationForward) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (canLocationBack) AssistChip(
                            onClick = { onInteraction(); onLocationBack() },
                            label = { Text(stringResource(R.string.reader_location_back)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(17.dp)) },
                        )
                        if (canLocationBack && canLocationForward) Spacer(Modifier.width(8.dp))
                        if (canLocationForward) AssistChip(
                            onClick = { onInteraction(); onLocationForward() },
                            label = { Text(stringResource(R.string.reader_location_forward)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Redo, null, Modifier.size(17.dp)) },
                        )
                    }
                }

                if (skimDragging) {
                    ReaderSkimBubble(skimPreview, percent)
                } else if (showSkimReturn && skimPreview != null) {
                    ReaderSkimReturnRow(skimPreview, onReturn = { onInteraction(); onReturnSkim() })
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton({ onInteraction(); onBookmarks() }, Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmarks), Modifier.size(21.dp))
                    }
                    if (ttsPlaying) {
                        FilledTonalIconButton({ onInteraction(); onTts() }, Modifier.size(48.dp)) {
                            Icon(Icons.Default.Pause, stringResource(R.string.pause_read_aloud), Modifier.size(21.dp))
                        }
                    } else {
                        IconButton({ onInteraction(); onTts() }, Modifier.size(48.dp)) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.start_read_aloud), Modifier.size(21.dp))
                        }
                    }
                    if (autoPaging) {
                        FilledTonalIconButton({ onInteraction(); onAutoPage() }, Modifier.size(48.dp)) {
                            Icon(Icons.Default.Pause, stringResource(R.string.stop_auto_page), Modifier.size(21.dp))
                        }
                    } else {
                        Box {
                            IconButton({ onInteraction(); moreOpen = true }, Modifier.size(48.dp)) {
                                Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_reading_tools), Modifier.size(21.dp))
                            }
                            DropdownMenu(moreOpen, onDismissRequest = { moreOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.start_auto_page)) },
                                    onClick = { moreOpen = false; onInteraction(); onAutoPage() },
                                    leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                                )
                            }
                        }
                    }
                }

                ReaderImmersiveProgressRail(
                    chapters = chapters,
                    length = length,
                    fraction = fraction,
                    contentDescription = "$progressDescription $percent%",
                    onFractionChange = { value -> onInteraction(); onFractionChange(value) },
                    onFractionCommit = { onInteraction(); onFractionCommit() },
                )
            }
        }
    }
}

@Composable
private fun ReaderSkimBubble(preview: ReaderSkimPreview?, fallbackPercent: Int) {
    Surface(
        Modifier.align(Alignment.CenterHorizontally),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(preview?.chapter ?: "${preview?.bookProgressPercent ?: fallbackPercent}%", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            preview?.chapterRemainingMinutes?.let { Text("· ${it} min", style = MaterialTheme.typography.labelSmall) }
            preview?.let { Text("· ${it.bookProgressPercent}%", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ReaderSkimReturnRow(preview: ReaderSkimPreview, onReturn: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            preview.chapter?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(preview.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onReturn) { Text(stringResource(R.string.reader_skim_return)) }
    }
}

@Composable
internal fun ReaderImmersiveProgressRail(
    chapters: List<ChapterModel>,
    length: Long,
    fraction: Float,
    contentDescription: String,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val surface = MaterialTheme.colorScheme.surface
    val latestChange = rememberUpdatedState(onFractionChange)
    val latestCommit = rememberUpdatedState(onFractionCommit)
    val safeFraction = fraction.coerceIn(0f, 1f)
    val activeChapter = remember(chapters, length, safeFraction) {
        if (length <= 0) -1 else {
            val offset = (length.toDouble() * safeFraction).toLong()
            chapters.indexOfLast { it.offset <= offset }
        }
    }

    val scrubber = Modifier.pointerInput(length) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun publish(x: Float) = latestChange.value((x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
            publish(down.position.x)
            var active = down
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                active = change
                publish(change.position.x)
                change.consume()
            } while (active.pressed)
            latestCommit.value()
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(scrubber)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(safeFraction, 0f..1f)
                setProgress { target ->
                    latestChange.value(target.coerceIn(0f, 1f))
                    latestCommit.value()
                    true
                }
            },
    ) {
        val y = size.height / 2f
        val progressX = safeFraction * size.width
        val trackStroke = 3.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = trackStroke, cap = StrokeCap.Round)
        drawLine(primary, Offset(0f, y), Offset(progressX, y), strokeWidth = trackStroke, cap = StrokeCap.Round)

        if (length > 0 && chapters.isNotEmpty()) {
            val minSpacing = 13.dp.toPx().coerceAtLeast(1f)
            val maxTicks = (size.width / minSpacing).toInt().coerceAtLeast(1)
            val stride = ((chapters.size + maxTicks - 1) / maxTicks).coerceAtLeast(1)
            chapters.forEachIndexed { index, chapter ->
                if (index % stride != 0 && index != activeChapter) return@forEachIndexed
                val x = (chapter.offset.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f) * size.width
                val current = index == activeChapter
                drawLine(
                    primary.copy(alpha = if (current) 0.92f else 0.30f),
                    Offset(x, y - (if (current) 5.dp else 3.dp).toPx()),
                    Offset(x, y + (if (current) 5.dp else 3.dp).toPx()),
                    strokeWidth = (if (current) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        drawCircle(surface, radius = 7.dp.toPx(), center = Offset(progressX, y))
        drawCircle(primary, radius = 4.25.dp.toPx(), center = Offset(progressX, y))
    }
}

@Composable
internal fun ReaderCompactQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun setMode(mode: ReaderMode) = actions.onSettingsChanged(
        s.copy(readingMode = mode, autoScrollEnabled = if (mode == ReaderMode.PAGED) false else s.autoScrollEnabled),
    )

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_quick_settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(actions.onClosePanel) { Text(stringResource(R.string.reader_settings_done)) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReaderPalette.entries.forEach { palette ->
                    val selected = s.palette == palette
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(immersivePaletteSwatch(palette), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .clickable { visual(s.copy(palette = palette)) }
                            .semantics { contentDescription = palette.name },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.font_size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                FilledTonalButton({ visual(s.copy(fontSizeSp = (s.fontSizeSp - 1f).coerceAtLeast(14f))) }) { Text("−") }
                Text("${s.fontSizeSp.roundToInt()}sp", Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton({ visual(s.copy(fontSizeSp = (s.fontSizeSp + 1f).coerceAtMost(40f))) }) { Text("+") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reader_brightness), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (s.useSystemBrightness) stringResource(R.string.system_default) else "${(s.readerBrightness * 100).roundToInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ReaderLinearSlider(
                    value = s.readerBrightness,
                    valueRange = 0.03f..1f,
                    onValueChange = { actions.onSettingsChanged(s.copy(useSystemBrightness = false, readerBrightness = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = stringResource(R.string.reader_brightness),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.readingMode == ReaderMode.PAGED,
                    onClick = { setMode(ReaderMode.PAGED) },
                    label = { Text(stringResource(R.string.reader_mode_paged)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = s.readingMode == ReaderMode.CONTINUOUS,
                    onClick = { setMode(ReaderMode.CONTINUOUS) },
                    label = { Text(stringResource(R.string.reader_mode_continuous)) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(
                onClick = { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reader_all_settings)) }
        }
    }
}

internal fun readerAdaptiveTextWidth(fontSizeSp: Float): Dp = (fontSizeSp * 32f).coerceIn(520f, 760f).dp
internal fun readerAdaptiveTwoColumnWidth(fontSizeSp: Float): Dp = (fontSizeSp * 60f + 28f).coerceIn(920f, 1200f).dp

private fun immersivePaletteSwatch(palette: ReaderPalette) = when (palette) {
    ReaderPalette.PAPER -> androidx.compose.ui.graphics.Color(0xFFF7F0DE)
    ReaderPalette.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF3E5C8)
    ReaderPalette.LIGHT -> androidx.compose.ui.graphics.Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> androidx.compose.ui.graphics.Color(0xFF242821)
    ReaderPalette.OLED -> androidx.compose.ui.graphics.Color.Black
}
