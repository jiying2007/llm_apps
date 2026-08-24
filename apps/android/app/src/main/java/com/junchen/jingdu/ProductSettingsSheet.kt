@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ProductSettingsSheet(state: AppUiState, actions: JingduActions) {
    val settings = state.settings
    var overridesDraft by rememberSaveable(settings.chineseOverrides) { mutableStateOf(settings.chineseOverrides) }
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM))

    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reading_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.reader_immersive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                SettingSection(stringResource(R.string.reader_preset)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderPreset.entries.filter { it != ReaderPreset.CUSTOM }) { preset ->
                            FilterChip(
                                selected = settings.preset == preset,
                                onClick = { actions.onSettingsChanged(settings.applyPreset(preset)) },
                                label = { Text(readerPresetLabel(preset)) },
                            )
                        }
                        if (settings.preset == ReaderPreset.CUSTOM) item {
                            FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.reader_preset_custom)) })
                        }
                    }
                }
            }

            item {
                SettingSection(stringResource(R.string.reader_mode)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.readingMode == mode,
                                onClick = {
                                    actions.onSettingsChanged(settings.copy(
                                        readingMode = mode,
                                        autoScrollEnabled = settings.autoScrollEnabled && mode == ReaderMode.CONTINUOUS,
                                    ))
                                },
                                label = { Text(stringResource(if (mode == ReaderMode.PAGED) R.string.reader_mode_paged else R.string.reader_mode_continuous)) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderPageAnimation.entries.forEach { animation ->
                            FilterChip(
                                selected = settings.pageAnimation == animation,
                                onClick = { actions.onSettingsChanged(settings.copy(pageAnimation = animation)) },
                                label = { Text(stringResource(if (animation == ReaderPageAnimation.NONE) R.string.reader_animation_none else R.string.reader_animation_slide)) },
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.reader_interaction)) {
                    SwitchSetting(stringResource(R.string.reader_tap_paging), settings.tapPagingEnabled) { actions.onSettingsChanged(settings.copy(tapPagingEnabled = it)) }
                    SwitchSetting(stringResource(R.string.reader_swipe_paging), settings.swipePagingEnabled) { actions.onSettingsChanged(settings.copy(swipePagingEnabled = it)) }
                    SwitchSetting(stringResource(R.string.reader_reverse_gestures), settings.reversePagingGestures) { actions.onSettingsChanged(settings.copy(reversePagingGestures = it)) }
                    ReaderSlider(
                        stringResource(R.string.reader_tap_zone),
                        settings.tapZoneEdgeFraction,
                        0.20f..0.35f,
                        stringResource(R.string.reader_tap_zone_value, (settings.tapZoneEdgeFraction * 100).roundToInt()),
                    ) { actions.onSettingsChanged(settings.copy(tapZoneEdgeFraction = it)) }
                    ReaderSlider(
                        stringResource(R.string.reader_controls_auto_hide),
                        settings.controlsAutoHideMs.toFloat(),
                        2000f..10000f,
                        stringResource(R.string.seconds_value, settings.controlsAutoHideMs / 1000f),
                    ) { actions.onSettingsChanged(settings.copy(controlsAutoHideMs = it.toLong())) }
                }
            }

            item {
                SettingSection(stringResource(R.string.reader_volume_keys)) {
                    ReaderVolumeKeyMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.volumeKeyMode == mode,
                            onClick = { actions.onSettingsChanged(settings.copy(volumeKeyMode = mode)) },
                            label = { Text(readerVolumeLabel(mode)) },
                        )
                    }
                    SwitchSetting(stringResource(R.string.reader_reverse_volume), settings.reverseVolumeKeys) { actions.onSettingsChanged(settings.copy(reverseVolumeKeys = it)) }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.reader_display_screen)) {
                    SwitchSetting(stringResource(R.string.reader_system_brightness), settings.useSystemBrightness) { actions.onSettingsChanged(settings.copy(useSystemBrightness = it)) }
                    if (!settings.useSystemBrightness) ReaderSlider(
                        stringResource(R.string.reader_brightness),
                        settings.readerBrightness,
                        0.05f..1f,
                        "${(settings.readerBrightness * 100).roundToInt()}%",
                    ) { actions.onSettingsChanged(settings.copy(readerBrightness = it)) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderOrientation.entries) { orientation ->
                            FilterChip(settings.orientation == orientation, { actions.onSettingsChanged(settings.copy(orientation = orientation)) }, label = { Text(readerOrientationLabel(orientation)) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderWideColumns.entries) { columns ->
                            FilterChip(settings.wideColumns == columns, { actions.onSettingsChanged(settings.copy(wideColumns = columns)) }, label = { Text(readerColumnsLabel(columns)) })
                        }
                    }
                    SwitchSetting(stringResource(R.string.reader_reading_status), settings.showReadingStatus) { actions.onSettingsChanged(settings.copy(showReadingStatus = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 3, 5).forEach { lines ->
                            FilterChip(settings.focusRulerLines == lines, { actions.onSettingsChanged(settings.copy(focusRulerLines = lines)) }, label = { Text(stringResource(when (lines) { 3 -> R.string.reader_focus_three; 5 -> R.string.reader_focus_five; else -> R.string.reader_focus_off })) })
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.page_tone)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderPalette.entries) { palette ->
                            FilterChip(settings.palette == palette, { visual(settings.copy(palette = palette)) }, label = { Text(readerPaletteLabel(palette)) })
                        }
                    }
                }
            }
            item {
                SettingSection(stringResource(R.string.font)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTypeface.entries.forEach { typeface ->
                            FilterChip(settings.typeface == typeface, { visual(settings.copy(typeface = typeface)) }, label = { Text(stringResource(if (typeface == ReaderTypeface.SYSTEM) R.string.system_font else R.string.serif)) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReaderFontWeight.entries) { weight ->
                            FilterChip(settings.fontWeight == weight, { visual(settings.copy(fontWeight = weight)) }, label = { Text(readerWeightLabel(weight)) })
                        }
                    }
                }
            }
            item { ReaderSlider(stringResource(R.string.font_size), settings.fontSizeSp, 16f..34f, "${settings.fontSizeSp.roundToInt()}sp") { visual(settings.copy(fontSizeSp = it)) } }
            item { ReaderSlider(stringResource(R.string.line_spacing), settings.lineHeightMultiplier, 1.2f..2.0f, String.format("%.2f×", settings.lineHeightMultiplier)) { visual(settings.copy(lineHeightMultiplier = it)) } }
            item { ReaderSlider(stringResource(R.string.side_margins), settings.horizontalPaddingDp, 12f..48f, "${settings.horizontalPaddingDp.roundToInt()}dp") { visual(settings.copy(horizontalPaddingDp = it)) } }
            item { ReaderSlider(stringResource(R.string.reader_vertical_margins), settings.verticalPaddingDp, 8f..48f, "${settings.verticalPaddingDp.roundToInt()}dp") { visual(settings.copy(verticalPaddingDp = it)) } }
            item { ReaderSlider(stringResource(R.string.reader_first_line_indent), settings.firstLineIndentEm, 0f..2f, stringResource(R.string.reader_indent_value, settings.firstLineIndentEm)) { visual(settings.copy(firstLineIndentEm = it)) } }
            item {
                SettingSection(stringResource(R.string.reader_text_alignment)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTextAlignment.entries.forEach { alignment ->
                            FilterChip(settings.textAlignment == alignment, { visual(settings.copy(textAlignment = alignment)) }, label = { Text(stringResource(if (alignment == ReaderTextAlignment.START) R.string.reader_align_start else R.string.reader_align_justify)) })
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.reader_auto_scroll)) {
                    ReaderSlider(
                        stringResource(R.string.reader_scroll_speed),
                        settings.autoScrollSpeedDpPerSecond,
                        20f..240f,
                        "${settings.autoScrollSpeedDpPerSecond.roundToInt()} dp/s",
                    ) { actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = it)) }
                    OutlinedButton(
                        onClick = {
                            if (settings.autoScrollEnabled) {
                                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
                            } else {
                                if (state.autoPaging) actions.onToggleAutoPaging()
                                actions.onSettingsChanged(settings.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = true))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (settings.autoScrollEnabled) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll)) }
                }
            }
            item {
                SettingSection(stringResource(R.string.auto_page)) {
                    ReaderSlider(stringResource(R.string.interval), settings.autoPageDelayMs.toFloat(), 2500f..15000f, stringResource(R.string.seconds_value, settings.autoPageDelayMs / 1000f)) { actions.onSettingsChanged(settings.copy(autoPageDelayMs = it.toLong())) }
                    OutlinedButton(
                        onClick = {
                            if (!state.autoPaging && settings.autoScrollEnabled) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
                            actions.onToggleAutoPaging()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (state.autoPaging) R.string.stop_auto_page else R.string.start_auto_page)) }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.chinese_conversion)) {
                    Text(stringResource(R.string.chinese_conversion_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(ChineseDisplayMode.entries) { mode ->
                            FilterChip(settings.chineseMode == mode, { actions.onSettingsChanged(settings.copy(chineseMode = mode)) }, label = { Text(chineseModeLabel(mode)) })
                        }
                    }
                    OutlinedTextField(
                        value = overridesDraft,
                        onValueChange = { overridesDraft = it.take(16 * 1024) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.chinese_overrides)) },
                        supportingText = {
                            Column {
                                Text(stringResource(R.string.chinese_overrides_body))
                                Text(stringResource(R.string.chinese_overrides_count, ChineseDisplayConverter.overrideCount(overridesDraft)))
                            }
                        },
                        placeholder = { Text(stringResource(R.string.chinese_overrides_hint)) },
                        minLines = 3,
                        maxLines = 6,
                    )
                    Button(onClick = { actions.onSettingsChanged(settings.copy(chineseOverrides = overridesDraft)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_dictionary)) }
                }
            }

            item { HorizontalDivider() }
            item {
                SettingSection(stringResource(R.string.read_aloud)) {
                    ReaderSlider(stringResource(R.string.speech_rate), settings.ttsRate, 0.6f..1.8f, String.format("%.1f×", settings.ttsRate)) { actions.onSettingsChanged(settings.copy(ttsRate = it)) }
                    ReaderSlider(stringResource(R.string.speech_pitch), settings.ttsPitch, 0.7f..1.4f, String.format("%.1f×", settings.ttsPitch)) { actions.onSettingsChanged(settings.copy(ttsPitch = it)) }
                    OutlinedButton(
                        onClick = {
                            if (settings.autoScrollEnabled) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false))
                            actions.onToggleTts()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(if (state.ttsPlaying) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.ttsPlaying) R.string.pause_read_aloud else R.string.read_from_here))
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.offline_voice), fontWeight = FontWeight.SemiBold)
                            AssistChip(onClick = {}, label = { Text("Pro") })
                        }
                        Text(stringResource(R.string.offline_voice_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val suffix = state.proPrice?.let { stringResource(R.string.price_suffix, it) } ?: ""
                        if (!state.proUnlocked) OutlinedButton(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.unlock_pro_voice, suffix))
                        } else if (state.ttsVoices.isEmpty()) {
                            Text(stringResource(R.string.no_offline_voice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(settings.ttsVoiceName.isEmpty(), { actions.onSettingsChanged(settings.copy(ttsVoiceName = "")) }, label = { Text(stringResource(R.string.system_default)) })
                            state.ttsVoices.take(12).forEach { voice ->
                                FilterChip(settings.ttsVoiceName == voice.name, { actions.onSettingsChanged(settings.copy(ttsVoiceName = voice.name)) }, label = { Text(voice.label) })
                            }
                            if (state.ttsVoices.size > 12) Text(stringResource(R.string.more_offline_voices, state.ttsVoices.size - 12), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SettingSection(stringResource(R.string.sleep_timer)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 15, 30, 60).forEach { minutes -> FilterChip(state.sleepMinutes == minutes, { actions.onSleepTimer(minutes) }, label = { Text(if (minutes == 0) stringResource(R.string.off) else stringResource(R.string.minutes_value, minutes)) }) } } } }
            item { HorizontalDivider() }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.local_asset_backup), fontWeight = FontWeight.SemiBold)
                            AssistChip(onClick = {}, label = { Text("Pro") })
                        }
                        Text(stringResource(R.string.local_asset_backup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.proUnlocked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = actions.onExportBackup, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_backup)) }
                            OutlinedButton(onClick = actions.onImportBackup, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore_backup)) }
                        } else OutlinedButton(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.WorkspacePremium, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.unlock_pro_backup)) }
                    }
                }
            }
            item { TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.proUnlocked) R.string.pro_unlocked_recheck else R.string.restore_jingdu_pro)) } }
        }
    }
}

