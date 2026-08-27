package com.junchen.jingdu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

private data class TocPanelKey(val bookId: String, val length: Long, val chaptersHash: Int)
private data class TocPanelEntry(val base: TocQualityReport, val report: TocQualityReport)
private object TocPanelCache {
    private val entries = object : LinkedHashMap<TocPanelKey, TocPanelEntry>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TocPanelKey, TocPanelEntry>?): Boolean = size > 4
    }
    @Synchronized fun get(key: TocPanelKey): TocPanelEntry? = entries[key]
    @Synchronized fun put(key: TocPanelKey, entry: TocPanelEntry) { entries[key] = entry }
}

/** Canonical Smart TOC route with one Canvas and a bounded eight-row viewport. */
@Composable
internal fun ReaderSmartChaptersPanel(state: AppUiState, actions: JingduActions, currentPosition: () -> Long) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    var base by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var report by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var loading by remember(book?.id) { mutableStateOf(!state.chaptersLoaded) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var windowStart by rememberSaveable(book?.id) { mutableIntStateOf(0) }

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) { base = null; report = null; loading = false; windowStart = 0; return@LaunchedEffect }
        if (!state.chaptersLoaded) { loading = true; actions.onEnsureChapters(); return@LaunchedEffect }
        val key = TocPanelKey(book.id, state.length, state.chapters.hashCode())
        TocPanelCache.get(key)?.let { cached -> base = cached.base; report = cached.report; loading = false; return@LaunchedEffect }
        val computed = withContext(Dispatchers.Default) { SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) }) }
        base = computed; report = computed; TocPanelCache.put(key, TocPanelEntry(computed, computed)); loading = false
    }
    LaunchedEffect(report?.chapters?.size) {
        val count = report?.chapters?.size ?: 0
        windowStart = windowStart.coerceIn(0, maxOf(0, count - CHAPTER_WINDOW_ROWS))
    }

    val panelTitle = stringResource(R.string.smart_toc)
    val addLabel = stringResource(R.string.toc_add_here)
    val hideLabel = stringResource(R.string.toc_hide_heading)
    val resetLabel = stringResource(R.string.toc_reset_repairs)
    val previousLabel = stringResource(R.string.reader_access_previous)
    val nextLabel = stringResource(R.string.reader_access_next)
    val sourceHint = stringResource(R.string.toc_source_user_or_special)
    val colors = MaterialTheme.colorScheme
    val paints = rememberReaderCanvasTextPaint(colors.onSurface, colors.onSurfaceVariant, colors.primary)
    val rowTop = with(density) { 70.dp.toPx() }
    val rowHeight = with(density) { CHAPTER_ROW_HEIGHT.toPx() }
    val navY = rowTop + rowHeight * CHAPTER_WINDOW_ROWS + with(density) { 24.dp.toPx() }
    val resetY = navY + with(density) { 48.dp.toPx() }
    val edge = with(density) { 18.dp.toPx() }
    val current = report
    val chapters = current?.chapters.orEmpty()
    val end = minOf(chapters.size, windowStart + CHAPTER_WINDOW_ROWS)
    val quality = current?.let { stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount) }.orEmpty()

    fun cache(updated: TocQualityReport?) {
        report = updated
        val b = base
        val currentBook = book
        if (b != null && updated != null && currentBook != null) TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(b, updated))
    }
    fun hide(index: Int) {
        val currentBook = book ?: return
        val chapter = chapters.getOrNull(index) ?: return
        store.hide(currentBook.id, chapter.offset, state.length)
        cache(base?.let { store.apply(it, store.load(currentBook.id, state.length)) })
    }
    fun reset() {
        val currentBook = book ?: return
        store.reset(currentBook.id); report = base; windowStart = 0
        base?.let { TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(it, it)) }
    }

    val accessibilityActions = buildList {
        add(CustomAccessibilityAction(addLabel) { addDialog = true; true })
        for (index in windowStart until end) {
            val chapter = chapters[index]
            add(CustomAccessibilityAction(chapter.title) { actions.onJump(chapter.offset); true })
            add(CustomAccessibilityAction("$hideLabel: ${chapter.title}") { hide(index); true })
        }
        if (windowStart > 0) add(CustomAccessibilityAction(previousLabel) { windowStart = (windowStart - CHAPTER_WINDOW_ROWS).coerceAtLeast(0); true })
        if (end < chapters.size) add(CustomAccessibilityAction(nextLabel) { windowStart = (windowStart + CHAPTER_WINDOW_ROWS).coerceAtMost(maxOf(0, chapters.size - CHAPTER_WINDOW_ROWS)); true })
        add(CustomAccessibilityAction(resetLabel) { reset(); true })
    }

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Box(Modifier.fillMaxWidth().height(CHAPTER_PANEL_HEIGHT)) {
            ReaderCanvasPanel(
                height = CHAPTER_PANEL_HEIGHT,
                description = panelTitle,
                actions = accessibilityActions,
                onTap = { point, width, _ ->
                    when {
                        point.y < rowTop && point.x > width - with(density) { 72.dp.toPx() } -> addDialog = true
                        point.y in rowTop..(rowTop + rowHeight * CHAPTER_WINDOW_ROWS) -> {
                            val local = ((point.y - rowTop) / rowHeight).toInt()
                            val index = windowStart + local
                            val chapter = chapters.getOrNull(index) ?: return@ReaderCanvasPanel
                            if (point.x > width - with(density) { 54.dp.toPx() }) hide(index) else actions.onJump(chapter.offset)
                        }
                        point.y in (navY - rowHeight / 2f)..(navY + rowHeight / 2f) -> {
                            if (point.x < width * 0.35f) windowStart = (windowStart - CHAPTER_WINDOW_ROWS).coerceAtLeast(0)
                            else if (point.x > width * 0.65f) windowStart = (windowStart + CHAPTER_WINDOW_ROWS).coerceAtMost(maxOf(0, chapters.size - CHAPTER_WINDOW_ROWS))
                        }
                        point.y >= resetY - rowHeight / 2f -> reset()
                    }
                },
            ) {
                drawReaderText(panelTitle, paints.title, edge, 28.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderText(if (loading) "…" else quality, paints.small, edge, 50.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderButton(Rect(size.width - edge - 46.dp.toPx(), 12.dp.toPx(), size.width - edge, 54.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                if (loading) drawRoundRect(colors.primary, Offset(edge, 61.dp.toPx()), androidx.compose.ui.geometry.Size((size.width - edge * 2f) * 0.45f, 2.dp.toPx()))
                for (index in windowStart until end) {
                    val chapter = chapters[index]
                    val local = index - windowStart
                    val centerY = rowTop + rowHeight * (local + 0.5f)
                    drawReaderText(chapter.title, paints.normal, edge + 8.dp.toPx(), centerY, size.width - edge * 2f - 64.dp.toPx())
                    if (chapter.source != "core") drawCircle(colors.primary, 3.dp.toPx(), Offset(size.width - edge - 46.dp.toPx(), centerY))
                    val x = size.width - edge - 18.dp.toPx()
                    val r = 6.dp.toPx()
                    drawLine(colors.onSurfaceVariant, Offset(x - r, centerY - r), Offset(x + r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.onSurfaceVariant, Offset(x + r, centerY - r), Offset(x - r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.outlineVariant, Offset(edge, rowTop + rowHeight * (local + 1f)), Offset(size.width - edge, rowTop + rowHeight * (local + 1f)), 1.dp.toPx())
                }
                if (chapters.isNotEmpty()) {
                    drawReaderButton(Rect(edge, navY - 18.dp.toPx(), edge + 64.dp.toPx(), navY + 18.dp.toPx()), "↑", paints.action, if (windowStart > 0) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                    drawReaderText("${windowStart + 1}–$end / ${chapters.size}", paints.small, edge + 72.dp.toPx(), navY, size.width - edge * 2f - 144.dp.toPx(), centered = true)
                    drawReaderButton(Rect(size.width - edge - 64.dp.toPx(), navY - 18.dp.toPx(), size.width - edge, navY + 18.dp.toPx()), "↓", paints.action, if (end < chapters.size) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                }
                drawReaderButton(Rect(edge, resetY - 19.dp.toPx(), size.width - edge, resetY + 19.dp.toPx()), resetLabel, paints.action, colors.primary, outline = colors.outlineVariant)
            }
        }
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(addLabel) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.toc_custom_title)) }) },
        confirmButton = { TextButton(onClick = {
            store.add(book.id, currentPosition(), title, state.length)
            cache(base?.let { store.apply(it, store.load(book.id, state.length)) })
            title = ""; addDialog = false
        }, enabled = title.isNotBlank()) { Text(stringResource(R.string.toc_add_action)) } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private val CHAPTER_ROW_HEIGHT = 42.dp
private val CHAPTER_PANEL_HEIGHT = 478.dp
private const val CHAPTER_WINDOW_ROWS = 8
