@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val QUICK_PALETTES = listOf(ReaderPalette.PAPER, ReaderPalette.SEPIA, ReaderPalette.LIGHT, ReaderPalette.NIGHT, ReaderPalette.OLED)

@Composable
internal fun ReaderQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    val density = LocalDensity.current
    val title = stringResource(R.string.reader_quick_settings)
    val paged = stringResource(R.string.reader_mode_paged)
    val continuous = stringResource(R.string.reader_mode_continuous)
    val bookmark = stringResource(R.string.reader_access_bookmark)
    val advanced = stringResource(R.string.reader_advanced_settings)
    val autoLabel = stringResource(if (state.autoScrolling) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll)
    val speedLabel = stringResource(R.string.reader_auto_scroll_speed_value, s.autoScrollSpeedDpPerSecond.roundToInt())
    val paletteLabels = QUICK_PALETTES.map { quickPaletteLabel(it) }
    val colors = MaterialTheme.colorScheme
    val paints = rememberReaderCanvasTextPaint(colors.onSurface, colors.onSurfaceVariant, colors.primary)
    val row1 = with(density) { 78.dp.toPx() }
    val row2 = with(density) { 132.dp.toPx() }
    val row3 = with(density) { 188.dp.toPx() }
    val row4 = with(density) { 238.dp.toPx() }
    val row5 = with(density) { 286.dp.toPx() }
    val edge = with(density) { 18.dp.toPx() }
    val buttonH = with(density) { 44.dp.toPx() }

    fun setPalette(palette: ReaderPalette) = actions.onSettingsChanged(s.copy(palette = palette, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun setPaged() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false))
    fun setContinuous() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS))
    fun font(delta: Float) = actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp + delta).coerceIn(14f, 40f), preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun speed(delta: Float) = actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond + delta).coerceIn(12f, 320f)))
    val accessibilityActions = buildList {
        paletteLabels.forEachIndexed { index, label -> add(CustomAccessibilityAction(label) { setPalette(QUICK_PALETTES[index]); true }) }
        add(CustomAccessibilityAction(paged) { setPaged(); true })
        add(CustomAccessibilityAction(continuous) { setContinuous(); true })
        add(CustomAccessibilityAction(bookmark) { actions.onAddBookmark(); true })
        add(CustomAccessibilityAction(advanced) { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS); true })
    }

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Box(Modifier.fillMaxWidth().height(QUICK_PANEL_HEIGHT)) {
            ReaderCanvasPanel(
                height = QUICK_PANEL_HEIGHT,
                description = title,
                actions = accessibilityActions,
                recordKey = listOf(colors, s.palette, s.fontSizeSp, s.lineHeightMultiplier, s.readingMode, state.autoScrolling, s.autoScrollSpeedDpPerSecond),
                onTap = { point, width, _ ->
                    when {
                        point.y in row1 - buttonH / 2f..row1 + buttonH / 2f -> {
                            val slot = ((width - edge * 2f) / QUICK_PALETTES.size).coerceAtLeast(1f)
                            setPalette(QUICK_PALETTES[((point.x - edge) / slot).toInt().coerceIn(0, QUICK_PALETTES.lastIndex)])
                        }
                        point.y in row2 - buttonH / 2f..row2 + buttonH / 2f -> when {
                            point.x < width * 0.20f -> font(-1f)
                            point.x < width * 0.40f -> font(1f)
                        }
                        point.y in row3 - buttonH / 2f..row3 + buttonH / 2f -> when {
                            point.x < width * 0.32f -> setPaged()
                            point.x < width * 0.64f -> setContinuous()
                            point.x > width * 0.78f -> actions.onAddBookmark()
                        }
                        s.readingMode == ReaderMode.CONTINUOUS && point.y in row4 - buttonH / 2f..row4 + buttonH / 2f -> when {
                            point.x < width * 0.24f -> speed(-8f)
                            point.x > width * 0.76f -> speed(8f)
                            else -> { actions.onClosePanel(); actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = !state.autoScrolling)) }
                        }
                        point.y >= row5 - buttonH / 2f -> { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) }
                    }
                },
            ) {
                drawReaderText(title, paints.title, edge, 30.dp.toPx(), size.width - edge * 2f)
                val slot = (size.width - edge * 2f) / QUICK_PALETTES.size
                QUICK_PALETTES.forEachIndexed { index, palette ->
                    val x = edge + slot * (index + 0.5f)
                    drawCircle(quickPaletteSwatch(palette), 13.dp.toPx(), Offset(x, row1))
                    if (s.palette == palette) drawCircle(colors.primary, 18.dp.toPx(), Offset(x, row1), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                drawReaderButton(Rect(edge, row2 - 22.dp.toPx(), edge + 52.dp.toPx(), row2 + 22.dp.toPx()), "−", paints.action, colors.primary, outline = colors.outlineVariant)
                drawReaderText("${s.fontSizeSp.roundToInt()}sp", paints.normal, edge + 58.dp.toPx(), row2, 62.dp.toPx(), centered = true)
                drawReaderButton(Rect(edge + 126.dp.toPx(), row2 - 22.dp.toPx(), edge + 178.dp.toPx(), row2 + 22.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                val modeWidth = (size.width - edge * 2f) * 0.29f
                val pagedRect = Rect(edge, row3 - 22.dp.toPx(), edge + modeWidth, row3 + 22.dp.toPx())
                val continuousRect = Rect(edge + modeWidth + 8.dp.toPx(), row3 - 22.dp.toPx(), edge + modeWidth * 2f + 8.dp.toPx(), row3 + 22.dp.toPx())
                drawReaderButton(pagedRect, paged, paints.action, if (s.readingMode == ReaderMode.PAGED) colors.onPrimary else colors.primary, if (s.readingMode == ReaderMode.PAGED) colors.primary else Color.Transparent, if (s.readingMode == ReaderMode.PAGED) null else colors.outlineVariant)
                drawReaderButton(continuousRect, continuous, paints.action, if (s.readingMode == ReaderMode.CONTINUOUS) colors.onPrimary else colors.primary, if (s.readingMode == ReaderMode.CONTINUOUS) colors.primary else Color.Transparent, if (s.readingMode == ReaderMode.CONTINUOUS) null else colors.outlineVariant)
                drawReaderButton(Rect(size.width - edge - 72.dp.toPx(), row3 - 22.dp.toPx(), size.width - edge, row3 + 22.dp.toPx()), "★", paints.action, colors.primary, outline = colors.outlineVariant)
                if (s.readingMode == ReaderMode.CONTINUOUS) {
                    drawReaderButton(Rect(edge, row4 - 22.dp.toPx(), edge + 52.dp.toPx(), row4 + 22.dp.toPx()), "−", paints.action, colors.primary, outline = colors.outlineVariant)
                    drawReaderButton(Rect(size.width - edge - 52.dp.toPx(), row4 - 22.dp.toPx(), size.width - edge, row4 + 22.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                    drawReaderText("$speedLabel · $autoLabel", paints.small, edge + 60.dp.toPx(), row4, size.width - edge * 2f - 120.dp.toPx(), centered = true)
                }
                drawReaderButton(Rect(edge, row5 - 22.dp.toPx(), size.width - edge, row5 + 22.dp.toPx()), advanced, paints.action, colors.primary, outline = colors.outlineVariant)
            }

            Row(Modifier.fillMaxWidth().height(48.dp).offset(y = 54.dp).padding(horizontal = 18.dp)) {
                paletteLabels.forEachIndexed { index, label ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { setPalette(QUICK_PALETTES[index]) }
                            .semantics { contentDescription = label },
                    )
                }
            }
            ReaderCanvasSemanticTarget("−", 18.dp, 108.dp, 52.dp, 48.dp) { font(-1f) }
            ReaderCanvasSemanticTarget("+", 144.dp, 108.dp, 52.dp, 48.dp) { font(1f) }
            ReaderLinearSlider(
                value = s.lineHeightMultiplier,
                valueRange = 1.15f..2.20f,
                onValueChange = { value -> actions.onSettingsChanged(s.copy(lineHeightMultiplier = value, preset = ReaderPreset.CUSTOM, activeThemeId = "")) },
                modifier = Modifier.fillMaxWidth().offset(y = 114.dp).padding(start = 208.dp, end = 18.dp),
                contentDescription = stringResource(R.string.line_spacing),
            )
            ReaderCanvasSemanticTarget(paged, 18.dp, 164.dp, 96.dp, 48.dp, ::setPaged)
            ReaderCanvasSemanticTarget(continuous, 122.dp, 164.dp, 112.dp, 48.dp, ::setContinuous)
            Box(
                Modifier.align(Alignment.TopEnd).offset(y = 164.dp).padding(end = 18.dp).size(72.dp, 48.dp)
                    .clickable { actions.onAddBookmark() }.semantics { contentDescription = bookmark },
            )
            if (s.readingMode == ReaderMode.CONTINUOUS) {
                ReaderCanvasSemanticTarget("−", 18.dp, 214.dp, 52.dp, 48.dp) { speed(-8f) }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).offset(y = 214.dp).padding(horizontal = 82.dp)
                        .clickable { actions.onClosePanel(); actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = !state.autoScrolling)) }
                        .semantics { contentDescription = autoLabel },
                )
                Box(
                    Modifier.align(Alignment.TopEnd).offset(y = 214.dp).padding(end = 18.dp).size(52.dp, 48.dp)
                        .clickable { speed(8f) }.semantics { contentDescription = "+" },
                )
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).offset(y = 262.dp).padding(horizontal = 18.dp)
                    .clickable { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) }
                    .semantics { contentDescription = advanced },
            )
        }
    }
}