@Composable
private fun readerPresetLabel(preset: ReaderPreset): String = stringResource(when (preset) {
    ReaderPreset.STANDARD -> R.string.reader_preset_standard
    ReaderPreset.COMFORT -> R.string.reader_preset_comfort
    ReaderPreset.LARGE -> R.string.reader_preset_large
    ReaderPreset.NIGHT -> R.string.reader_preset_night
    ReaderPreset.CUSTOM -> R.string.reader_preset_custom
})

@Composable
private fun readerPaletteLabel(palette: ReaderPalette): String = stringResource(when (palette) {
    ReaderPalette.PAPER -> R.string.paper
    ReaderPalette.LIGHT -> R.string.light
    ReaderPalette.SEPIA -> R.string.reader_theme_sepia
    ReaderPalette.NIGHT -> R.string.night
    ReaderPalette.OLED -> R.string.reader_oled
})

@Composable
private fun readerOrientationLabel(value: ReaderOrientation): String = stringResource(when (value) {
    ReaderOrientation.SYSTEM -> R.string.reader_orientation_system
    ReaderOrientation.PORTRAIT -> R.string.reader_orientation_portrait
    ReaderOrientation.LANDSCAPE -> R.string.reader_orientation_landscape
})

@Composable
private fun readerVolumeLabel(value: ReaderVolumeKeyMode): String = stringResource(when (value) {
    ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS -> R.string.reader_volume_except_tts
    ReaderVolumeKeyMode.ALWAYS_PAGE -> R.string.reader_volume_always
    ReaderVolumeKeyMode.SYSTEM_VOLUME -> R.string.reader_volume_system
})

