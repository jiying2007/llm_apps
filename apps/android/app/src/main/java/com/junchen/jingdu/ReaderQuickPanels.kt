@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Low-friction reading controls. Advanced/rare controls live in ReaderSettingsScreen. */
@Composable
internal fun ReaderQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.reader_quick_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.reader_quick_settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(ReaderPalette.PAPER, ReaderPalette.SEPIA, ReaderPalette.LIGHT, ReaderPalette.NIGHT, ReaderPalette.OLED).forEach { palette ->
                    FilterChip(s.palette == palette, { actions.onSettingsChanged(s.copy(palette = palette, preset = ReaderPreset.CUSTOM, activeThemeId = "")) }, label = { Text(quickPaletteLabel(palette)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton({ actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp - 1).coerceAtLeast(14f), preset = ReaderPreset.CUSTOM, activeThemeId = "")) }) { Icon(Icons.Default.Remove, stringResource(R.string.font_size)) }
                Text("${s.fontSizeSp.roundToInt()}sp", Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
