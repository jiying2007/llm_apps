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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SearchSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("全文搜索", "在当前文本中查找，结果点击即跳转")
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入关键词") },
                trailingIcon = {
                    IconButton(onClick = { actions.onSearch(state.searchQuery) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            if (state.searchResults.isEmpty()) {
                Text("输入关键词后搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(state.searchResults, key = { it.offset }) { hit ->
                        TextButton(
                            onClick = { actions.onJump(hit.offset) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Text(hit.context, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChaptersSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            SheetTitle(
                "目录",
                if (state.chapters.isEmpty()) "未检测到章节，或仍在生成" else "${state.chapters.size} 个章节",
            )
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(state.chapters, key = { it.offset }) { chapter ->
                    TextButton(onClick = { actions.onJump(chapter.offset) }, modifier = Modifier.fillMaxWidth()) {
                        Text(chapter.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookmarksSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle(
                "书签",
                if (state.cleanMode) "净读预览不会写入原文书签" else "书签绑定当前文本 revision",
            )
            Button(onClick = actions.onAddBookmark, enabled = !state.cleanMode, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bookmark, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加当前位置")
            }
            Spacer(Modifier.height(10.dp))
            if (state.bookmarks.isEmpty()) {
                Text("还没有书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(state.bookmarks, key = { it.offset }) { mark ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { actions.onJump(mark.offset) }, modifier = Modifier.weight(1f)) {
                                Text(
                                    "${(mark.progressFraction * 100).roundToInt()}% · 位置 ${mark.offset}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                )
                            }
                            IconButton(onClick = { actions.onDeleteBookmark(mark.offset) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除书签")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CleanSheet(state: AppUiState, actions: JingduActions) {
    var find by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SheetTitle("净读", "规则只作用于私有派生文本，源 TXT 永不修改") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.cleanMode,
                        onClick = { if (state.cleanMode) actions.onToggleCleanPreview() },
                        label = { Text("原文") },
                    )
                    FilterChip(
                        selected = state.cleanMode,
                        onClick = { if (!state.cleanMode) actions.onToggleCleanPreview() },
                        label = { Text("净读预览") },
                    )
                    AssistChip(onClick = actions.onExportClean, label = { Text("导出 TXT") })
                }
            }
            item {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("查找文本") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("替换为（留空表示删除）") },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        actions.onAddRule(find, replacement)
                        find = ""
                        replacement = ""
                    },
                    enabled = find.isNotBlank(),
                ) { Text("添加规则") }
            }
            if (state.repairRules.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("当前规则 · ${state.repairRules.size}", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = actions.onClearRules) { Text("清空") }
                    }
                }
            }
            items(state.repairRules.indices.toList()) { index ->
                val rule = state.repairRules[index]
                ElevatedCard {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.find, fontWeight = FontWeight.Medium)
                            Text(
                                if (rule.replacement.isEmpty()) "→ 删除" else "→ ${rule.replacement}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { actions.onDeleteRule(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除净读规则")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EncodingSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SheetTitle("文本编码", "乱码时可直接用私有源副本重新解码，无需重新选文件")
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(BookRepository.ENCODINGS.toList()) { encoding ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.currentBook?.encoding == encoding,
                                onClick = { actions.onEncodingSelected(encoding) },
                            )
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (encoding == BookRepository.AUTO) "自动识别" else encoding, modifier = Modifier.weight(1f))
                        if (state.currentBook?.encoding == encoding) {
                            Text("当前", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun SettingsSheet(state: AppUiState, actions: JingduActions) {
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SheetTitle("阅读设置", "排版只影响显示，不修改文本内容") }
            item {
                SettingGroup("页面色调") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderPalette.entries.forEach { palette ->
                            FilterChip(
                                selected = settings.palette == palette,
                                onClick = { actions.onSettingsChanged(settings.copy(palette = palette)) },
                                label = {
                                    Text(when (palette) {
                                        ReaderPalette.PAPER -> "纸张"
                                        ReaderPalette.LIGHT -> "明亮"
                                        ReaderPalette.NIGHT -> "夜间"
                                    })
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingGroup("字体") {
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
                SliderSetting("字号", settings.fontSizeSp, 16f..34f, "${settings.fontSizeSp.roundToInt()}sp") {
                    actions.onSettingsChanged(settings.copy(fontSizeSp = it))
                }
            }
            item {
                SliderSetting(
                    "行距", settings.lineHeightMultiplier, 1.2f..2.0f,
                    String.format("%.2f×", settings.lineHeightMultiplier),
                ) { actions.onSettingsChanged(settings.copy(lineHeightMultiplier = it)) }
            }
            item {
                SliderSetting(
                    "左右留白", settings.horizontalPaddingDp, 12f..48f,
                    "${settings.horizontalPaddingDp.roundToInt()}dp",
                ) { actions.onSettingsChanged(settings.copy(horizontalPaddingDp = it)) }
            }
            item { HorizontalDivider() }
            item {
                SettingGroup("朗读") {
                    SliderSetting(
                        "语速", settings.ttsRate, 0.6f..1.8f,
                        String.format("%.1f×", settings.ttsRate),
                    ) { actions.onSettingsChanged(settings.copy(ttsRate = it)) }
                    SliderSetting(
                        "音调", settings.ttsPitch, 0.7f..1.4f,
                        String.format("%.1f×", settings.ttsPitch),
                    ) { actions.onSettingsChanged(settings.copy(ttsPitch = it)) }
                    OutlinedButton(onClick = actions.onToggleTts, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (state.ttsPlaying) Icons.Default.Pause else Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.ttsPlaying) "暂停朗读" else "从当前位置朗读")
                    }
                }
            }
            item {
                SettingGroup("自动翻页") {
                    SliderSetting(
                        "间隔", settings.autoPageDelayMs.toFloat(), 2500f..15000f,
                        String.format("%.1f 秒", settings.autoPageDelayMs / 1000f),
                    ) { actions.onSettingsChanged(settings.copy(autoPageDelayMs = it.toLong())) }
                    OutlinedButton(onClick = actions.onToggleAutoPaging, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.autoPaging) "停止自动翻页" else "开始自动翻页")
                    }
                }
            }
            item {
                SettingGroup("睡眠定时") {
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
        }
    }
}

@Composable
private fun SliderSetting(
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
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
