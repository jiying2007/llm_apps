package com.junchen.jingdu

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.abs

private data class TocPanelKey(val bookId: String, val revision: String, val length: Long, val chaptersHash: Int)
private data class TocPanelEntry(val base: TocQualityReport, val report: TocQualityReport)
private object TocPanelCache {
    private val entries = object : LinkedHashMap<TocPanelKey, TocPanelEntry>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TocPanelKey, TocPanelEntry>?): Boolean = size > 4
    }
    @Synchronized fun get(key: TocPanelKey): TocPanelEntry? = entries[key]
    @Synchronized fun get(bookId: String, revision: String, length: Long): TocPanelEntry? =
        entries.entries.firstOrNull { (key, _) ->
            key.bookId == bookId && key.revision == revision && key.length == length
        }?.value
    @Synchronized fun put(key: TocPanelKey, entry: TocPanelEntry) { entries[key] = entry }
}

internal fun prewarmReaderSmartChaptersPanel(
    context: Context,
    bookId: String,
    revision: String,
    length: Long,
) {
    if (bookId.isBlank() || revision.isBlank() || length <= 0L) return
    if (TocPanelCache.get(bookId, revision, length) != null) return
    val appContext = context.applicationContext
    val base = SmartTocCacheStore(appContext).load(bookId, revision, length) ?: return
    val key = TocPanelKey(bookId, revision, length, base.chapters.hashCode())
    if (TocPanelCache.get(key) != null) return
    val store = TocOverrideStore(appContext)
    val computed = store.apply(base, store.load(bookId, length))
    TocPanelCache.put(key, TocPanelEntry(base, computed))
}

