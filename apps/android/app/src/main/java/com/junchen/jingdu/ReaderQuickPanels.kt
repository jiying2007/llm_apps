@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val QUICK_PALETTES = listOf(
    ReaderPalette.PAPER,
    ReaderPalette.SEPIA,
    ReaderPalette.LIGHT,
    ReaderPalette.NIGHT,
    ReaderPalette.OLED,
)

/** Low-friction reading controls. Advanced/rare controls live in ReaderSettingsScreen. */
@Composable
internal fun ReaderQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reader_quick_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_PALETTES.forEach { palette ->
                    val label = quickPaletteLabel(palette)
                    FilterChip(
                        selected = s.palette == palette,
                        onClick = { actions.onSettingsChanged(s.copy(palette = palette, preset = ReaderPreset.CUSTOM, activeThemeId = "")) },
                        label = { Box(Modifier.size(22.dp).background(quickPaletteSwatch(palette), CircleShape)) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp - 1).coerceAtLeast(14f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Remove, stringResource(R.string.font_size)) }
                Text("${s.fontSizeSp.roundToInt()}sp", Modifier.width(54.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp + 1).coerceAtMost(40f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Add, stringResource(R.string.font_size)) }
                ReaderLinearSlider(
                    value = s.lineHeightMultiplier,
                    valueRange = 1.15f..2.20f,
                    onValueChange = { actions.onSettingsChanged(s.copy(lineHeightMultiplier = it, preset = ReaderPreset.CUSTOM, activeThemeId = "")) },
                    contentDescription = stringResource(R.string.reader_quick_settings),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = s.readingMode == ReaderMode.PAGED,
                    onCheckedChange = { if (it) actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false)) },
                ) { Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.reader_mode_paged)) }
                IconToggleButton(
                    checked = s.readingMode == ReaderMode.CONTINUOUS,
                    onCheckedChange = { if (it) actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS)) },
                ) { Icon(Icons.Default.ViewStream, stringResource(R.string.reader_mode_continuous)) }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(actions.onAddBookmark) { Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.reader_access_bookmark)) }
            }
            if (s.readingMode == ReaderMode.CONTINUOUS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond - 8f).coerceAtLeast(12f))) }) { Icon(Icons.Default.Remove, stringResource(R.string.reader_auto_scroll_slow)) }
                    Text(stringResource(R.string.reader_auto_scroll_speed_value, s.autoScrollSpeedDpPerSecond.roundToInt()), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

/** Fixed-cost Canvas slider used on reader hot paths instead of Material3 Slider measure/layout. */
@Composable
internal fun ReaderLinearSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    contentDescription: String? = null,
) {
    val latestChange = rememberUpdatedState(onValueChange)
    val latestFinished = rememberUpdatedState(onValueChangeFinished)
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .height(36.dp)
            .pointerInput(valueRange.start, valueRange.endInclusive) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(x: Float) {
                        val f = (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        latestChange.value(valueRange.start + range * f)
                    }
                    update(down.position.x)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            update(change.position.x)
                            pressed = change.pressed
                            change.consume()
                        } ?: run { pressed = false }
                    }
                    latestFinished.value()
                }
            }
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target ->
                    latestChange.value(target.coerceIn(valueRange))
                    latestFinished.value()
                    true
                }
            },
    ) {
        val y = size.height / 2f
        drawRoundRect(track, topLeft = androidx.compose.ui.geometry.Offset(0f, y - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        val x = size.width * fraction
        if (x > 0f) drawRoundRect(primary, topLeft = androidx.compose.ui.geometry.Offset(0f, y - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(x, 4.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        drawCircle(primary, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
    }
}

@Composable
internal fun ReaderGestureCoach(settings: ReaderSettings, actions: JingduActions) {
    if (settings.gestureCoachDismissed) return
    AlertDialog(
        onDismissRequest = { actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) },
        title = { Text(stringResource(R.string.reader_gesture_coach_title)) },
        text = { Text(stringResource(R.string.reader_gesture_coach_body)) },
        confirmButton = { TextButton({ actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) }) { Text(stringResource(R.string.reader_gesture_coach_done)) } },
    )
}

@Composable
private fun quickPaletteLabel(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> stringResource(R.string.reader_oled)
}

private fun quickPaletteSwatch(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.SEPIA -> Color(0xFFF3E5C8)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}
