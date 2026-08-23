@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ProductSettingsSheet(state: AppUiState, actions: JingduActions) {
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text("阅读设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "排版、朗读和自动翻页只影响本地阅读体验，不修改文本内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingSection("页面色调") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderPalette.entries.forEach { palette ->
                            FilterChip(
                                selected = settings.palette == palette,
                                onClick = { actions.onSettingsChanged(settings.copy(palette = palette)) },
                                label = {
                                    Text(
                                        when (palette) {
                                            ReaderPalette.PAPER -> "纸张"
                                            ReaderPalette.LIGHT -> "明亮"
                                            ReaderPalette.NIGHT -> "夜间"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingSection("字体") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTypeface.entries.forEach { family ->
                            FilterChip(
                                selected = settings.typeface == family,
                                onClick = { actions.onSettingsChanged(settings.copy(typeface = family)) },
                                label = { Text(if (family == ReaderTypeface.SYSTEM) "系统字体" else "衬线") },
                            )
                        }
                    }
                }
            }
            item {
                ReaderSlider("字号", settings.fontSizeSp, 16f..34f, "${settings.fontSizeSp.roundToInt()}sp") {
                    actions.onSettingsChanged(settings.copy(fontSizeSp = it))
                }
            }
            item {
                ReaderSlider(
                    "行距", settings.lineHeightMultiplier, 1.2f..2.0f,
                    String.format("%.2f×", settings.lineHeightMultiplier),
                ) { actions.onSettingsChanged(settings.copy(lineHeightMultiplier = it)) }
            }
            item {
                ReaderSlider(
                    "左右留白", settings.horizontalPaddingDp, 12f..48f,
                    "${settings.horizontalPaddingDp.roundToInt()}dp",
                ) { actions.onSettingsChanged(settings.copy(horizontalPaddingDp = it)) }
            }
            item { HorizontalDivider() }
            item {
                SettingSection("朗读") {
                    ReaderSlider("语速", settings.ttsRate, 0.6f..1.8f, String.format("%.1f×", settings.ttsRate)) {
                        actions.onSettingsChanged(settings.copy(ttsRate = it))
                    }
                    ReaderSlider("音调", settings.ttsPitch, 0.7f..1.4f, String.format("%.1f×", settings.ttsPitch)) {
                        actions.onSettingsChanged(settings.copy(ttsPitch = it))
                    }
                    OutlinedButton(onClick = actions.onToggleTts, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (state.ttsPlaying) Icons.Default.Pause else Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.ttsPlaying) "暂停朗读" else "从当前位置朗读")
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("离线朗读声音", fontWeight = FontWeight.SemiBold)
                            AssistChip(onClick = {}, label = { Text("Pro") })
                        }
                        Text(
                            "只显示系统 TTS 标记为无需网络的 voice；不会为了高级朗读上传书籍正文。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!state.proUnlocked) {
                            OutlinedButton(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("解锁 Pro 选择离线 voice${state.proPrice?.let { " · $it" } ?: ""}")
                            }
                        } else if (state.ttsVoices.isEmpty()) {
                            Text("当前系统 TTS 暂未提供可选离线 voice。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = settings.ttsVoiceName.isEmpty(),
                                    onClick = { actions.onSettingsChanged(settings.copy(ttsVoiceName = "")) },
                                    label = { Text("系统默认") },
                                )
                                state.ttsVoices.take(12).forEach { voice ->
                                    FilterChip(
                                        selected = settings.ttsVoiceName == voice.name,
                                        onClick = { actions.onSettingsChanged(settings.copy(ttsVoiceName = voice.name)) },
                                        label = { Text(voice.label) },
                                    )
                                }
                                if (state.ttsVoices.size > 12) {
                                    Text(
                                        "当前系统还有 ${state.ttsVoices.size - 12} 个离线 voice；优先展示前 12 个。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingSection("自动翻页") {
                    ReaderSlider(
                        "间隔", settings.autoPageDelayMs.toFloat(), 2500f..15000f,
                        String.format("%.1f 秒", settings.autoPageDelayMs / 1000f),
                    ) { actions.onSettingsChanged(settings.copy(autoPageDelayMs = it.toLong())) }
                    OutlinedButton(onClick = actions.onToggleAutoPaging, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.autoPaging) "停止自动翻页" else "开始自动翻页")
                    }
                }
            }
            item {
                SettingSection("睡眠定时") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = state.sleepMinutes == minutes,
                                onClick = { actions.onSleepTimer(minutes) },
                                label = { Text(if (minutes == 0) "关闭" else "$minutes 分钟") },
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("本地资产备份", fontWeight = FontWeight.SemiBold)
                            AssistChip(onClick = {}, label = { Text("Pro") })
                        }
                        Text(
                            "导出阅读设置、离线 voice 选择和全局净读规则。备份不包含任何书籍正文，也不会上传云端。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.proUnlocked) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = actions.onExportBackup, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("导出备份")
                                }
                                OutlinedButton(onClick = actions.onImportBackup, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("恢复备份")
                                }
                            }
                        } else {
                            OutlinedButton(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("解锁 Pro 备份个人规则与设置")
                            }
                        }
                    }
                }
            }
            item {
                if (state.proUnlocked) {
                    TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) {
                        Text("Pro 已解锁 · 重新校验购买")
                    }
                } else {
                    TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) {
                        Text("恢复净读 Pro 购买")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}