/** Canonical Smart TOC route with touch-safe 48dp rows, direct row targets and swipe/page navigation. */
@Composable
internal fun ReaderSmartChaptersPanel(state: AppUiState, actions: JingduActions, currentPosition: () -> Long) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    val derivedCache = remember { SmartTocCacheStore(context) }
    val initial = remember(book?.id, book?.normalizedSha256, state.length) {
        book?.let { TocPanelCache.get(it.id, it.normalizedSha256, state.length) }
    }
    var base by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(initial?.base) }
    var report by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(initial?.report) }
    var loading by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(initial == null && !state.chaptersLoaded) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var windowStart by rememberSaveable(book?.id) { mutableIntStateOf(0) }

    LaunchedEffect(book?.id, book?.normalizedSha256, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) { base = null; report = null; loading = false; windowStart = 0; return@LaunchedEffect }
        if (initial != null) return@LaunchedEffect
        val cachedBase = withContext(Dispatchers.IO) { derivedCache.load(book.id, book.normalizedSha256, state.length) }
        if (cachedBase != null) {
            val key = TocPanelKey(book.id, book.normalizedSha256, state.length, cachedBase.chapters.hashCode())
            TocPanelCache.get(key)?.let { cached ->
                base = cached.base; report = cached.report; loading = false
                return@LaunchedEffect
            }
            val overrides = withContext(Dispatchers.IO) { store.load(book.id, state.length) }
            val computed = withContext(Dispatchers.Default) { store.apply(cachedBase, overrides) }
            base = cachedBase
            report = computed
            TocPanelCache.put(key, TocPanelEntry(cachedBase, computed))
            loading = false
            return@LaunchedEffect
        }
        if (!state.chaptersLoaded) { loading = true; actions.onEnsureChapters(); return@LaunchedEffect }
        val key = TocPanelKey(book.id, book.normalizedSha256, state.length, state.chapters.hashCode())
        TocPanelCache.get(key)?.let { cached -> base = cached.base; report = cached.report; loading = false; return@LaunchedEffect }
        val computedBase = withContext(Dispatchers.Default) {
            SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
        }
        val overrides = withContext(Dispatchers.IO) { store.load(book.id, state.length) }
        val computed = withContext(Dispatchers.Default) { store.apply(computedBase, overrides) }
        base = computedBase
        report = computed
        TocPanelCache.put(key, TocPanelEntry(computedBase, computed))
        loading = false
    }
    LaunchedEffect(report?.chapters?.size, book?.id) {
        val values = report?.chapters.orEmpty()
        if (values.isEmpty()) { windowStart = 0; return@LaunchedEffect }
        val currentIndex = values.indexOfLast { it.offset <= currentPosition() }.coerceAtLeast(0)
        val pageStart = (currentIndex / CHAPTER_WINDOW_ROWS) * CHAPTER_WINDOW_ROWS
        windowStart = pageStart.coerceIn(0, maxOf(0, values.size - CHAPTER_WINDOW_ROWS))
    }

    val panelTitle = stringResource(R.string.smart_toc)
    val addLabel = stringResource(R.string.toc_add_here)
    val hideLabel = stringResource(R.string.toc_hide_heading)
    val resetLabel = stringResource(R.string.toc_reset_repairs)
    val previousLabel = stringResource(R.string.reader_access_previous)
    val nextLabel = stringResource(R.string.reader_access_next)
    val colors = MaterialTheme.colorScheme
    val paints = rememberReaderCanvasTextPaint(colors.onSurface, colors.onSurfaceVariant, colors.primary)
    val rowTop = with(density) { CHAPTER_ROWS_TOP.toPx() }
    val rowHeight = with(density) { CHAPTER_ROW_HEIGHT.toPx() }
    val navY = with(density) { CHAPTER_NAV_CENTER.toPx() }
    val resetY = with(density) { CHAPTER_RESET_CENTER.toPx() }
    val edge = with(density) { 18.dp.toPx() }
    val current = report
    val chapters = current?.chapters.orEmpty()
    val displayTitles = remember(chapters, state.settings.chineseMode, state.settings.chineseOverrides) {
        chapters.map { chapter -> ReaderTextPresentation.chapterTitle(chapter.title, state.settings) }
    }
    val activePosition = currentPosition()
    val currentIndex = chapters.indexOfLast { it.offset <= activePosition }
    val end = minOf(chapters.size, windowStart + CHAPTER_WINDOW_ROWS)
    val quality = current?.let { stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount) }.orEmpty()

    fun previousWindow() { windowStart = (windowStart - CHAPTER_WINDOW_ROWS).coerceAtLeast(0) }
    fun nextWindow() { windowStart = (windowStart + CHAPTER_WINDOW_ROWS).coerceAtMost(maxOf(0, chapters.size - CHAPTER_WINDOW_ROWS)) }
    fun panelKey(): TocPanelKey? = book?.let { TocPanelKey(it.id, it.normalizedSha256, state.length, base?.chapters?.hashCode() ?: state.chapters.hashCode()) }
    fun cache(updated: TocQualityReport?) {
        report = updated
        val b = base
        val key = panelKey()
        if (b != null && updated != null && key != null) TocPanelCache.put(key, TocPanelEntry(b, updated))
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
        val key = panelKey()
        if (base != null && key != null) TocPanelCache.put(key, TocPanelEntry(base!!, base!!))
    }

    val accessibilityActions = buildList {
        add(CustomAccessibilityAction(addLabel) { addDialog = true; true })
        for (index in windowStart until end) {
            val chapter = chapters[index]
            add(CustomAccessibilityAction(displayTitles[index]) { actions.onJump(chapter.offset); true })
            add(CustomAccessibilityAction("$hideLabel: ${displayTitles[index]}") { hide(index); true })
        }
        if (windowStart > 0) add(CustomAccessibilityAction(previousLabel) { previousWindow(); true })
        if (end < chapters.size) add(CustomAccessibilityAction(nextLabel) { nextWindow(); true })
        add(CustomAccessibilityAction(resetLabel) { reset(); true })
    }

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Box(Modifier.fillMaxWidth().height(CHAPTER_PANEL_HEIGHT)) {
            ReaderCanvasPanel(
                height = CHAPTER_PANEL_HEIGHT,
                description = panelTitle,
                actions = accessibilityActions,
                recordKey = listOf(colors, loading, windowStart, current, currentIndex),
                onTap = { point, width, _ ->
                    when {
                        point.y < rowTop && point.x > width - with(density) { 72.dp.toPx() } -> addDialog = true
                        point.y in rowTop..(rowTop + rowHeight * CHAPTER_WINDOW_ROWS) -> {
                            val local = ((point.y - rowTop) / rowHeight).toInt()
                            val index = windowStart + local
                            val chapter = chapters.getOrNull(index) ?: return@ReaderCanvasPanel
                            if (point.x > width - with(density) { 58.dp.toPx() }) hide(index) else actions.onJump(chapter.offset)
                        }
                        point.y in (navY - rowHeight / 2f)..(navY + rowHeight / 2f) -> {
                            if (point.x < width * 0.40f) previousWindow()
                            else if (point.x > width * 0.60f) nextWindow()
                        }
                        point.y >= resetY - rowHeight / 2f -> reset()
                    }
                },
                onSwipe = { delta ->
                    if (abs(delta.y) > abs(delta.x) * 1.15f) {
                        if (delta.y < 0f) nextWindow() else previousWindow()
                    }
                },
            ) {
                drawReaderText(panelTitle, paints.title, edge, 28.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderText(if (loading) "…" else quality, paints.small, edge, 52.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderButton(Rect(size.width - edge - 48.dp.toPx(), 10.dp.toPx(), size.width - edge, 58.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                if (loading) drawRoundRect(colors.primary, Offset(edge, 64.dp.toPx()), androidx.compose.ui.geometry.Size((size.width - edge * 2f) * 0.45f, 2.dp.toPx()))
                for (index in windowStart until end) {
                    val chapter = chapters[index]
                    val local = index - windowStart
                    val top = rowTop + rowHeight * local
                    val centerY = top + rowHeight / 2f
                    if (index == currentIndex) {
                        drawRoundRect(
                            colors.primaryContainer.copy(alpha = 0.55f),
                            Offset(edge, top + 3.dp.toPx()),
                            androidx.compose.ui.geometry.Size(size.width - edge * 2f, rowHeight - 6.dp.toPx()),
                            androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        )
                    }
                    drawReaderText(displayTitles[index], paints.normal, edge + 10.dp.toPx(), centerY, size.width - edge * 2f - 70.dp.toPx())
                    if (chapter.source != "core") drawCircle(colors.primary, 3.dp.toPx(), Offset(size.width - edge - 48.dp.toPx(), centerY))
                    val x = size.width - edge - 20.dp.toPx()
                    val r = 6.dp.toPx()
                    drawLine(colors.onSurfaceVariant, Offset(x - r, centerY - r), Offset(x + r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.onSurfaceVariant, Offset(x + r, centerY - r), Offset(x - r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.outlineVariant, Offset(edge, top + rowHeight), Offset(size.width - edge, top + rowHeight), 1.dp.toPx())
                }
                if (chapters.isNotEmpty()) {
                    drawReaderButton(Rect(edge, navY - 24.dp.toPx(), edge + 84.dp.toPx(), navY + 24.dp.toPx()), "↑", paints.action, if (windowStart > 0) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                    drawReaderText("${windowStart + 1}–$end / ${chapters.size}", paints.small, edge + 92.dp.toPx(), navY, size.width - edge * 2f - 184.dp.toPx(), centered = true)
                    drawReaderButton(Rect(size.width - edge - 84.dp.toPx(), navY - 24.dp.toPx(), size.width - edge, navY + 24.dp.toPx()), "↓", paints.action, if (end < chapters.size) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                }
                drawReaderButton(Rect(edge, resetY - 24.dp.toPx(), size.width - edge, resetY + 24.dp.toPx()), resetLabel, paints.action, colors.primary, outline = colors.outlineVariant)
            }

            for (index in windowStart until end) {
                val local = index - windowStart
                val y = CHAPTER_ROWS_TOP + CHAPTER_ROW_HEIGHT * local
                Box(
                    Modifier.fillMaxWidth().height(CHAPTER_ROW_HEIGHT).offset(y = y).padding(end = 60.dp)
                        .clickable { actions.onJump(chapters[index].offset) }
                        .semantics { contentDescription = displayTitles[index] },
                )
                Box(
                    Modifier.align(Alignment.TopEnd).offset(y = y).padding(end = 12.dp).size(48.dp, CHAPTER_ROW_HEIGHT)
                        .clickable { hide(index) }
                        .semantics { contentDescription = "$hideLabel: ${displayTitles[index]}" },
                )
            }
            ReaderCanvasSemanticTarget(previousLabel, 18.dp, CHAPTER_NAV_TOP, 84.dp, 48.dp, ::previousWindow)
            Box(
                Modifier.align(Alignment.TopEnd).offset(y = CHAPTER_NAV_TOP).padding(end = 18.dp).size(84.dp, 48.dp)
                    .clickable { nextWindow() }.semantics { contentDescription = nextLabel },
            )
            Box(
                Modifier.fillMaxWidth().height(48.dp).offset(y = CHAPTER_RESET_TOP).padding(horizontal = 18.dp)
                    .clickable { reset() }.semantics { contentDescription = resetLabel },
            )
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

private val CHAPTER_ROWS_TOP = 72.dp
private val CHAPTER_ROW_HEIGHT = 48.dp
private val CHAPTER_NAV_CENTER = 482.dp
private val CHAPTER_NAV_TOP = 458.dp
private val CHAPTER_RESET_CENTER = 536.dp
private val CHAPTER_RESET_TOP = 512.dp
private val CHAPTER_PANEL_HEIGHT = 572.dp
private const val CHAPTER_WINDOW_ROWS = 8