@Composable
private fun readerColumnsLabel(value: ReaderWideColumns): String = stringResource(when (value) {
    ReaderWideColumns.AUTO -> R.string.reader_columns_auto
    ReaderWideColumns.SINGLE -> R.string.reader_columns_single
    ReaderWideColumns.DOUBLE -> R.string.reader_columns_double
})

@Composable
private fun readerWeightLabel(value: ReaderFontWeight): String = stringResource(when (value) {
    ReaderFontWeight.NORMAL -> R.string.reader_weight_normal
    ReaderFontWeight.MEDIUM -> R.string.reader_weight_medium
    ReaderFontWeight.SEMIBOLD -> R.string.reader_weight_semibold
})

@Composable
private fun chineseModeLabel(mode: ChineseDisplayMode): String = stringResource(when (mode) {
    ChineseDisplayMode.ORIGINAL -> R.string.chinese_original
    ChineseDisplayMode.SIMPLIFIED -> R.string.chinese_simplified
    ChineseDisplayMode.TRADITIONAL -> R.string.chinese_traditional
    ChineseDisplayMode.TAIWAN -> R.string.chinese_taiwan
    ChineseDisplayMode.TAIWAN_PHRASES -> R.string.chinese_taiwan_phrases
    ChineseDisplayMode.HONG_KONG -> R.string.chinese_hong_kong
})

@Composable
private fun ReaderSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}
