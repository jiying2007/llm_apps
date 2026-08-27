@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import android.content.Context
import android.widget.SeekBar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        label = { Box(Modifier.size(24.dp).background(quickPaletteSwatch(palette), CircleShape)) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp - 1).coerceAtLeast(14f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Remove, stringResource(R.string.font_size)) }
                Text("${s.fontSizeSp.roundToInt()}sp", Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp + 1).coerceAtMost(40f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Add, stringResource(R.string.font_size)) }
                val lineHeightDescription = "${stringResource(R.string.reader_quick_settings)} ${"%.2f".format(s.lineHeightMultiplier)}"
                AndroidView(
                    factory = { QuickLineHeightSeekBar(it) },
                    update = { slider ->
                        slider.bind(s.lineHeightMultiplier, lineHeightDescription) { value ->
                            actions.onSettingsChanged(s.copy(lineHeightMultiplier = value, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = s.readingMode == ReaderMode.PAGED,
                    onCheckedChange = { if (it) actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false)) },
                ) { Icon(Icons.Default.MenuBook, stringResource(R.string.reader_mode_paged)) }
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

/** Platform SeekBar keeps the high-frequency line-height control out of Compose Slider measure/JIT. */
private class QuickLineHeightSeekBar(context: Context) : SeekBar(context) {
    private var onValueChange: (Float) -> Unit = {}

    init {
        max = 105
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onValueChange((1.15f + progress / 100f).coerceIn(1.15f, 2.20f))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    fun bind(value: Float, description: String, onValueChange: (Float) -> Unit) {
        this.onValueChange = onValueChange
        contentDescription = description
        if (!isPressed) progress = ((value.coerceIn(1.15f, 2.20f) - 1.15f) * 100f).roundToInt().coerceIn(0, max)
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
