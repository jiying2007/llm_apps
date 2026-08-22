package com.junchen.jingdu

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

data class JingduActions(
    val onImport: () -> Unit,
    val onOpenBook: (String) -> Unit,
    val onDeleteLibraryBook: (String) -> Unit,
    val onBackToLibrary: () -> Unit,
    val onNavigatePrevious: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onSeekFraction: (Float) -> Unit,
    val onVisibleCharsChanged: (Long) -> Unit,
    val onOpenPanel: (ReaderPanel) -> Unit,
    val onClosePanel: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onSearch: (String) -> Unit,
    val onJump: (Long) -> Unit,
    val onAddBookmark: () -> Unit,
    val onDeleteBookmark: (Long) -> Unit,
    val onAddRule: (String, String) -> Unit,
    val onDeleteRule: (Int) -> Unit,
    val onClearRules: () -> Unit,
    val onToggleCleanPreview: () -> Unit,
    val onExportClean: () -> Unit,
    val onEncodingSelected: (String) -> Unit,
    val onSettingsChanged: (ReaderSettings) -> Unit,
    val onToggleTts: () -> Unit,
    val onToggleAutoPaging: () -> Unit,
    val onSleepTimer: (Int) -> Unit,
    val onRequestDeleteCurrent: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onConfirmDeleteCurrent: () -> Unit,
    val onMessageConsumed: () -> Unit,
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF386A52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0D1),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4F6357),
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFF8FAF6),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF9DD5B6),
    onPrimary = Color(0xFF073823),
    primaryContainer = Color(0xFF20513A),
    onPrimaryContainer = Color(0xFFB9F0D1),
    secondary = Color(0xFFB6CCBD),
    surface = Color(0xFF111411),
    background = Color(0xFF0E110F),
)