@Composable
internal fun ReaderLinearSlider(value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, onValueChangeFinished: () -> Unit = {}, contentDescription: String? = null) {
    val latestChange = rememberUpdatedState(onValueChange)
    val latestFinished = rememberUpdatedState(onValueChangeFinished)
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.height(36.dp).pointerInput(valueRange.start, valueRange.endInclusive) {
        awaitEachGesture {
            val down = awaitFirstDown()
            fun update(x: Float) { latestChange.value(valueRange.start + range * (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)) }
            update(down.position.x)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { change -> update(change.position.x); pressed = change.pressed; change.consume() } ?: run { pressed = false }
            }
            latestFinished.value()
        }
    }.semantics {
        if (contentDescription != null) this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
        setProgress { target -> latestChange.value(target.coerceIn(valueRange)); latestFinished.value(); true }
    }) {
        val y = size.height / 2f
        drawRoundRect(track, Offset(0f, y - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        val x = size.width * fraction
        if (x > 0f) drawRoundRect(primary, Offset(0f, y - 2.dp.toPx()), androidx.compose.ui.geometry.Size(x, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawCircle(primary, 7.dp.toPx(), Offset(x, y))
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
    ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.SEPIA -> Color(0xFFF3E5C8); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.NIGHT -> Color(0xFF151713); ReaderPalette.OLED -> Color.Black
}
private val QUICK_PANEL_HEIGHT = 324.dp
