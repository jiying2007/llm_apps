@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ReaderAdvancedSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    var overrides by rememberSaveable(s.chineseOverrides) { mutableStateOf(s.chineseOverrides) }
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM))
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.94f),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.reader_advanced_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item {
                V2Section(stringResource(R.string.reader_preset)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderPreset.entries.filter { it != ReaderPreset.CUSTOM }) { preset ->
                            FilterChip(s.preset == preset, { actions.onSettingsChanged(s.applyPreset(preset)) }, label = { Text(v2PresetLabel(preset)) })
                        }
                    }
                }
            }
            item {
                V2Section(stringResource(R.string.page_tone)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderPalette.entries) { palette -> FilterChip(s.palette == palette, { visual(s.copy(palette = palette)) }, label = { Text(v2PaletteLabel(palette)) }) }
                    }
                }
            }
            item {
                V2Section(stringResource(R.string.font)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderTypeface.entries) { typeface -> FilterChip(s.typeface == typeface, { visual(s.copy(typeface = typeface)) }, label = { Text(v2TypefaceLabel(typeface)) }) }
                    }
                    OutlinedButton(actions.onImportFont, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FontDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reader_import_font)) }
                }
            }
            item { V2Slider(stringResource(R.string.font_size), s.fontSizeSp, 14f..40f, "${s.fontSizeSp.roundToInt()}sp") { visual(s.copy(fontSizeSp = it)) } }
            item { V2Slider(stringResource(R.string.line_spacing), s.lineHeightMultiplier, 1.15f..2.2f, "%.2f×".format(s.lineHeightMultiplier)) { visual(s.copy(lineHeightMultiplier = it)) } }
            item { V2Slider(stringResource(R.string.reader_letter_spacing), s.letterSpacingEm, -0.02f..0.12f, "%.2fem".format(s.letterSpacingEm)) { visual(s.copy(letterSpacingEm = it)) } }
            item { V2Slider(stringResource(R.string.reader_paragraph_spacing), s.paragraphSpacingEm, 0f..1.5f, "%.2fem".format(s.paragraphSpacingEm)) { visual(s.copy(paragraphSpacingEm = it)) } }
            item { V2Slider(stringResource(R.string.side_margins), s.horizontalPaddingDp, 8f..56f, "${s.horizontalPaddingDp.roundToInt()}dp") { visual(s.copy(horizontalPaddingDp = it)) } }
            item { V2Slider(stringResource(R.string.reader_vertical_margins), s.verticalPaddingDp, 4f..56f, "${s.verticalPaddingDp.roundToInt()}dp") { visual(s.copy(verticalPaddingDp = it)) } }
            item { V2Switch(stringResource(R.string.reader_compress_blank_lines), s.compressBlankLines) { visual(s.copy(compressBlankLines = it)) } }
            item { V2Switch(stringResource(R.string.reader_emphasize_headings), s.emphasizeHeadings) { visual(s.copy(emphasizeHeadings = it)) } }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.reader_tap_zone_template)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderTapZonePreset.entries.filter { it != ReaderTapZonePreset.CUSTOM }) { preset ->
                            FilterChip(s.tapZonePreset == preset, { actions.onSettingsChanged(s.copy(tapZonePreset = preset)) }, label = { Text(v2TapZoneLabel(preset)) })
                        }
                    }
                    V2Switch(stringResource(R.string.reader_tap_paging), s.tapPagingEnabled) { actions.onSettingsChanged(s.copy(tapPagingEnabled = it)) }
                    V2Switch(stringResource(R.string.reader_swipe_paging), s.swipePagingEnabled) { actions.onSettingsChanged(s.copy(swipePagingEnabled = it)) }
                    V2Switch(stringResource(R.string.reader_reverse_gestures), s.reversePagingGestures) { actions.onSettingsChanged(s.copy(reversePagingGestures = it)) }
                    V2Switch(stringResource(R.string.reader_brightness_gesture), s.brightnessGestureEnabled) { actions.onSettingsChanged(s.copy(brightnessGestureEnabled = it)) }
                    V2Switch(stringResource(R.string.reader_pinch_font), s.pinchFontEnabled) { actions.onSettingsChanged(s.copy(pinchFontEnabled = it)) }
                    V2Switch(stringResource(R.string.reader_double_tap_bookmark), s.doubleTapBookmarkEnabled) { actions.onSettingsChanged(s.copy(doubleTapBookmarkEnabled = it)) }
                    V2Switch(stringResource(R.string.reader_haptics), s.hapticEnabled) { actions.onSettingsChanged(s.copy(hapticEnabled = it)) }
                }
            }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.reader_display_screen)) {
                    V2Switch(stringResource(R.string.reader_system_brightness), s.useSystemBrightness) { actions.onSettingsChanged(s.copy(useSystemBrightness = it)) }
                    if (!s.useSystemBrightness) V2Slider(stringResource(R.string.reader_brightness), s.readerBrightness, 0.03f..1f, "${(s.readerBrightness * 100).roundToInt()}%") { actions.onSettingsChanged(s.copy(readerBrightness = it)) }
                    V2Switch(stringResource(R.string.reader_show_clock), s.showClock) { actions.onSettingsChanged(s.copy(showClock = it)) }
                    V2Switch(stringResource(R.string.reader_show_battery), s.showBattery) { actions.onSettingsChanged(s.copy(showBattery = it)) }
                    V2Switch(stringResource(R.string.reader_reading_status), s.showReadingStatus) { actions.onSettingsChanged(s.copy(showReadingStatus = it)) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderOrientation.entries) { value -> FilterChip(s.orientation == value, { actions.onSettingsChanged(s.copy(orientation = value)) }, label = { Text(value.name.lowercase()) }) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderWideColumns.entries) { value -> FilterChip(s.wideColumns == value, { actions.onSettingsChanged(s.copy(wideColumns = value)) }, label = { Text(value.name.lowercase()) }) }
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.auto_page)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderAutoPageMode.entries) { mode ->
                            FilterChip(s.autoPageMode == mode, { actions.onSettingsChanged(s.copy(autoPageMode = mode)) }, label = { Text(stringResource(if (mode == ReaderAutoPageMode.ADAPTIVE) R.string.reader_auto_page_adaptive else R.string.reader_auto_page_fixed)) })
                        }
                    }
                    if (s.autoPageMode == ReaderAutoPageMode.ADAPTIVE) V2Slider(stringResource(R.string.reader_auto_page_pace), s.autoPagePaceMultiplier, 0.5f..2f, "%.1f×".format(s.autoPagePaceMultiplier)) { actions.onSettingsChanged(s.copy(autoPagePaceMultiplier = it)) }
                    else V2Slider(stringResource(R.string.interval), s.autoPageDelayMs.toFloat(), 2_000f..120_000f, "%.1fs".format(s.autoPageDelayMs / 1000f)) { actions.onSettingsChanged(s.copy(autoPageDelayMs = it.toLong())) }
                }
            }
            item { V2Slider(stringResource(R.string.reader_scroll_speed), s.autoScrollSpeedDpPerSecond, 12f..320f, "${s.autoScrollSpeedDpPerSecond.roundToInt()} dp/s") { actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = it)) } }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.chinese_conversion)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ChineseDisplayMode.entries) { mode -> FilterChip(s.chineseMode == mode, { actions.onSettingsChanged(s.copy(chineseMode = mode)) }, label = { Text(mode.name.replace('_', ' ').lowercase()) }) }
                    }
                    OutlinedTextField(overrides, { overrides = it.take(16 * 1024) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.chinese_overrides)) }, minLines = 3, maxLines = 6)
                    Button({ actions.onSettingsChanged(s.copy(chineseOverrides = overrides)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_dictionary)) }
                }
            }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.read_aloud)) {
                    V2Slider(stringResource(R.string.speech_rate), s.ttsRate, 0.5f..2f, "%.1f×".format(s.ttsRate)) { actions.onSettingsChanged(s.copy(ttsRate = it)) }
                    V2Slider(stringResource(R.string.speech_pitch), s.ttsPitch, 0.6f..1.6f, "%.1f×".format(s.ttsPitch)) { actions.onSettingsChanged(s.copy(ttsPitch = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.RecordVoiceOver, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.offline_voice), fontWeight = FontWeight.SemiBold) }
                    if (state.proUnlocked) {
                        FilterChip(s.ttsVoiceName.isEmpty(), { actions.onSettingsChanged(s.copy(ttsVoiceName = "")) }, label = { Text(stringResource(R.string.system_default)) })
                        state.ttsVoices.take(12).forEach { voice -> FilterChip(s.ttsVoiceName == voice.name, { actions.onSettingsChanged(s.copy(ttsVoiceName = voice.name)) }, label = { Text(voice.label) }) }
                    } else OutlinedButton(actions.onUpgradePro, Modifier.fillMaxWidth()) { Text("Pro") }
                }
            }
            item { V2Section(stringResource(R.string.sleep_timer)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(0, 15, 30, 60)) { minutes -> FilterChip(state.sleepMinutes == minutes, { actions.onSleepTimer(minutes) }, label = { Text(if (minutes == 0) stringResource(R.string.off) else stringResource(R.string.minutes_value, minutes)) }) } } } }

            item { HorizontalDivider() }
            item {
                V2Section(stringResource(R.string.local_asset_backup)) {
                    Text(stringResource(R.string.local_asset_backup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.proUnlocked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(actions.onExportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_backup)) }
                        OutlinedButton(actions.onImportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore_backup)) }
                    } else OutlinedButton(actions.onUpgradePro, Modifier.fillMaxWidth()) { Text("Pro") }
                }
            }
        }
    }
}