@Composable
fun JingduApp(state: AppUiState, actions: JingduActions) {
    val dark = state.settings.palette == ReaderPalette.NIGHT
    MaterialTheme(colorScheme = if (dark) BrandDark else BrandLight) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                actions.onMessageConsumed()
            }
        }

        BackHandler(enabled = state.panel != null || state.screen == AppScreen.READER) {
            if (state.panel != null) actions.onClosePanel() else actions.onBackToLibrary()
        }

        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, actions, snackbar)
                AppScreen.READER -> ReaderScreen(state, actions, snackbar)
            }
            state.busyLabel?.let { BusyOverlay(it) }
        }

        state.panel?.let { panel ->
            when (panel) {
                ReaderPanel.SEARCH -> SearchSheet(state, actions)
                ReaderPanel.CHAPTERS -> ChaptersSheet(state, actions)
                ReaderPanel.BOOKMARKS -> BookmarksSheet(state, actions)
                ReaderPanel.CLEAN -> CleanSheet(state, actions)
                ReaderPanel.SETTINGS -> SettingsSheet(state, actions)
                ReaderPanel.ENCODING -> EncodingSheet(state, actions)
            }
        }

        if (state.deleteConfirmation) {
            AlertDialog(
                onDismissRequest = actions.onDismissDelete,
                title = { Text("删除私有副本？") },
                text = { Text("只删除净读保存的私有副本、阅读进度、书签和净读规则。原始 TXT 不会被删除。") },
                confirmButton = {
                    TextButton(onClick = actions.onConfirmDeleteCurrent) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismissDelete) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun LibraryScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Text("净读", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "本地 TXT · 无广告 · 不上传",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onImport,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("导入 TXT") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.books.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier.padding(padding),
                onImport = actions.onImport,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onOpen = { actions.onOpenBook(book.id) },
                        onDelete = { deleteTarget = book.id },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("从书架移除？") },
            text = { Text("只删除应用私有副本，设备上的源 TXT 不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDeleteLibraryBook(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier, onImport: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("把本地 TXT 变得好读", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "自动识别常见中文编码，支持大文件、搜索、目录、净读规则和离线朗读。源文件始终保持不变。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("选择 TXT 文件")
        }
    }
}

@Composable
private fun BookCard(book: BookCardModel, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onOpen,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(width = 58.dp, height = 78.dp),
                shape = MaterialTheme.shapes.medium,
                color = coverColor(book.id),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = book.name.trim().firstOrNull()?.toString()?.uppercase() ?: "TXT",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stripTxt(book.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${book.encoding} · ${formatBytes(book.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small),
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (book.charCount > 0) "${(book.progressFraction * 100).roundToInt()}%" else "未完成索引",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatTouched(book.touchedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "书籍操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("继续阅读") },
                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        onClick = { menu = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除私有副本") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    val book = state.currentBook ?: return
    var more by remember { mutableStateOf(false) }
    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = actions.onBackToLibrary) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回书架")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stripTxt(book.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.cleanMode) "净读预览" else "${(fraction * 100).roundToInt()}% · ${book.encoding}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { actions.onOpenPanel(ReaderPanel.SEARCH) }) {
                        Icon(Icons.Default.Search, contentDescription = "全文搜索")
                    }
                    IconButton(onClick = { actions.onOpenPanel(ReaderPanel.CHAPTERS) }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "目录")
                    }
                    Box {
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多阅读工具")
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("书签") },
                                leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                                onClick = { more = false; actions.onOpenPanel(ReaderPanel.BOOKMARKS) },
                            )
                            DropdownMenuItem(
                                text = { Text("净读") },
                                leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null) },
                                onClick = { more = false; actions.onOpenPanel(ReaderPanel.CLEAN) },
                            )
                            DropdownMenuItem(
                                text = { Text("文本编码") },
                                leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
                                onClick = { more = false; actions.onOpenPanel(ReaderPanel.ENCODING) },
                            )
                            DropdownMenuItem(
                                text = { Text("阅读设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { more = false; actions.onOpenPanel(ReaderPanel.SETTINGS) },
                            )
                            DropdownMenuItem(
                                text = { Text("删除私有副本") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { more = false; actions.onRequestDeleteCurrent() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ReaderBottomBar(
                fraction = fraction,
                ttsPlaying = state.ttsPlaying,
                autoPaging = state.autoPaging,
                onPrevious = actions.onNavigatePrevious,
                onNext = actions.onNavigateNext,
                onSeek = actions.onSeekFraction,
                onTts = actions.onToggleTts,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ReaderPage(
            text = state.pageText,
            settings = state.settings,
            modifier = Modifier.padding(padding),
            onVisibleCharsChanged = actions.onVisibleCharsChanged,
        )
    }
}

@Composable
private fun ReaderPage(
    text: String,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    onVisibleCharsChanged: (Long) -> Unit,
) {
    val pageBackground = when (settings.palette) {
        ReaderPalette.PAPER -> Color(0xFFF7F0DE)
        ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
        ReaderPalette.NIGHT -> Color(0xFF151713)
    }
    val pageText = when (settings.palette) {
        ReaderPalette.NIGHT -> Color(0xFFE8E5DA)
        else -> Color(0xFF24241F)
    }
    val family = if (settings.typeface == ReaderTypeface.SERIF) FontFamily.Serif else FontFamily.SansSerif

    Surface(modifier = modifier.fillMaxSize(), color = pageBackground) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxTextWidth = if (maxWidth >= 840.dp) 760.dp else maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 18.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier
                            .widthIn(max = maxTextWidth)
                            .fillMaxHeight(),
                        style = TextStyle(
                            color = pageText,
                            fontFamily = family,
                            fontSize = settings.fontSizeSp.sp,
                            lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                            textAlign = TextAlign.Justify,
                        ),
                        overflow = TextOverflow.Clip,
                        onTextLayout = { layout ->
                            if (layout.lineCount <= 0 || text.isEmpty()) return@Text
                            val end = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                                .coerceIn(0, text.length)
                            val count = text.codePointCount(0, end).toLong()
                            if (count >= ReaderController.MIN_PAGE_CHARS) onVisibleCharsChanged(count)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    fraction: Float,
    ttsPlaying: Boolean,
    autoPaging: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onTts: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一页")
                }
                Slider(
                    value = fraction,
                    onValueChange = onSeek,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "阅读进度" },
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一页")
                }
                IconButton(onClick = onTts) {
                    Icon(
                        if (ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ttsPlaying) "暂停朗读" else "开始朗读",
                    )
                }
            }
            if (autoPaging || ttsPlaying) {
                Text(
                    when {
                        ttsPlaying -> "正在朗读"
                        autoPaging -> "自动翻页中"
                        else -> ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SearchSheet(state: AppUiState, actions: JingduActions) {
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.searchResults) { hit ->
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
private fun ChaptersSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            SheetTitle("目录", if (state.chapters.isEmpty()) "正在识别或未检测到章节" else "${state.chapters.size} 个章节")
            LazyColumn {
                items(state.chapters) { chapter ->
                    TextButton(onClick = { actions.onJump(chapter.offset) }, modifier = Modifier.fillMaxWidth()) {
                        Text(chapter.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("书签", if (state.cleanMode) "净读预览不会写入原文书签" else "保存在当前文本 revision 的原文位置")
            Button(onClick = actions.onAddBookmark, enabled = !state.cleanMode, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bookmark, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加当前位置")
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn {
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

@Composable
private fun CleanSheet(state: AppUiState, actions: JingduActions) {
    var find by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
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
                    AssistChip(onClick = actions.onExportClean, label = { Text("导出净读 TXT") })
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
                    label = { Text("替换为（可留空表示删除）") },
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
private fun EncodingSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SheetTitle("文本编码", "乱码时可直接用私有 source.bin 重新解码，不需要重新选文件")
            BookRepository.ENCODINGS.forEach { encoding ->
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
                    if (state.currentBook?.encoding == encoding) Text("当前", color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingsSheet(state: AppUiState, actions: JingduActions) {
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SheetTitle("阅读设置", "排版只影响阅读显示，不修改文本内容") }
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
                SliderSetting("行距", settings.lineHeightMultiplier, 1.2f..2.0f, String.format("%.2f×", settings.lineHeightMultiplier)) {
                    actions.onSettingsChanged(settings.copy(lineHeightMultiplier = it))
                }
            }
            item {
                SliderSetting("左右留白", settings.horizontalPaddingDp, 12f..48f, "${settings.horizontalPaddingDp.roundToInt()}dp") {
                    actions.onSettingsChanged(settings.copy(horizontalPaddingDp = it))
                }
            }
            item { HorizontalDivider() }
            item {
                SettingGroup("朗读") {
                    SliderSetting("语速", settings.ttsRate, 0.6f..1.8f, String.format("%.1f×", settings.ttsRate)) {
                        actions.onSettingsChanged(settings.copy(ttsRate = it))
                    }
                    SliderSetting("音调", settings.ttsPitch, 0.7f..1.4f, String.format("%.1f×", settings.ttsPitch)) {
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
                SettingGroup("自动翻页") {
                    SliderSetting(
                        "间隔",
                        settings.autoPageDelayMs.toFloat(),
                        2500f..15000f,
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

@Composable
private fun BusyOverlay(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun stripTxt(name: String): String =
    if (name.lowercase().endsWith(".txt")) name.dropLast(4) else name

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTouched(value: Long): String {
    if (value <= 0) return "未阅读"
    return DateFormat.getDateInstance(DateFormat.SHORT).format(Date(value))
}

private fun coverColor(id: String): Color {
    val hash = id.take(8).toLongOrNull(16) ?: id.hashCode().toLong()
    val palette = listOf(
        Color(0xFF4E6E5D), Color(0xFF665C80), Color(0xFF7B5948),
        Color(0xFF3E6575), Color(0xFF6D6044), Color(0xFF76556A),
    )
    return palette[(kotlin.math.abs(hash) % palette.size).toInt()]
}
