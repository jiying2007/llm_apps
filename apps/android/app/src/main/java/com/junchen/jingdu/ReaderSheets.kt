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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
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
    var mode by rememberSaveable { mutableStateOf(RepairRuleMode.LITERAL) }
    var globalFind by rememberSaveable { mutableStateOf("") }
    var globalReplacement by rememberSaveable { mutableStateOf("") }
    var globalMode by rememberSaveable { mutableStateOf(RepairRuleMode.LINE_GLOB) }

    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SheetTitle("净读", "本地识别干扰文本；源 TXT 永不修改、永不上传") }
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
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("智能净读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "扫描高频重复行、网址、站点推广和水印；扫描与结果预览永久免费。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.proUnlocked) {
                                AssistChip(onClick = {}, label = { Text("Pro") }, leadingIcon = {
                                    Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
                                })
                            }
                        }
                        OutlinedButton(onClick = actions.onAnalyzeSmartClean, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.smartCleanAnalyzed) "重新扫描干扰文本" else "免费扫描干扰文本")
                        }

                        if (state.smartCleanAnalyzed && state.noiseCandidates.isEmpty()) {
                            Text("没有发现高置信度干扰文本，可继续使用手动规则。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.noiseCandidates.isNotEmpty()) {
                            val selectedCount = state.noiseCandidates.count { it.selected }
                            val totalOccurrences = state.noiseCandidates.filter { it.selected }.sumOf { it.count }
                            Text(
                                "发现 ${state.noiseCandidates.size} 类 · 已选 $selectedCount 类 · 预计处理 $totalOccurrences 处",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.noiseCandidates.take(20).forEachIndexed { index, candidate ->
                                    ElevatedCard(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = candidate.selected,
                                                onCheckedChange = { actions.onToggleNoiseCandidate(index) },
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "${candidate.reason} · ${candidate.count} 次 · 置信 ${candidate.score}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    candidate.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = if (state.proUnlocked) actions.onApplySmartClean else actions.onUpgradePro,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedCount > 0,
                            ) {
                                if (!state.proUnlocked) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    if (state.proUnlocked) {
                                        "应用已选建议并预览"
                                    } else {
                                        "解锁 Pro 应用建议${state.proPrice?.let { " · $it" } ?: ""}"
                                    },
                                )
                            }
                            if (!state.proUnlocked) {
                                TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) {
                                    Text("已购买？恢复 Pro")
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { Text("本书手动规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == RepairRuleMode.LITERAL,
                        onClick = { mode = RepairRuleMode.LITERAL },
                        label = { Text("精确文本") },
                    )
                    FilterChip(
                        selected = mode == RepairRuleMode.LINE_GLOB,
                        onClick = { mode = RepairRuleMode.LINE_GLOB },
                        label = { Text(if (state.proUnlocked) "整行通配 *" else "整行通配 * · Pro") },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (mode == RepairRuleMode.LITERAL) "查找文本" else "整行模式，例如 *请收藏本站*")
                    },
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
                        actions.onAddRule(mode, find, replacement)
                        if (mode == RepairRuleMode.LITERAL || state.proUnlocked) {
                            find = ""
                            replacement = ""
                        }
                    },
                    enabled = find.isNotBlank(),
                ) { Text(if (mode == RepairRuleMode.LINE_GLOB && !state.proUnlocked) "解锁 Pro 添加通配规则" else "添加本书规则") }
            }
            if (state.repairRules.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("本书规则 · ${state.repairRules.size}", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = actions.onClearRules) { Text("清空") }
                    }
                }
            }
            items(state.repairRules.indices.toList()) { index ->
                val rule = state.repairRules[index]
                RuleCard(rule = rule, onDelete = { actions.onDeleteRule(index) })
            }

            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("全局规则库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text("Pro") })
                }
            }
            item {
                Text(
                    "全局规则会自动参与所有书籍的净读生成，适合长期积累个人广告/水印清理资产。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!state.proUnlocked) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("一次买断，本地高级能力永久使用", fontWeight = FontWeight.SemiBold)
                            Text(
                                "解锁智能建议一键应用、整行通配、全局规则库和规则导入/导出。基础阅读、搜索、目录、书签、排版和 TTS 始终免费。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("解锁净读 Pro${state.proPrice?.let { " · $it" } ?: ""}")
                            }
                            TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) {
                                Text("恢复购买")
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = actions.onInstallRecommendedRules, label = { Text("加入推荐规则") })
                        AssistChip(onClick = actions.onImportGlobalRules, label = { Text("导入") })
                        AssistChip(onClick = actions.onExportGlobalRules, label = { Text("导出") })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = globalMode == RepairRuleMode.LITERAL,
                            onClick = { globalMode = RepairRuleMode.LITERAL },
                            label = { Text("精确文本") },
                        )
                        FilterChip(
                            selected = globalMode == RepairRuleMode.LINE_GLOB,
                            onClick = { globalMode = RepairRuleMode.LINE_GLOB },
                            label = { Text("整行通配 *") },
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = globalFind,
                        onValueChange = { globalFind = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (globalMode == RepairRuleMode.LITERAL) "全局查找文本" else "全局整行模式") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = globalReplacement,
                        onValueChange = { globalReplacement = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("替换为（留空表示删除）") },
                        singleLine = true,
                    )
                }
                item {
                    Button(
                        onClick = {
                            actions.onAddGlobalRule(globalMode, globalFind, globalReplacement)
                            globalFind = ""
                            globalReplacement = ""
                        },
                        enabled = globalFind.isNotBlank(),
                    ) { Text("添加全局规则") }
                }
                if (state.globalRules.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("全局规则 · ${state.globalRules.size}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = actions.onClearGlobalRules) { Text("清空") }
                        }
                    }
                    items(state.globalRules.indices.toList()) { index ->
                        RuleCard(rule = state.globalRules[index], onDelete = { actions.onDeleteGlobalRule(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: RepairRule, onDelete: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (rule.mode == RepairRuleMode.LINE_GLOB) "通配 · ${rule.find}" else rule.find,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (rule.replacement.isEmpty()) "→ 删除" else "→ ${rule.replacement}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除净读规则")
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