@Composable private fun V2Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() }
}
@Composable private fun V2Switch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChecked) }
}
@Composable private fun V2Slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(valueText, style = MaterialTheme.typography.labelMedium) }; Slider(value, onChange, valueRange = range) }
}
@Composable private fun v2PresetLabel(value: ReaderPreset): String = stringResource(when (value) {
    ReaderPreset.STANDARD -> R.string.reader_preset_standard; ReaderPreset.COMFORT -> R.string.reader_preset_comfort
    ReaderPreset.LARGE -> R.string.reader_preset_large; ReaderPreset.NIGHT -> R.string.reader_preset_night; ReaderPreset.CUSTOM -> R.string.reader_preset_custom
})
@Composable private fun v2PaletteLabel(value: ReaderPalette): String = stringResource(when (value) {
    ReaderPalette.PAPER -> R.string.paper; ReaderPalette.LIGHT -> R.string.light; ReaderPalette.SEPIA -> R.string.reader_theme_sepia; ReaderPalette.NIGHT -> R.string.night; ReaderPalette.OLED -> R.string.reader_oled
})
@Composable private fun v2TypefaceLabel(value: ReaderTypeface): String = when (value) {
    ReaderTypeface.SYSTEM -> stringResource(R.string.system_font); ReaderTypeface.SERIF -> stringResource(R.string.serif)
    ReaderTypeface.MONOSPACE -> stringResource(R.string.reader_font_monospace); ReaderTypeface.CUSTOM -> stringResource(R.string.reader_font_custom)
}
@Composable private fun v2TapZoneLabel(value: ReaderTapZonePreset): String = stringResource(when (value) {
    ReaderTapZonePreset.BALANCED -> R.string.reader_tap_zone_balanced; ReaderTapZonePreset.RIGHT_HANDED -> R.string.reader_tap_zone_right
    ReaderTapZonePreset.LEFT_HANDED -> R.string.reader_tap_zone_left; ReaderTapZonePreset.CUSTOM -> R.string.reader_tap_zone_balanced
})
